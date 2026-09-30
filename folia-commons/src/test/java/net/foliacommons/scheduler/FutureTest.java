package net.foliacommons.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FutureTest {

    private final Entity entity = mock(Entity.class);

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
