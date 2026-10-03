package net.folianpc.api.agent;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlannerTest {
    private AgentOperator action(String name, Map<String, Long> requires, Map<String, Long> effects, double cost) {
        return new AgentOperator(name, requires, effects, cost, context -> CompletableFuture.completedFuture(true));
    }

    @Test void unrelatedResourceCyclesDoNotConsumeTheSearchBudget() {
        var choices = new java.util.ArrayList<AgentOperator>();
        for (int i = 0; i < 100; i++) choices.add(action("irrelevant-" + i, Map.of(), Map.of("unrelated-" + i, 1L), 0.1));
        var needed = action("needed", Map.of(), Map.of("goal", 1L), 1);
        choices.add(needed);
        assertEquals(List.of(needed), AgentPlanner.plan(Map.of(), new AgentGoal(Map.of("goal", 1L)), choices, 2, 2, () -> false).actions());
        var missing = AgentPlanner.plan(Map.of(), new AgentGoal(Map.of("missing", 1L)), choices, 2, 2, () -> false);
        assertEquals(AgentPlan.Status.UNREACHABLE, missing.status());
        assertEquals(0, missing.explored());
    }

    @Test void relevanceRetainsActionsThatPreventOverflowOfAnIncidentalEffect() {
        var lower = action("lower", Map.of("incidental", 1L), Map.of("incidental", -1L), 1);
        var produce = action("produce", Map.of(), Map.of("goal", 1L, "incidental", 1L), 1);
        var plan = AgentPlanner.plan(Map.of("incidental", Long.MAX_VALUE), new AgentGoal(Map.of("goal", 1L)),
                List.of(lower, produce), 100, 4, () -> false);
        assertEquals(List.of(lower, produce), plan.actions());
    }

    @Test void plansAPlacedSmeltingStationFromMiningPickupAndOwnedFuel() {
        AgentAction action = context -> java.util.concurrent.CompletableFuture.completedFuture(true);
        AgentRecipe furnace = new AgentRecipe("make-station", Map.of(org.bukkit.Material.COBBLESTONE, 1), AgentInventoryTest.stack(org.bukkit.Material.FURNACE, 1), 1);
        AgentRecipe iron = new AgentRecipe("smelt-iron", Map.of(org.bukkit.Material.RAW_IRON, 1, org.bukkit.Material.COAL, 1), AgentInventoryTest.stack(org.bukkit.Material.IRON_INGOT, 1), 2);
        AgentOperator smelt = iron.operator(2, action);
        Map<String, Long> required = new java.util.HashMap<>(smelt.requires());
        required.put("station", 1L);
        var choices = List.of(furnace.operator(2, action),
                new AgentOperator("place-station", Map.of("plain:FURNACE", 1L), Map.of("FURNACE", -1L, "plain:FURNACE", -1L, "station", 1L), 2, action),
                new AgentOperator(smelt.name(), required, smelt.changes(), smelt.cost(), action),
                AgentActions.gather("mine", AgentInventoryTest.stack(org.bukkit.Material.COBBLESTONE, 1), 2, action),
                AgentActions.gather("pickup", AgentInventoryTest.stack(org.bukkit.Material.RAW_IRON, 1), 2, action));
        AgentPlan result = AgentPlanner.plan(Map.of("IRON_PICKAXE", 1L, "plain:IRON_PICKAXE", 1L, "COAL", 1L, "plain:COAL", 1L),
                AgentGoal.obtain(org.bukkit.Material.IRON_INGOT, 1), choices, 4000, 16, () -> false);
        assertEquals(AgentPlan.Status.FOUND, result.status());
        assertEquals(5, result.actions().size());
        assertEquals(10, result.cost());
    }

    @Test void buildsAMultiStageGatherCraftAndSmeltPlan() {
        var chop = action("chop", Map.of(), Map.of("log", 1L), 3);
        var planks = action("planks", Map.of("log", 1L), Map.of("log", -1L, "plank", 4L), 1);
        var pickaxe = action("pickaxe", Map.of("plank", 3L), Map.of("plank", -3L, "pickaxe", 1L), 1);
        var mine = action("mine", Map.of("pickaxe", 1L), Map.of("ore", 1L), 4);
        var smelt = action("smelt", Map.of("ore", 1L, "plank", 1L), Map.of("ore", -1L, "plank", -1L, "ingot", 1L), 5);
        var plan = AgentPlanner.plan(Map.of(), new AgentGoal(Map.of("ingot", 1L)), List.of(chop, planks, pickaxe, mine, smelt), 1000, 8, () -> false);
        assertEquals(AgentPlan.Status.FOUND, plan.status());
        assertEquals(List.of("chop", "planks", "pickaxe", "mine", "smelt"), plan.actions().stream().map(AgentOperator::name).toList());
        assertEquals(14, plan.cost());
    }

    @Test void choosesTheCheapestAvailableProductionChain() {
        var expensive = action("buy", Map.of(), Map.of("result", 1L), 10);
        var first = action("gather", Map.of(), Map.of("raw", 1L), 2);
        var second = action("produce", Map.of("raw", 1L), Map.of("raw", -1L, "result", 1L), 3);
        var plan = AgentPlanner.plan(Map.of(), new AgentGoal(Map.of("result", 1L)), List.of(expensive, first, second), 100, 8, () -> false);
        assertEquals(List.of(first, second), plan.actions());
        assertEquals(5, plan.cost());
    }

    @Test void cannotSpendUnavailableResourcesOrOverflowFacts() {
        var spend = action("spend", Map.of(), Map.of("missing", -1L, "goal", 1L), 1);
        assertEquals(AgentPlan.Status.UNREACHABLE, AgentPlanner.plan(Map.of(), new AgentGoal(Map.of("goal", 1L)), List.of(spend), 8, 8, () -> false).status());
        var overflow = action("overflow", Map.of(), Map.of("huge", 1L, "goal", 1L), 1);
        assertEquals(AgentPlan.Status.UNREACHABLE, AgentPlanner.plan(Map.of("huge", Long.MAX_VALUE), new AgentGoal(Map.of("goal", 1L)), List.of(overflow), 8, 8, () -> false).status());
    }

    @Test void cancellationAndBudgetsAreDistinctFromUnreachableGoals() {
        var gather = action("gather", Map.of(), Map.of("resource", 1L), 1);
        var goal = new AgentGoal(Map.of("resource", 20L));
        assertEquals(AgentPlan.Status.CANCELLED, AgentPlanner.plan(Map.of(), goal, List.of(gather), 100, 100, () -> true).status());
        assertEquals(AgentPlan.Status.BUDGET_EXHAUSTED, AgentPlanner.plan(Map.of(), goal, List.of(gather), 4, 100, () -> false).status());
        assertEquals(AgentPlan.Status.BUDGET_EXHAUSTED, AgentPlanner.plan(Map.of(), goal, List.of(gather), 100, 4, () -> false).status());
        assertEquals(AgentPlan.Status.UNREACHABLE, AgentPlanner.plan(Map.of(), goal, List.of(), 100, 100, () -> false).status());
    }

    @Test void observesCancellationDuringExpansionAndNeverExecutesActionsWhilePlanning() {
        AtomicInteger calls = new AtomicInteger();
        var action = new AgentOperator("gather", Map.of(), Map.of("x", 1L), 1, context -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        });
        AtomicInteger checks = new AtomicInteger();
        var plan = AgentPlanner.plan(Map.of(), new AgentGoal(Map.of("x", 50L)), List.of(action), 100, 100, () -> checks.incrementAndGet() > 5);
        assertEquals(AgentPlan.Status.CANCELLED, plan.status());
        assertEquals(0, calls.get());
    }

    @Test void retainsShallowerAlternativesWhenAPlanHasADepthLimit() {
        var slow = action("direct", Map.of(), Map.of("middle", 1L), 3);
        var one = action("first", Map.of(), Map.of("intermediate", 1L), 1);
        var two = action("second", Map.of("intermediate", 1L), Map.of("intermediate", -1L, "middle", 1L), 1);
        var finish = action("finish", Map.of("middle", 1L), Map.of("middle", -1L, "goal", 1L), 1);
        var plan = AgentPlanner.plan(Map.of(), new AgentGoal(Map.of("goal", 1L)), List.of(one, two, slow, finish), 200, 2, () -> false);
        assertEquals(List.of(slow, finish), plan.actions());
    }

    @Test void satisfiedGoalsNeedNoActionsAndInvalidBudgetsFailImmediately() {
        var goal = new AgentGoal(Map.of("goal", 1L));
        assertTrue(AgentPlanner.plan(Map.of("goal", 1L), goal, List.of(), 1, 1, () -> false).actions().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> AgentPlanner.plan(Map.of(), goal, List.of(), 0, 1, () -> false));
        assertThrows(IllegalArgumentException.class, () -> AgentPlanner.plan(Map.of("negative", -1L), goal, List.of(), 8, 1, () -> false));
        assertThrows(IllegalArgumentException.class, () -> action("bad", Map.of(), Map.of("x", 1L), Double.NaN));
    }
}
