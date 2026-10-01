package net.foliacommons.scheduler;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A thread-safe cancellation scope for related tasks and futures. Closing cancels the whole scope;
 * adding work after cancellation cancels that work immediately. Cancellation cannot interrupt a
 * callback already executing on a region thread.
 */
@ApiStatus.Experimental
public final class TaskGroup implements TaskHandle, AutoCloseable {
    private final List<TaskHandle> tasks = new ArrayList<>();
    private boolean cancelled;

    /** Creates an empty cancellation scope. */
    public TaskGroup() {
    }

    /** Adds a task and returns the same handle. Safe during concurrent cancellation. */
    public @NotNull TaskHandle add(@NotNull TaskHandle task) {
        Objects.requireNonNull(task, "task");
        synchronized (this) {
            if (!cancelled) {
                tasks.add(task);
                return task;
            }
        }
        task.cancel();
        return task;
    }

    /** Tracks a future until completion. Group cancellation cancels the future without interrupting its supplier. */
    public <T> @NotNull CompletableFuture<T> add(@NotNull CompletableFuture<T> future) {
        Objects.requireNonNull(future, "future");
        TaskHandle handle = new TaskHandle() {
            @Override
            public void cancel() {
                future.cancel(false);
            }

            @Override
            public boolean isCancelled() {
                return future.isCancelled();
            }
        };
        add(handle);
        future.whenComplete((result, failure) -> {
            synchronized (this) {
                tasks.remove(handle);
            }
        });
        return future;
    }

    /** Removes a handle from this scope without cancelling it. */
    public synchronized boolean remove(@NotNull TaskHandle task) {
        return tasks.remove(Objects.requireNonNull(task, "task"));
    }

    /** Cancels every tracked operation once and releases references, even if a handle throws. */
    @Override
    public void cancel() {
        List<TaskHandle> pending;
        synchronized (this) {
            if (cancelled) {
                return;
            }
            cancelled = true;
            pending = List.copyOf(tasks);
            tasks.clear();
        }
        RuntimeException failure = null;
        for (TaskHandle task : pending) {
            try {
                task.cancel();
            } catch (RuntimeException thrown) {
                if (failure == null) {
                    failure = thrown;
                } else if (failure != thrown) {
                    failure.addSuppressed(thrown);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Whether this scope has been cancelled or closed. */
    @Override
    public synchronized boolean isCancelled() {
        return cancelled;
    }

    /** Cancels the scope. Safe to call repeatedly from any thread. */
    @Override
    public void close() {
        cancel();
    }
}
