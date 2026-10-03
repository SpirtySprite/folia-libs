package net.foliacommons.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FutureTest {

    private final Entity entity = mock(Entity.class);

    @Test
    void defaultAsyncResultsSuppressCancelledSuppliersAndReportDispatchFailures() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        Scheduler scheduler = new NeverScheduler() {
            @Override public boolean runAsync(Runnable task) { queued.set(task); return true; }
        };
        var success = scheduler.callAsync(() -> 42);
        queued.get().run();
        assertEquals(42, success.join());
        var cancelled = scheduler.callAsync(() -> { throw new AssertionError("cancelled supplier ran"); });
        cancelled.cancel(false);
        queued.get().run();
        assertTrue(cancelled.isCancelled());
        var failure = scheduler.callAsync(() -> { throw new IllegalStateException("supplier"); });
        queued.get().run();
        assertInstanceOf(IllegalStateException.class, assertThrows(java.util.concurrent.CompletionException.class, failure::join).getCause());
        assertTrue(new NeverScheduler().callAsync(() -> 1).isCompletedExceptionally());
        Scheduler throwing = new NeverScheduler() {
            @Override public boolean runAsync(Runnable task) { throw new IllegalStateException("dispatch"); }
        };
        assertTrue(throwing.callAsync(() -> 1).isCompletedExceptionally());
    }

    @Test
    void defaultOneShotAdaptersSuppressCancelledCallbacks() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        Scheduler scheduler = new NeverScheduler() {
            @Override
            public boolean runForEntityLater(Entity e, Runnable task, Runnable retired, long ticks) {
                assertEquals(1, ticks);
                queued.set(task);
                return true;
            }

            @Override
            public boolean runAsyncLater(Runnable task, Duration delay) {
                queued.set(task);
                return true;
            }
        };
        AtomicInteger ran = new AtomicInteger();
        TaskHandle entityTask = scheduler.scheduleForEntityLater(entity, ran::incrementAndGet, null, 0);
        entityTask.cancel();
        queued.get().run();
        assertTrue(entityTask.isCancelled());
        TaskHandle asyncTask = scheduler.scheduleAsyncLater(ran::incrementAndGet, Duration.ZERO);
        queued.get().run();
        assertEquals(1, ran.get());
        asyncTask.cancel();
        queued.get().run();
        assertEquals(1, ran.get());
        assertSameNoop(new NeverScheduler().scheduleAsyncLater(() -> {}, Duration.ZERO));
        assertSameNoop(new NeverScheduler().scheduleForEntityLater(entity, () -> {}, null, 1));
    }

    @Test
    void defaultGlobalOneShotCancelsItsTimerBeforeRunningOnce() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicInteger cancelled = new AtomicInteger();
        TaskHandle timer = new TaskHandle() {
            public void cancel() { cancelled.incrementAndGet(); }
            public boolean isCancelled() { return cancelled.get() > 0; }
        };
        Scheduler scheduler = new NeverScheduler() {
            @Override
            public TaskHandle runGlobalTimer(Runnable task, long delay, long period) {
                assertEquals(1, delay);
                assertEquals(1, period);
                queued.set(task);
                return timer;
            }
        };
        AtomicInteger ran = new AtomicInteger();
        TaskHandle handle = scheduler.scheduleGlobalLater(() -> {
            assertTrue(timer.isCancelled());
            ran.incrementAndGet();
        }, 0);
        queued.get().run();
        queued.get().run();
        assertEquals(1, ran.get());
        assertEquals(1, cancelled.get());
        assertTrue(handle.isCancelled());
    }

    @Test
    void defaultLocationFutureSnapshotsItsTargetAndReportsDispatchFailure() {
        World world = mock(World.class);
        Location target = new Location(world, 4, 5, 6);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicReference<Location> captured = new AtomicReference<>();
        Scheduler scheduler = new NeverScheduler() {
            @Override
            public boolean runForLocation(Location location, Runnable task) {
                captured.set(location);
                queued.set(task);
                return true;
            }
        };
        CompletableFuture<Integer> future = scheduler.callForLocation(target, () -> 42);
        target.setX(100);
        assertEquals(4, captured.get().getX());
        queued.get().run();
        assertEquals(42, future.join());
        assertTrue(new NeverScheduler().callForLocation(target, () -> 1).isCompletedExceptionally());
        Scheduler throwing = new NeverScheduler() {
            @Override
            public boolean runForLocation(Location location, Runnable task) {
                throw new IllegalStateException("dispatch");
            }
        };
        assertTrue(throwing.callForLocation(target, () -> 1).isCompletedExceptionally());
        CompletableFuture<Integer> cancelledFuture = scheduler.callForLocation(target, () -> {
            throw new AssertionError("cancelled supplier must not run");
        });
        cancelledFuture.cancel(false);
        queued.get().run();
        assertTrue(cancelledFuture.isCancelled());
    }

    private static void assertSameNoop(TaskHandle handle) {
        org.junit.jupiter.api.Assertions.assertSame(TaskHandle.NOOP, handle);
    }

    @Test
    void callForEntityDeliversTheTasksResult() throws Exception {
        CompletableFuture<String> future = Scheduler.synchronous().callForEntity(entity, () -> "done");

        assertEquals("done", future.get());
    }

    @Test
    void callForEntityDeliversTheTasksException() {
        CompletableFuture<String> future = Scheduler.synchronous().callForEntity(entity, () -> {
            throw new IllegalStateException("boom");
        });

        ExecutionException failure = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test
    void callGlobalDeliversTheTasksResult() throws Exception {
        assertEquals(42, Scheduler.synchronous().callGlobal(() -> 42).get());
    }

    @Test
    void anUnscheduledCallFailsTheFutureInsteadOfHanging() {
        Scheduler disabled = new NeverScheduler();

        ExecutionException entityFailure = assertThrows(ExecutionException.class,
                () -> disabled.callForEntity(entity, () -> "x").get());
        ExecutionException globalFailure = assertThrows(ExecutionException.class,
                () -> disabled.callGlobal(() -> "x").get());

        assertInstanceOf(SchedulingException.class, entityFailure.getCause());
        assertInstanceOf(SchedulingException.class, globalFailure.getCause());
    }

    @Test
    void aRemovedEntityFailsTheFutureThroughTheRetiredCallback() {
        Scheduler retiring = new NeverScheduler() {
            @Override
            public boolean runForEntity(Entity e, Runnable task, Runnable retired) {
                retired.run();
                return false;
            }
        };

        CompletableFuture<String> future = retiring.callForEntity(entity, () -> "x");

        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void ensureRunsImmediatelyWhenTheCurrentThreadOwnsTheEntity() {
        Server server = mock(Server.class);
        when(server.isOwnedByCurrentRegion(entity)).thenReturn(true);
        AtomicInteger scheduled = new AtomicInteger();
        Scheduler scheduler = new NeverScheduler() {
            @Override
            public boolean runForEntity(Entity e, Runnable task, Runnable retired) {
                scheduled.incrementAndGet();
                return true;
            }
        };
        AtomicInteger ran = new AtomicInteger();

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            assertTrue(scheduler.ensureForEntity(entity, ran::incrementAndGet, null));
        }

        assertEquals(1, ran.get());
        assertEquals(0, scheduled.get(), "nothing needed scheduling");
    }

    @Test
    void ensureSchedulesWhenAnotherThreadOwnsTheEntity() {
        Server server = mock(Server.class);
        when(server.isOwnedByCurrentRegion(entity)).thenReturn(false);
        AtomicInteger scheduled = new AtomicInteger();
        Scheduler scheduler = new NeverScheduler() {
            @Override
            public boolean runForEntity(Entity e, Runnable task, Runnable retired) {
                scheduled.incrementAndGet();
                return true;
            }
        };
        AtomicInteger ran = new AtomicInteger();

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            assertTrue(scheduler.ensureForEntity(entity, ran::incrementAndGet, null));
        }

        assertEquals(0, ran.get());
        assertEquals(1, scheduled.get());
    }

    /** Accepts nothing; tests override the one method they care about. */
    private static class NeverScheduler implements Scheduler {
        @Override
        public boolean runForEntity(Entity e, Runnable task, Runnable retired) {
            return false;
        }

        @Override
        public boolean runForEntityLater(Entity e, Runnable task, Runnable retired, long delayTicks) {
            return false;
        }

        @Override
        public TaskHandle runForEntityTimer(Entity e, Runnable task, Runnable retired, long delay, long period) {
            return TaskHandle.NOOP;
        }

        @Override
        public boolean runForLocation(org.bukkit.Location location, Runnable task) {
            return false;
        }

        @Override
        public boolean runGlobal(Runnable task) {
            return false;
        }

        @Override
        public TaskHandle runGlobalTimer(Runnable task, long delay, long period) {
            return TaskHandle.NOOP;
        }

        @Override
        public boolean runAsync(Runnable task) {
            return false;
        }

        @Override
        public boolean runAsyncLater(Runnable task, java.time.Duration delay) {
            return false;
        }

        @Override
        public boolean isFolia() {
            return false;
        }
    }
}
