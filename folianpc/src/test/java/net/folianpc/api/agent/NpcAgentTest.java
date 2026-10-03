package net.folianpc.api.agent;

import net.foliacommons.scheduler.DeterministicScheduler;
import net.foliacommons.scheduler.Scheduler;
import net.folianpc.api.Npc;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NpcAgentTest {
    private NpcAgent agent(DeterministicScheduler scheduler) {
        return new NpcAgent(mock(Npc.class), new AgentInventory(8), scheduler, Runnable::run);
    }

    @Test void goalHandlesComposeWithSharedCancellationScopesAndClose() {
        try (var scheduler = Scheduler.deterministic(); var agent = agent(scheduler); var scope = new net.foliacommons.scheduler.TaskGroup()) {
            agent.operator(new AgentOperator("work", Map.of(), Map.of("goal", 1L), 1, context -> new CompletableFuture<>()));
            var task = agent.pursue(new AgentGoal(Map.of("goal", 1L)));
            assertFalse(task.isCancelled());
            scope.add(task);
            scope.close();
            assertTrue(task.isCancelled());
            assertEquals(AgentResult.Status.CANCELLED, task.result().join().status());
            var other = agent.pursue(new AgentGoal(Map.of("goal", 1L)));
            other.close();
            assertTrue(other.isCancelled());
            assertEquals(AgentResult.Status.CANCELLED, other.result().join().status());
        }
    }

    @Test void terminalResultWaitsForAdmittedCommitWithoutHoldingTheControllerLock() throws Exception {
        try (var scheduler = Scheduler.deterministic(); var executor = java.util.concurrent.Executors.newSingleThreadExecutor(); var agent = agent(scheduler)) {
            AtomicReference<AgentContext> context = new AtomicReference<>();
            agent.operator(new AgentOperator("capture", Map.of(), Map.of("goal", 1L), 1, c -> { context.set(c); return new CompletableFuture<>(); }));
            var task = agent.pursue(new AgentGoal(Map.of("goal", 1L)));
            var entered = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            var committed = executor.submit(() -> context.get().commit(() -> {
                assertFalse(Thread.holdsLock(agent));
                entered.countDown();
                try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                return context.get().inventory().add(List.of(AgentInventoryTest.stack(Material.STONE, 1)));
            }));
            try {
                assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
                task.cancel();
                assertFalse(task.result().isDone());
            } finally { release.countDown(); }
            assertTrue(committed.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(AgentResult.Status.CANCELLED, task.result().join().status());
            assertEquals(1L, agent.inventory().resources().get("STONE"));
        }
    }

    @Test void cancellationTracksObservationCreatedByAnAlreadyRunningSupplier() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var stage = new CompletableFuture<List<AgentOperator>>();
        try (var scheduler = Scheduler.deterministic(); var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
             var agent = new NpcAgent(mock(Npc.class), new AgentInventory(8), scheduler, executor)) {
            agent.perception(() -> {
                entered.countDown();
                try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                return stage;
            });
            var task = agent.pursue(new AgentGoal(Map.of("goal", 1L)));
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            task.cancel();
            release.countDown();
            executor.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(stage.isCancelled());
            assertEquals(AgentResult.Status.CANCELLED, task.result().join().status());
        } finally { release.countDown(); }
    }

    @Test void alreadyOwnedResourcesDoNotRequireAWorldObservation() {
        try (var scheduler = Scheduler.deterministic(); var agent = agent(scheduler)) {
            agent.inventory().add(List.of(AgentInventoryTest.stack(Material.STONE, 1)));
            agent.perception(() -> { throw new AssertionError("Observation unnecessary"); });
            var task = agent.pursue(AgentGoal.obtain(Material.STONE, 1));
            assertEquals(AgentResult.Status.COMPLETED, task.result().join().status());
            assertEquals(0, task.executedActions());
            assertEquals(0, scheduler.pendingTasks());
        }
    }

    @Test void schedulingAndObservationFailuresAlwaysFinishTheGoal() {
        Scheduler scheduler = mock(Scheduler.class);
        IllegalStateException dispatch = new IllegalStateException("dispatch");
        when(scheduler.scheduleGlobalLater(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong())).thenThrow(dispatch);
        try (var agent = new NpcAgent(mock(Npc.class), new AgentInventory(8), scheduler, Runnable::run)) {
            var task = agent.pursue(new AgentGoal(Map.of("goal", 1L)));
            assertEquals(AgentResult.Status.FAILED, task.result().join().status());
            assertEquals(dispatch, task.result().join().failure().orElseThrow());
        }
        try (var test = Scheduler.deterministic(); var agent = agent(test)) {
            IllegalStateException observation = new IllegalStateException("observation");
            agent.perception(() -> CompletableFuture.failedFuture(observation));
            var task = agent.pursue(new AgentGoal(Map.of("goal", 1L)));
            assertEquals(observation, task.result().join().failure().orElseThrow());
            assertEquals(0, test.pendingTasks());
        }
    }

    @Test void nestedCommitCancellationNeverRunsCleanupInsideTheOuterGate() {
        try (var scheduler = Scheduler.deterministic(); var agent = agent(scheduler)) {
            AtomicReference<AgentContext> captured = new AtomicReference<>();
            CompletableFuture<Boolean> operation = new CompletableFuture<>();
            agent.operator(new AgentOperator("capture", Map.of(), Map.of("goal", 1L), 1, context -> { captured.set(context); return operation; }));
            var task = agent.pursue(new AgentGoal(Map.of("goal", 1L)));
            AtomicBoolean insideLock = new AtomicBoolean();
            operation.whenComplete((success, failure) -> insideLock.set(Thread.holdsLock(agent)));
            captured.get().commit(() -> captured.get().commit(() -> { task.cancel(); return true; }));
            assertEquals(AgentResult.Status.CANCELLED, task.result().join().status());
            assertFalse(insideLock.get());
        }
    }

    @Test void replansAgainstActualInventoryUntilAResourceGoalIsMet() {
        try (var scheduler = Scheduler.deterministic(); var agent = agent(scheduler)) {
            agent.operator(AgentActions.gather("gather", AgentInventoryTest.stack(Material.STONE, 1), 1,
                    context -> CompletableFuture.completedFuture(context.commit(() -> context.inventory().add(List.of(AgentInventoryTest.stack(Material.STONE, 1)))))));
            var task = agent.pursue(AgentGoal.obtain(Material.STONE, 3));
            scheduler.advanceTicks(4);
            assertEquals(AgentResult.Status.COMPLETED, task.result().join().status());
            assertEquals(3, task.executedActions());
            assertEquals(3L, agent.inventory().resources().get("STONE"));
            assertTrue(task.plan().isPresent());
            assertEquals(0, scheduler.pendingTasks());
        }
    }

    @Test void cancellationRejectsLateCommitsWithoutCancellingIndependentObservers() {
        try (var scheduler = Scheduler.deterministic(); var agent = agent(scheduler)) {
            AtomicReference<AgentContext> captured = new AtomicReference<>();
            CompletableFuture<Boolean> operation = new CompletableFuture<>();
            agent.operator(new AgentOperator("pending", Map.of(), Map.of("goal", 1L), 1, context -> {
                captured.set(context);
                return operation;
            }));
            var task = agent.pursue(new AgentGoal(Map.of("goal", 1L)));
            var observer = task.result();
            observer.cancel(false);
            assertTrue(captured.get().active());
            task.cancel();
            AtomicInteger mutations = new AtomicInteger();
            assertFalse(captured.get().commit(() -> { mutations.incrementAndGet(); return true; }));
            assertEquals(0, mutations.get());
            assertTrue(operation.isCancelled());
            assertEquals(AgentResult.Status.CANCELLED, task.result().join().status());
        }
    }

    @Test void supersessionAndShutdownPreventQueuedActionsFromStarting() {
        List<Runnable> queued = new ArrayList<>();
        Executor executor = queued::add;
        try (var scheduler = Scheduler.deterministic(); var agent = new NpcAgent(mock(Npc.class), new AgentInventory(8), scheduler, executor)) {
            AtomicInteger actions = new AtomicInteger();
            AtomicInteger observations = new AtomicInteger();
            agent.perception(() -> { observations.incrementAndGet(); return CompletableFuture.completedFuture(List.of()); });
            agent.operator(new AgentOperator("work", Map.of(), Map.of("goal", 1L), 1, context -> {
                actions.incrementAndGet(); return CompletableFuture.completedFuture(true);
            }));
            var first = agent.pursue(new AgentGoal(Map.of("goal", 1L)));
            var second = agent.pursue(new AgentGoal(Map.of("goal", 1L)));
            assertEquals(AgentResult.Status.SUPERSEDED, first.result().join().status());
            agent.close();
            assertEquals(AgentResult.Status.SHUTDOWN, second.result().join().status());
            List.copyOf(queued).forEach(Runnable::run);
            assertEquals(0, actions.get());
            assertEquals(0, observations.get());
            assertEquals(0, scheduler.pendingTasks());
            assertThrows(IllegalStateException.class, () -> agent.pursue(new AgentGoal(Map.of("goal", 1L))));
        }
    }

    @Test void failedTargetsAreExcludedAndAlternativeActionsAreTried() {
        try (var scheduler = Scheduler.deterministic(); var agent = agent(scheduler)) {
            AtomicInteger failures = new AtomicInteger();
            agent.operator(new AgentOperator("blocked", Map.of(), Map.of("STONE", 1L), 1, context -> {
                failures.incrementAndGet(); return CompletableFuture.completedFuture(false);
            }));
            agent.operator(AgentActions.gather("alternative", AgentInventoryTest.stack(Material.STONE, 1), 2,
                    context -> CompletableFuture.completedFuture(context.commit(() -> context.inventory().add(List.of(AgentInventoryTest.stack(Material.STONE, 1)))))));
            var task = agent.pursue(AgentGoal.obtain(Material.STONE, 1));
            scheduler.advanceTicks(3);
            assertEquals(AgentResult.Status.COMPLETED, task.result().join().status());
            assertEquals(1, failures.get());
            assertEquals(2, task.executedActions());
        }
    }

    @Test void observationIsRefreshedAfterEachActionAndCancelledWithItsGoal() {
        try (var scheduler = Scheduler.deterministic(); var agent = agent(scheduler)) {
            AtomicInteger scans = new AtomicInteger();
            agent.perception(() -> {
                int source = scans.incrementAndGet();
                return CompletableFuture.completedFuture(List.of(AgentActions.gather("source-" + source,
                        AgentInventoryTest.stack(Material.STONE, 1), 1, context -> CompletableFuture.completedFuture(
                                context.commit(() -> context.inventory().add(List.of(AgentInventoryTest.stack(Material.STONE, 1))))))));
            });
            var task = agent.pursue(AgentGoal.obtain(Material.STONE, 2));
            scheduler.advanceTicks(3);
            assertEquals(AgentResult.Status.COMPLETED, task.result().join().status());
            assertEquals(2, scans.get());
            CompletableFuture<List<AgentOperator>> pending = new CompletableFuture<>();
            agent.perception(() -> pending);
            var cancelled = agent.pursue(AgentGoal.obtain(Material.STONE, 3));
            cancelled.cancel();
            assertTrue(pending.isCancelled());
        }
    }

    @Test void cancellationInsideAnActionCommitDefersCleanupUntilTheGateIsReleased() {
        try (var scheduler = Scheduler.deterministic(); var agent = agent(scheduler)) {
            AtomicReference<AgentContext> captured = new AtomicReference<>();
            CompletableFuture<Boolean> operation = new CompletableFuture<>();
            agent.operator(new AgentOperator("work", Map.of(), Map.of("goal", 1L), 1, context -> { captured.set(context); return operation; }));
            var task = agent.pursue(new AgentGoal(Map.of("goal", 1L)));
            AtomicBoolean cleanupHeldLock = new AtomicBoolean(true);
            operation.whenComplete((success, failure) -> cleanupHeldLock.set(Thread.holdsLock(agent)));
            captured.get().commit(() -> { task.cancel(); return true; });
            assertEquals(AgentResult.Status.CANCELLED, task.result().join().status());
            assertFalse(cleanupHeldLock.get());
        }
    }

    @Test void deadlinesAndRemovalFinishPendingGoalsAndRejectLateWork() {
        try (var scheduler = Scheduler.deterministic()) {
            Npc npc = mock(Npc.class);
            try (var agent = new NpcAgent(npc, new AgentInventory(8), scheduler, Runnable::run)) {
                agent.operator(new AgentOperator("work", Map.of(), Map.of("goal", 1L), 1, context -> new CompletableFuture<>()));
                var defaults = AgentOptions.defaults();
                var options = new AgentOptions(100, 8, 8, 2, 2, defaults.speed(), defaults.reach(), defaults.navigation());
                var timed = agent.pursue(new AgentGoal(Map.of("goal", 1L)), options);
                scheduler.advanceTicks(3);
                assertEquals(AgentResult.Status.TIMED_OUT, timed.result().join().status());
                var removed = agent.pursue(new AgentGoal(Map.of("goal", 1L)));
                when(npc.removed()).thenReturn(true);
                scheduler.advanceTicks(1);
                assertEquals(AgentResult.Status.REMOVED, removed.result().join().status());
            }
        }
    }

    @Test void inconsistentInventoryFactsAndActionExceptionsRetainFailureCauses() {
        try (var scheduler = Scheduler.deterministic(); var agent = agent(scheduler)) {
            agent.facts(() -> Map.of("STONE", 999L));
            var invalid = agent.pursue(AgentGoal.obtain(Material.STONE, 1));
            assertEquals(AgentResult.Status.FAILED, invalid.result().join().status());
            assertTrue(invalid.result().join().failure().isPresent());
            agent.facts(Map::of);
            IllegalStateException failure = new IllegalStateException("action failed");
            agent.operator(new AgentOperator("failing", Map.of(), Map.of("STONE", 1L), 1, context -> CompletableFuture.failedFuture(failure)));
            var failed = agent.pursue(AgentGoal.obtain(Material.STONE, 1));
            assertEquals(AgentResult.Status.FAILED, failed.result().join().status());
            assertEquals(failure, failed.result().join().failure().orElseThrow());
            AssertionError immediate = new AssertionError("immediate action failure");
            agent.operator(new AgentOperator("failing", Map.of(), Map.of("STONE", 1L), 1, context -> { throw immediate; }));
            var thrown = agent.pursue(AgentGoal.obtain(Material.STONE, 1));
            assertEquals(AgentResult.Status.FAILED, thrown.result().join().status());
            assertEquals(immediate, thrown.result().join().failure().orElseThrow());
        }
    }
}
