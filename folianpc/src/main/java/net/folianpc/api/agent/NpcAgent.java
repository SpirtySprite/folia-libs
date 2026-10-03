package net.folianpc.api.agent;

import net.foliacommons.scheduler.Scheduler;
import net.foliacommons.scheduler.TaskGroup;
import net.foliacommons.scheduler.TaskHandle;
import net.folianpc.api.Npc;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Opt-in autonomous control of a packet NPC. Plans asynchronously, executes one action, observes actual facts and replans after every action. */
@ApiStatus.Experimental
public final class NpcAgent implements AutoCloseable {
    final Npc npc;
    final AgentInventory inventory;
    final Scheduler scheduler;
    private final Executor executor;
    private final Map<String, AgentOperator> operators = new java.util.LinkedHashMap<>();
    Predicate<AgentInteraction> permission = interaction -> false;
    private Supplier<Map<String, Long>> facts = Map::of;
    private volatile Request current;
    private volatile boolean closed;
    private Supplier<java.util.concurrent.CompletionStage<List<AgentOperator>>> perception = () -> CompletableFuture.completedFuture(List.of());

    /** Creates a controller with a supplied scheduler and planning executor. The owner must close it; custom executors must not block game threads. */
    public NpcAgent(Npc npc, AgentInventory inventory, Scheduler scheduler, Executor executor) {
        this.npc = Objects.requireNonNull(npc, "npc");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.executor = Objects.requireNonNull(executor, "executor");
        if (npc.removed()) throw new IllegalStateException("NPC was removed");
    }

    /** Returns the managed inventory, retained when a goal is cancelled. */
    public AgentInventory inventory() { return inventory; }

    /** Registers or replaces a named planning action. Updates apply at the next planning pass. */
    public synchronized NpcAgent operator(AgentOperator operator) {
        ensureOpen();
        Objects.requireNonNull(operator, "operator");
        if (!operators.containsKey(operator.name()) && operators.size() >= 256) throw new IllegalStateException("Operator limit reached");
        operators.put(operator.name(), operator);
        return this;
    }

    /** Removes a named action from subsequent planning passes. Does not interrupt an action already executing. */
    public synchronized boolean removeOperator(String name) { ensureOpen(); return operators.remove(Objects.requireNonNull(name, "name")) != null; }

    /** Adds developer-owned immutable facts. Runs on the planning executor, must not access live Bukkit state, and cannot override inventory keys. */
    public synchronized NpcAgent facts(Supplier<Map<String, Long>> provider) { ensureOpen(); facts = Objects.requireNonNull(provider, "provider"); return this; }

    /** Discovers current world-action bindings asynchronously before every planning pass. Must schedule all live reads and return at most 256 distinct named actions. */
    public synchronized NpcAgent perception(Supplier<java.util.concurrent.CompletionStage<List<AgentOperator>>> provider) {
        ensureOpen(); perception = Objects.requireNonNull(provider, "provider"); return this;
    }

    /** Installs world-action authorization. Runs on the action target's region and must be nonblocking. Nothing grants player protection-plugin permissions automatically. */
    public synchronized NpcAgent permission(Predicate<AgentInteraction> policy) { ensureOpen(); permission = Objects.requireNonNull(policy, "policy"); return this; }

    /** Starts a goal with bounded defaults, replacing any unfinished goal. */
    public AgentTask pursue(AgentGoal goal) { return pursue(goal, AgentOptions.defaults()); }

    /** Starts a bounded goal. All observer futures are independent; stale action callbacks cannot pass the context's commit gate. */
    public AgentTask pursue(AgentGoal goal, AgentOptions options) {
        Request request = new Request(Objects.requireNonNull(goal, "goal"), Objects.requireNonNull(options, "options"));
        Request previous;
        synchronized (this) { ensureOpen(); previous = current; current = request; }
        if (previous != null) finish(previous, AgentResult.Status.SUPERSEDED, null);
        try {
            TaskHandle timeout = scheduler.scheduleGlobalLater(() -> finish(request, AgentResult.Status.TIMED_OUT, null), options.timeoutTicks());
            request.work.add(timeout);
            TaskHandle monitor = scheduler.runGlobalTimer(() -> {
                if (npc.removed()) finish(request, AgentResult.Status.REMOVED, null);
            }, 1, 1);
            request.work.add(monitor);
            if (timeout.isCancelled() || monitor.isCancelled()) finish(request, AgentResult.Status.SHUTDOWN, null);
            else plan(request);
        } catch (RuntimeException failure) { finish(request, AgentResult.Status.FAILED, failure); }
        return request;
    }

    /** Permanently closes the controller and rejects new goals and configuration changes. Committed resources remain in the inventory. */
    @Override public void close() {
        Request request;
        synchronized (this) { if (closed) return; closed = true; request = current; }
        if (request != null) finish(request, npc.removed() ? AgentResult.Status.REMOVED : AgentResult.Status.SHUTDOWN, null);
    }

    /** Reports permanent closure. */
    public boolean isClosed() { return closed; }

    boolean active(Request request) { return !closed && current == request && !request.ending && !npc.removed(); }

    private void ensureOpen() { if (closed || npc.removed()) throw new IllegalStateException("Agent is closed or NPC removed"); }

    private void plan(Request request) {
        if (!active(request)) return;
        if (request.goal.satisfied(inventory.resources())) { finish(request, AgentResult.Status.COMPLETED, null); return; }
        Supplier<Map<String, Long>> provider;
        Supplier<java.util.concurrent.CompletionStage<List<AgentOperator>>> observe;
        List<AgentOperator> choices;
        synchronized (this) {
            if (!active(request)) return;
            provider = facts;
            observe = perception;
            choices = operators.values().stream().filter(operator -> !request.rejected.contains(operator.name())).toList();
        }
        CompletableFuture<AgentPlan> search;
        try {
            var source = CompletableFuture.supplyAsync(() -> {
                if (!active(request)) return CompletableFuture.<List<AgentOperator>>completedFuture(List.of());
                CompletableFuture<List<AgentOperator>> observed = Objects.requireNonNull(observe.get(), "perception.result").toCompletableFuture();
                request.work.add(observed);
                return observed;
            }, executor);
            request.work.add(source);
            var observation = source.thenCompose(stage -> stage);
            request.work.add(observation);
            search = observation.thenApplyAsync(dynamic -> {
                List<AgentOperator> available = new java.util.ArrayList<>(choices);
                Set<String> names = new HashSet<>();
                for (AgentOperator choice : choices) names.add(choice.name());
                for (AgentOperator choice : List.copyOf(dynamic)) {
                    if (!names.add(choice.name())) throw new IllegalArgumentException("Duplicate operator name: " + choice.name());
                    if (!request.rejected.contains(choice.name())) available.add(choice);
                }
                Map<String, Long> state = new HashMap<>(Map.copyOf(provider.get()));
                Map<String, Long> resources = inventory.resources();
                for (String key : state.keySet()) {
                    if (isMaterial(key)) throw new IllegalArgumentException("Custom fact overlaps inventory material: " + key);
                }
                state.putAll(resources);
                return AgentPlanner.plan(state, request.goal, available, request.options.maxNodes(), request.options.maxPlanActions(), () -> !active(request));
            }, executor);
            request.work.add(search);
        } catch (RuntimeException failure) { finish(request, AgentResult.Status.FAILED, failure); return; }
        search.whenComplete((result, failure) -> {
            AgentResult.Status terminal = null;
            AgentOperator selected = null;
            synchronized (this) {
                if (!active(request)) return;
                if (failure != null) terminal = AgentResult.Status.FAILED;
                else {
                    request.lastPlan = result;
                    if (result.status() != AgentPlan.Status.FOUND) terminal = result.status() == AgentPlan.Status.BUDGET_EXHAUSTED
                            ? AgentResult.Status.BUDGET_EXHAUSTED : AgentResult.Status.UNREACHABLE;
                    else if (result.actions().isEmpty()) terminal = AgentResult.Status.COMPLETED;
                    else if (request.executed >= request.options.maxExecutedActions()) terminal = AgentResult.Status.BUDGET_EXHAUSTED;
                    else selected = result.actions().getFirst();
                }
            }
            if (terminal != null) finish(request, terminal, failure);
            else execute(request, selected);
        });
    }

    private void execute(Request request, AgentOperator operator) {
        if (!active(request)) return;
        try {
            CompletableFuture<Boolean> operation = Objects.requireNonNull(operator.action().execute(new AgentContext(this, request)), "action.result").toCompletableFuture();
            request.work.add(operation);
            operation.whenComplete((success, failure) -> {
                AgentResult.Status terminal = null;
                synchronized (this) {
                    if (!active(request)) return;
                    if (failure != null) terminal = AgentResult.Status.FAILED;
                    else {
                        request.executed++;
                        if (!Boolean.TRUE.equals(success)) {
                            request.rejected.add(operator.name());
                            if (++request.failures > request.options.maxFailures()) terminal = AgentResult.Status.UNREACHABLE;
                        }
                    }
                }
                if (terminal != null) { finish(request, terminal, failure); return; }
                try {
                    TaskHandle next = scheduler.scheduleGlobalLater(() -> plan(request), 1);
                    request.work.add(next);
                    if (next.isCancelled()) finish(request, AgentResult.Status.SHUTDOWN, null);
                } catch (RuntimeException schedulingFailure) { finish(request, AgentResult.Status.FAILED, schedulingFailure); }
            });
        } catch (Throwable failure) { finish(request, AgentResult.Status.FAILED, failure); }
    }

    synchronized boolean beginCommit(Request request) {
        if (!active(request)) return false;
        request.commits++;
        return true;
    }

    void endCommit(Request request) {
        AgentResult outcome;
        synchronized (this) {
            request.commits--;
            outcome = request.commits == 0 && request.cleanupDone ? request.outcome : null;
        }
        if (outcome != null) request.result.complete(outcome);
    }

    private void finish(Request request, AgentResult.Status status, Throwable failure) {
        synchronized (this) {
            if (request.ending) return;
            request.ending = true;
            if (current == request) current = null;
            request.outcome = new AgentResult(status, request.executed, Optional.ofNullable(unwrap(failure)));
        }
        try { request.work.close(); }
        finally {
            AgentResult outcome;
            synchronized (this) {
                request.cleanupDone = true;
                outcome = request.commits == 0 ? request.outcome : null;
            }
            if (outcome != null) request.result.complete(outcome);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        while ((failure instanceof java.util.concurrent.CompletionException || failure instanceof java.util.concurrent.ExecutionException)
                && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    private static boolean isMaterial(String key) {
        try { org.bukkit.Material.valueOf(key.startsWith("plain:") ? key.substring(6) : key); return true; }
        catch (IllegalArgumentException absent) { return false; }
    }

    final class Request implements AgentTask {
        final AgentGoal goal;
        final AgentOptions options;
        final TaskGroup work = new TaskGroup();
        final Set<String> rejected = new HashSet<>();
        final CompletableFuture<AgentResult> result = new CompletableFuture<>();
        volatile AgentPlan lastPlan;
        volatile int executed;
        int failures;
        volatile boolean ending;
        int commits;
        boolean cleanupDone;
        AgentResult outcome;
        Request(AgentGoal goal, AgentOptions options) { this.goal = goal; this.options = options; }
        @Override public CompletableFuture<AgentResult> result() { return result.copy(); }
        @Override public Optional<AgentPlan> plan() { return Optional.ofNullable(lastPlan); }
        @Override public int executedActions() { return executed; }
        @Override public void cancel() { finish(this, AgentResult.Status.CANCELLED, null); }
        @Override public boolean isCancelled() { return ending; }
    }
}
