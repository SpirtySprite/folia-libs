package net.folianpc.api.agent;

import net.foliacommons.scheduler.Scheduler;
import net.foliacommons.scheduler.TaskHandle;
import net.folianpc.api.MovementResult;
import net.folianpc.api.MovementTask;
import net.folianpc.api.Npc;
import net.folianpc.api.NpcPosition;
import org.bukkit.Location;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Execution context bound to one goal generation. Schedule live access, then use commit to reject stale work immediately before mutation. */
@ApiStatus.Experimental
public final class AgentContext {
    final NpcAgent agent;
    final NpcAgent.Request request;
    AgentContext(NpcAgent agent, NpcAgent.Request request) { this.agent = agent; this.request = request; }

    /** Returns the packet NPC. Custom actions must not replace its movement outside this goal's lifetime. */
    public Npc npc() { return agent.npc; }
    /** Returns the agent's managed inventory. */
    public AgentInventory inventory() { return agent.inventory; }
    /** Returns the configured shared scheduler. Ownership is not established by merely retrieving it. */
    public Scheduler scheduler() { return agent.scheduler; }
    /** Returns the goal's immutable options. */
    public AgentOptions options() { return request.options; }
    /** Reports whether the goal remains current and the NPC remains live. */
    public boolean active() { return agent.active(request); }

    /** Admits a short nonblocking operation while this goal is current. Caller must own all game objects; the terminal result waits for admitted commits to finish without rolling them back. */
    public boolean commit(BooleanSupplier operation) {
        java.util.Objects.requireNonNull(operation, "operation");
        if (!agent.beginCommit(request)) return false;
        try { return operation.getAsBoolean(); }
        finally { agent.endCommit(request); }
    }

    /** Checks copied target coordinates and world name against a coherent NPC snapshot without reading live blocks or entities. */
    public boolean inReach(Location target) {
        java.util.Objects.requireNonNull(target, "target").checkFinite();
        NpcPosition position = npc().positionSnapshot();
        return target.getWorld() != null && position.world().equals(target.getWorld().getName())
                && squared(position.x() - target.getX(), position.y() - target.getY(), position.z() - target.getZ())
                <= options().reach() * options().reach();
    }

    /** Calls the explicitly configured permission callback on the target owner. World actions are denied until a callback is installed. */
    public boolean permitted(AgentInteraction interaction) {
        java.util.Objects.requireNonNull(interaction, "interaction");
        java.util.function.Predicate<AgentInteraction> permission;
        synchronized (agent) { permission = agent.permission; }
        return agent.active(request) && permission.test(interaction) && agent.active(request);
    }

    /** Starts owned movement to a copied destination and observes actual arrival. Cancellation stops only this movement request. */
    public CompletableFuture<Boolean> navigate(Location target) {
        return navigate(target, options().navigation());
    }

    /** Navigates to any reachable standing point within a nonnegative radius of the target. */
    public CompletableFuture<Boolean> navigateNear(Location target, double radius) {
        return navigate(target, options().navigation().toBuilder().arrivalRadius(radius).build());
    }

    private CompletableFuture<Boolean> navigate(Location target, net.folianpc.api.NavigationOptions navigation) {
        Location destination = java.util.Objects.requireNonNull(target, "target").clone();
        java.util.Objects.requireNonNull(destination.getWorld(), "world");
        destination.checkFinite();
        if (!agent.active(request)) return CompletableFuture.completedFuture(false);
        MovementTask movement = npc().navigateTo(destination, options().speed(), navigation, this::active);
        TaskHandle handle = new TaskHandle() {
            @Override public void cancel() { movement.cancel(); }
            @Override public boolean isCancelled() { return movement.isCancelled(); }
        };
        request.work.add(handle);
        return movement.result().thenApply(outcome -> active() && outcome.status() == MovementResult.Status.ARRIVED)
                .whenComplete((result, failure) -> request.work.remove(handle));
    }

    /** Delivers a tick-delayed signal, tracked through cancellation and shutdown. */
    public CompletableFuture<Boolean> waitTicks(long ticks) {
        if (ticks < 1 || ticks > 72000) throw new IllegalArgumentException("Invalid delay");
        if (!agent.active(request)) return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = request.work.add(new CompletableFuture<>());
        try {
            TaskHandle timer = scheduler().scheduleGlobalLater(() -> future.complete(active()), ticks);
            request.work.add(timer);
            future.whenComplete((value, failure) -> { timer.cancel(); request.work.remove(timer); });
            if (timer.isCancelled() && !future.isDone()) future.complete(false);
        } catch (RuntimeException failure) { future.completeExceptionally(failure); }
        return future;
    }

    private static double squared(double x, double y, double z) { return x*x + y*y + z*z; }
}
