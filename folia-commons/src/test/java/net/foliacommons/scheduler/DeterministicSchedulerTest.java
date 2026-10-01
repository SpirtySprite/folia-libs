package net.foliacommons.scheduler;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DeterministicSchedulerTest {
    private final Entity entity = mock(Entity.class);
    private final World world = mock(World.class);
    private final Location location = new Location(world, 0, 64, 0);

    @Test
    void throwingRetirementCallbacksDoNotLeaveOtherCallsPending() {
        try (DeterministicScheduler scheduler = Scheduler.deterministic()) {
            AtomicInteger retired = new AtomicInteger();
            scheduler.runForEntityLater(entity, () -> {}, () -> { throw new IllegalStateException("first"); }, 1);
            scheduler.runForEntityLater(entity, () -> {}, retired::incrementAndGet, 2);
            CompletableFuture<String> future = scheduler.callForEntity(entity, () -> "unused");
            assertThrows(IllegalStateException.class, () -> scheduler.retire(entity));
            assertEquals(1, retired.get());
            assertTrue(future.isCompletedExceptionally());
            assertEquals(0, scheduler.pendingTasks());
        }
    }

    @Test
    void retiringAnEntityDuringItsCallbackStopsRepetition() {
        try (DeterministicScheduler scheduler = Scheduler.deterministic()) {
            AtomicInteger calls = new AtomicInteger();
            scheduler.runForEntityTimer(entity, () -> {
                calls.incrementAndGet();
                scheduler.retire(entity);
            }, null, 1, 1);
            scheduler.advanceTicks(10);
            assertEquals(1, calls.get());
            assertEquals(0, scheduler.pendingTasks());
        }
    }

    @Test
    void queuedDomainsRunAtTheirDeadlinesInSubmissionOrder() {
        try (DeterministicScheduler scheduler = Scheduler.deterministic()) {
            List<String> order = new ArrayList<>();
            scheduler.runForEntity(entity, () -> order.add("entity"), null);
            scheduler.runGlobal(() -> order.add("global"));
            scheduler.runForLocation(location, () -> order.add("location"));
            scheduler.runAsync(() -> order.add("async"));
            scheduler.runAsyncLater(() -> order.add("later"), Duration.ZERO);
            assertTrue(order.isEmpty());
            scheduler.advance(Duration.ZERO);
            assertEquals(List.of("async"), order);
            scheduler.advanceTicks(1);
            assertEquals(List.of("async", "later", "entity", "global", "location"), order);
            assertEquals(50_000_000, scheduler.nanoTime());
            assertEquals(0, scheduler.pendingTasks());
            assertFalse(scheduler.isFolia());
        }
    }

    @Test
    void handlesAndGroupsCancelBeforeTheDeadline() {
        try (DeterministicScheduler scheduler = Scheduler.deterministic(); TaskGroup group = new TaskGroup()) {
            AtomicInteger calls = new AtomicInteger();
            group.add(scheduler.scheduleForEntityLater(entity, calls::incrementAndGet, null, 2));
            group.add(scheduler.scheduleGlobalLater(calls::incrementAndGet, 2));
            group.add(scheduler.scheduleAsyncLater(calls::incrementAndGet, Duration.ofMillis(75)));
            group.cancel();
            scheduler.advanceTicks(10);
            assertEquals(0, calls.get());
            assertEquals(0, scheduler.pendingTasks());
        }
    }

    @Test
    void repeatingTasksCanCancelThemselvesAndScheduleNewWork() {
        try (DeterministicScheduler scheduler = Scheduler.deterministic()) {
            AtomicInteger calls = new AtomicInteger();
            scheduler.repeatForEntity(entity, handle -> {
                if (calls.incrementAndGet() == 3) {
                    handle.cancel();
                    scheduler.runGlobal(calls::incrementAndGet);
                }
            }, null, 1, 2);
            scheduler.advanceTicks(10);
            assertEquals(4, calls.get());
            assertEquals(0, scheduler.pendingTasks());
        }
    }

    @Test
    void retirementAndCloseTerminateCallsWithoutRunningSuppliers() {
        DeterministicScheduler scheduler = Scheduler.deterministic();
        AtomicInteger ran = new AtomicInteger();
        AtomicInteger retired = new AtomicInteger();
        CompletableFuture<Integer> entityCall = scheduler.callForEntity(entity, ran::incrementAndGet);
        scheduler.runForEntityLater(entity, ran::incrementAndGet, retired::incrementAndGet, 3);
        scheduler.retire(entity);
        scheduler.retire(entity);
        assertTrue(entityCall.isCompletedExceptionally());
        assertEquals(1, retired.get());
        assertFalse(scheduler.runForEntity(entity, ran::incrementAndGet, null));
        CompletableFuture<Integer> global = scheduler.callGlobal(ran::incrementAndGet);
        CompletableFuture<Integer> region = scheduler.callForLocation(location, ran::incrementAndGet);
        scheduler.close();
        scheduler.close();
        scheduler.advanceTicks(5);
        assertTrue(global.isCompletedExceptionally());
        assertTrue(region.isCompletedExceptionally());
        assertEquals(0, ran.get());
        assertSame(TaskHandle.NOOP, scheduler.scheduleGlobalLater(ran::incrementAndGet, 1));
        assertTrue(scheduler.callGlobal(ran::incrementAndGet).isCompletedExceptionally());
    }

    @Test
    void callsReturnResultsFailuresAndRespectCancellation() {
        try (DeterministicScheduler scheduler = Scheduler.deterministic()) {
            CompletableFuture<String> result = scheduler.callForLocation(location, () -> "region");
            CompletableFuture<String> failure = scheduler.callGlobal(() -> { throw new IllegalStateException("failed"); });
            CompletableFuture<Integer> cancelled = scheduler.callForEntity(entity, () -> { throw new AssertionError(); });
            cancelled.cancel(false);
            scheduler.advanceTicks(1);
            assertEquals("region", result.join());
            assertTrue(failure.isCompletedExceptionally());
            assertTrue(cancelled.isCancelled());
        }
    }

    @Test
    void invalidInputsAndRecursiveAdvancementFailClearly() {
        try (DeterministicScheduler scheduler = Scheduler.deterministic()) {
            assertThrows(IllegalArgumentException.class, () -> scheduler.advanceTicks(-1));
            assertThrows(IllegalArgumentException.class, () -> scheduler.advance(Duration.ofNanos(-1)));
            assertThrows(NullPointerException.class, () -> scheduler.runForLocation(new Location(null, 0, 0, 0), () -> {}));
            assertThrows(ArithmeticException.class, () -> scheduler.advanceTicks(Long.MAX_VALUE));
            scheduler.runGlobal(() -> scheduler.advanceTicks(1));
            assertThrows(IllegalStateException.class, () -> scheduler.advanceTicks(1));
            assertEquals(0, scheduler.pendingTasks());
        }
    }

    @Test
    void globalTimersClampingAndEnsureRemainQueued() {
        try (DeterministicScheduler scheduler = Scheduler.deterministic()) {
            AtomicInteger calls = new AtomicInteger();
            TaskHandle timer = scheduler.runGlobalTimer(calls::incrementAndGet, -1, 0);
            scheduler.ensureForEntity(entity, calls::incrementAndGet, null);
            scheduler.runForEntityLater(entity, calls::incrementAndGet, null, 0);
            assertEquals(0, calls.get());
            scheduler.advanceTicks(3);
            assertEquals(5, calls.get());
            timer.cancel();
            assertTrue(timer.isCancelled());
        }
    }
}
