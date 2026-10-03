package net.folianpc.api.agent;

import org.jetbrains.annotations.ApiStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;

/** Pure minimum-cost planning over immutable fact snapshots. Performs no Bukkit access and can run asynchronously. */
@ApiStatus.Experimental
public final class AgentPlanner {
    private record Key(Map<String, Long> state, int depth) { }
    private record Search(Map<String, Long> state, Search parent, AgentOperator action, double cost, long sequence, int depth) { }
    private AgentPlanner() { }

    /** Searches up to maxNodes states and maxActions actions. Cancellation is checked at every expansion and candidate action. */
    public static AgentPlan plan(Map<String, Long> state, AgentGoal goal, List<AgentOperator> operators,
                                 int maxNodes, int maxActions, BooleanSupplier cancelled) {
        Map<String, Long> supplied = Map.copyOf(state);
        Map<String, Long> initial = supplied.entrySet().stream().filter(entry -> entry.getValue() != 0)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        Objects.requireNonNull(goal, "goal");
        List<AgentOperator> choices = List.copyOf(operators);
        Objects.requireNonNull(cancelled, "cancelled");
        if (maxNodes < 1 || maxNodes > 100_000 || maxActions < 1 || maxActions > 256
                || choices.size() > 256 || supplied.size() > 512
                || supplied.keySet().stream().anyMatch(String::isBlank) || initial.values().stream().anyMatch(n -> n < 0)) throw new IllegalArgumentException("Invalid planning limits or facts");
        java.util.Set<String> keys = new java.util.HashSet<>(supplied.keySet());
        keys.addAll(goal.requires().keySet());
        for (AgentOperator operator : choices) { keys.addAll(operator.requires().keySet()); keys.addAll(operator.changes().keySet()); }
        if (keys.size() > 512) throw new IllegalArgumentException("Too many planning facts");
        choices = relevant(goal, choices, cancelled);
        if (cancelled.getAsBoolean()) return result(AgentPlan.Status.CANCELLED, 0);
        if (!reachable(initial, goal, choices, cancelled)) return result(cancelled.getAsBoolean() ? AgentPlan.Status.CANCELLED : AgentPlan.Status.UNREACHABLE, 0);
        PriorityQueue<Search> queue = new PriorityQueue<>(Comparator.comparingDouble(Search::cost).thenComparingLong(Search::sequence));
        Map<Key, Double> best = new HashMap<>();
        Search start = new Search(initial, null, null, 0, 0, 0);
        queue.add(start);
        best.put(new Key(initial, 0), 0.0);
        int explored = 0;
        long sequence = 1;
        boolean depthLimited = false;
        double droppedCost = Double.POSITIVE_INFINITY;
        while (!queue.isEmpty()) {
            if (cancelled.getAsBoolean()) return result(AgentPlan.Status.CANCELLED, explored);
            if (queue.size() > maxNodes * 2L) queue.removeIf(entry -> entry.cost() > best.get(new Key(entry.state(), entry.depth())));
            Search current = queue.remove();
            if (current.cost() > best.get(new Key(current.state(), current.depth()))) continue;
            if (goal.satisfied(current.state())) return current.cost() <= droppedCost ? found(current, explored) : result(AgentPlan.Status.BUDGET_EXHAUSTED, explored);
            if (explored >= maxNodes) return result(AgentPlan.Status.BUDGET_EXHAUSTED, explored);
            explored++;
            if (current.depth() >= maxActions) { depthLimited = true; continue; }
            for (AgentOperator operator : choices) {
                if (cancelled.getAsBoolean()) return result(AgentPlan.Status.CANCELLED, explored);
                if (operator.requires().entrySet().stream().anyMatch(e -> current.state().getOrDefault(e.getKey(), 0L) < e.getValue())) continue;
                Map<String, Long> next = apply(current.state(), operator.changes());
                if (next == null) continue;
                double cost = current.cost() + operator.cost();
                Key key = new Key(next, current.depth() + 1);
                if (!Double.isFinite(cost) || cost >= best.getOrDefault(key, Double.POSITIVE_INFINITY)) continue;
                if (!best.containsKey(key) && best.size() >= maxNodes) { droppedCost = Math.min(droppedCost, cost); continue; }
                best.put(key, cost);
                queue.add(new Search(next, current, operator, cost, sequence++, current.depth() + 1));
            }
        }
        return result(depthLimited || Double.isFinite(droppedCost) ? AgentPlan.Status.BUDGET_EXHAUSTED : AgentPlan.Status.UNREACHABLE, explored);
    }

    private static List<AgentOperator> relevant(AgentGoal goal, List<AgentOperator> choices, BooleanSupplier cancelled) {
        java.util.Set<String> needed = new java.util.HashSet<>(goal.requires().keySet());
        java.util.Set<AgentOperator> selected = new java.util.HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (AgentOperator operator : choices) {
                if (cancelled.getAsBoolean()) return List.of();
                if (selected.contains(operator) || operator.changes().keySet().stream().noneMatch(needed::contains)) continue;
                selected.add(operator);
                needed.addAll(operator.requires().keySet());
                needed.addAll(operator.changes().keySet());
                changed = true;
            }
        } while (changed);
        return choices.stream().filter(selected::contains).toList();
    }

    private static boolean reachable(Map<String, Long> initial, AgentGoal goal, List<AgentOperator> choices, BooleanSupplier cancelled) {
        java.util.Set<String> available = new java.util.HashSet<>(initial.keySet());
        boolean changed;
        do {
            changed = false;
            for (AgentOperator operator : choices) {
                if (cancelled.getAsBoolean()) return false;
                if (operator.requires().entrySet().stream().anyMatch(e -> e.getValue() > 0 && !available.contains(e.getKey()))
                        || operator.changes().entrySet().stream().anyMatch(e -> e.getValue() < 0 && !available.contains(e.getKey()))) continue;
                for (var effect : operator.changes().entrySet()) if (effect.getValue() > 0) changed |= available.add(effect.getKey());
            }
        } while (changed);
        return available.containsAll(goal.requires().keySet());
    }

    private static Map<String, Long> apply(Map<String, Long> state, Map<String, Long> changes) {
        Map<String, Long> next = new HashMap<>(state);
        for (var change : changes.entrySet()) {
            long value;
            try { value = Math.addExact(next.getOrDefault(change.getKey(), 0L), change.getValue()); }
            catch (ArithmeticException overflow) { return null; }
            if (value < 0) return null;
            if (value == 0) next.remove(change.getKey()); else next.put(change.getKey(), value);
        }
        return Map.copyOf(next);
    }

    private static AgentPlan found(Search search, int explored) {
        List<AgentOperator> reversed = new ArrayList<>();
        for (Search at = search; at.parent() != null; at = at.parent()) reversed.add(at.action());
        return new AgentPlan(AgentPlan.Status.FOUND, reversed.reversed(), explored, search.cost());
    }

    private static AgentPlan result(AgentPlan.Status status, int explored) { return new AgentPlan(status, List.of(), explored, 0); }
}
