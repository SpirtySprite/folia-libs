package net.foliacommons.scheduler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TaskGroupTest {
    @Test
    void concurrentAdditionAndCancellationNeverLoseHandles() {
        TaskGroup group = new TaskGroup();
        AtomicInteger cancelled = new AtomicInteger();
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        CompletableFuture<Void> adding = CompletableFuture.runAsync(() -> {
            try {
                start.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            for (int index = 0; index < 1000; index++) {
                group.add(new TaskHandle() {
                    @Override
                    public void cancel() { cancelled.incrementAndGet(); }
                    @Override
                    public boolean isCancelled() { return false; }
                });
            }
        });
        start.countDown();
        group.cancel();
        adding.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();
        assertEquals(1000, cancelled.get());
    }
    @Test
    void closingCancelsHandlesAndFuturesAndRejectsNewWork() {
        TaskGroup group = new TaskGroup();
        TaskHandle handle = mock(TaskHandle.class);
        CompletableFuture<String> future = new CompletableFuture<>();
        assertSame(handle, group.add(handle));
        assertSame(future, group.add(future));
        assertFalse(group.isCancelled());
        group.close();
        group.close();
        assertTrue(group.isCancelled());
        assertTrue(future.isCancelled());
        verify(handle).cancel();
        TaskHandle late = mock(TaskHandle.class);
        group.add(late);
        verify(late).cancel();
        assertTrue(group.add(new CompletableFuture<>()).isCancelled());
    }

    @Test
    void completedAndRemovedOperationsAreReleasedWithoutCancellation() {
        TaskGroup group = new TaskGroup();
        TaskHandle detached = mock(TaskHandle.class);
        group.add(detached);
        assertTrue(group.remove(detached));
        assertFalse(group.remove(detached));
        CompletableFuture<String> result = group.add(new CompletableFuture<>());
        result.complete("done");
        group.close();
        assertEquals("done", result.join());
        verify(detached, never()).cancel();
    }

    @Test
    void oneThrowingHandleDoesNotPreventTheOthersFromCancelling() {
        TaskGroup group = new TaskGroup();
        AtomicInteger cancelled = new AtomicInteger();
        group.add(throwing());
        group.add(throwing());
        group.add(new TaskHandle() {
            @Override
            public void cancel() { cancelled.incrementAndGet(); }
            @Override
            public boolean isCancelled() { return cancelled.get() > 0; }
        });
        RuntimeException failure = assertThrows(RuntimeException.class, group::cancel);
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(1, cancelled.get());
        assertThrows(NullPointerException.class, () -> group.add((TaskHandle) null));
    }

    private static TaskHandle throwing() {
        return new TaskHandle() {
            @Override
            public void cancel() { throw new IllegalStateException("failure"); }
            @Override
            public boolean isCancelled() { return false; }
        };
    }
}
