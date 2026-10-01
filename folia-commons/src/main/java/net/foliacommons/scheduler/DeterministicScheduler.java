package net.foliacommons.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A thread-safe virtual-time scheduler for tests without a server. All domains share one ordered
 * queue; this models ordering and lifetime, not actual region ownership or parallel server threads.
 * Callbacks run on the thread advancing time, in submission order at equal deadlines. Tick delays
 * clamp to one tick and asynchronous delays to one millisecond, as on plugin schedulers.
 */
@ApiStatus.Experimental
public final class DeterministicScheduler implements Scheduler, AutoCloseable {
    private static final long TICK_NANOS = 50_000_000;
    private final PriorityQueue<Job> queue = new PriorityQueue<>(Comparator.comparingLong((Job job) -> job.due)
            .thenComparingLong(job -> job.sequence));
    private final Set<Entity> retired = new HashSet<>();
    private final ReentrantLock advancing = new ReentrantLock();
    private long now;
    private long sequence;
    private boolean closed;

    /** Creates an empty scheduler at virtual time zero. */
    public DeterministicScheduler() {
    }

    /** Advances by non-negative ticks, executing due work and repeating tasks without sleeping. */
    public void advanceTicks(long ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be non-negative");
        }
        advance(Duration.ofNanos(Math.multiplyExact(ticks, TICK_NANOS)));
    }

    /** Advances by a non-negative duration. Concurrent advancement is serialized; callbacks must not block. */
    public void advance(@NotNull Duration duration) {
        long nanos = Objects.requireNonNull(duration, "duration").toNanos();
        if (nanos < 0) {
            throw new IllegalArgumentException("duration must be non-negative");
        }
        if (advancing.isHeldByCurrentThread()) {
            throw new IllegalStateException("A callback cannot recursively advance time");
        }
        advancing.lock();
        try {
            long target;
            synchronized (queue) {
                target = Math.addExact(now, nanos);
            }
            while (true) {
                Job job;
                synchronized (queue) {
                    job = queue.peek();
                    if (job == null || job.due > target) {
                        now = target;
                        return;
                    }
                    queue.remove();
                    now = job.due;
                }
                if (job.isCancelled()) {
                    continue;
                }
                try {
                    job.task.run();
                } catch (RuntimeException | Error failure) {
                    job.cancel();
                    throw failure;
                }
                synchronized (queue) {
                    if (job.period > 0 && !job.isCancelled() && !closed && !retired.contains(job.entity)) {
                        job.due = Math.addExact(job.due, job.period);
                        queue.add(job);
                    }
                }
            }
        } finally {
            advancing.unlock();
        }
    }

    /** Current virtual elapsed time, usable as the source for a monotonic deadline. */
    public long nanoTime() {
        synchronized (queue) {
            return now;
        }
    }

    /** Retires an entity and cancels pending work. All retirement callbacks run, even if one throws. */
    public void retire(@NotNull Entity entity) {
        Objects.requireNonNull(entity, "entity");
        List<Job> pending;
        synchronized (queue) {
            retired.add(entity);
            pending = queue.stream().filter(job -> entity.equals(job.entity)).toList();
        }
        RuntimeException failure = null;
        for (Job job : pending) {
            if (job.cancelled.compareAndSet(false, true)) {
                synchronized (queue) {
                    queue.remove(job);
                }
                try {
                    if (job.onRetired != null) {
                        job.onRetired.run();
                    }
                } catch (RuntimeException thrown) {
                    if (failure == null) {
                        failure = thrown;
                    } else if (failure != thrown) {
                        failure.addSuppressed(thrown);
                    }
                } finally {
                    job.onCancelled.run();
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Number of queued callbacks, including repeating tasks. */
    public int pendingTasks() {
        synchronized (queue) {
            return queue.size();
        }
    }

    /** Cancels queued work and fails pending calls. New submissions are rejected. */
    @Override
    public void close() {
        List<Job> pending;
        synchronized (queue) {
            closed = true;
            pending = List.copyOf(queue);
            queue.clear();
            retired.clear();
        }
        pending.forEach(Job::cancel);
    }

    @Override
    public boolean runForEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        return scheduleForEntityLater(entity, task, retired, 1) != TaskHandle.NOOP;
    }

    @Override
    public boolean runForEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long ticks) {
        return scheduleForEntityLater(entity, task, retired, ticks) != TaskHandle.NOOP;
    }

    @Override
    public @NotNull TaskHandle scheduleForEntityLater(@NotNull Entity entity, @NotNull Runnable task,
                                                     @Nullable Runnable retired, long ticks) {
        return enqueue(Objects.requireNonNull(entity, "entity"), task, retired, ticks(ticks), 0, () -> {});
    }

    @Override
    public @NotNull TaskHandle runForEntityTimer(@NotNull Entity entity, @NotNull Runnable task,
                                                @Nullable Runnable retired, long delay, long period) {
        return enqueue(Objects.requireNonNull(entity, "entity"), task, retired, ticks(delay), ticks(period), () -> {});
    }

    @Override
    public boolean ensureForEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        return runForEntity(entity, task, retired);
    }

    @Override
    public boolean runForLocation(@NotNull Location location, @NotNull Runnable task) {
        location(location);
        return runGlobal(task);
    }

    @Override
    public boolean runGlobal(@NotNull Runnable task) {
        return scheduleGlobalLater(task, 1) != TaskHandle.NOOP;
    }

    @Override
    public @NotNull TaskHandle scheduleGlobalLater(@NotNull Runnable task, long delay) {
        return enqueue(null, task, null, ticks(delay), 0, () -> {});
    }

    @Override
    public @NotNull TaskHandle runGlobalTimer(@NotNull Runnable task, long delay, long period) {
        return enqueue(null, task, null, ticks(delay), ticks(period), () -> {});
    }

    @Override
    public boolean runAsync(@NotNull Runnable task) {
        return enqueue(null, task, null, 0, 0, () -> {}) != TaskHandle.NOOP;
    }

    @Override
    public boolean runAsyncLater(@NotNull Runnable task, @NotNull Duration delay) {
        return scheduleAsyncLater(task, delay) != TaskHandle.NOOP;
    }

    @Override
    public @NotNull TaskHandle scheduleAsyncLater(@NotNull Runnable task, @NotNull Duration delay) {
        long millis = Math.max(1, Objects.requireNonNull(delay, "delay").toMillis());
        return enqueue(null, task, null, Math.multiplyExact(millis, 1_000_000), 0, () -> {});
    }

    @Override
    public <T> @NotNull CompletableFuture<T> callForEntity(@NotNull Entity entity, @NotNull Supplier<T> task) {
        return call(Objects.requireNonNull(entity, "entity"), task);
    }

    @Override
    public <T> @NotNull CompletableFuture<T> callGlobal(@NotNull Supplier<T> task) {
        return call(null, task);
    }

    @Override
    public <T> @NotNull CompletableFuture<T> callForLocation(@NotNull Location location, @NotNull Supplier<T> task) {
        location(location);
        return call(null, task);
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    private <T> CompletableFuture<T> call(Entity entity, Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable fail = () -> future.completeExceptionally(new SchedulingException("The test call was cancelled or retired"));
        TaskHandle handle = enqueue(entity, () -> {
            if (!future.isDone()) {
                try {
                    future.complete(task.get());
                } catch (Throwable failure) {
                    future.completeExceptionally(failure);
                }
            }
        }, fail, TICK_NANOS, 0, fail);
        if (handle == TaskHandle.NOOP) {
            fail.run();
        }
        future.whenComplete((result, failure) -> handle.cancel());
        return future;
    }

    private TaskHandle enqueue(Entity entity, Runnable task, Runnable onRetired, long delay, long period, Runnable onCancelled) {
        Objects.requireNonNull(task, "task");
        synchronized (queue) {
            if (closed || entity != null && retired.contains(entity)) {
                return TaskHandle.NOOP;
            }
            Job job = new Job(entity, task, onRetired, onCancelled, Math.addExact(now, delay), period, sequence++);
            queue.add(job);
            return job;
        }
    }

    private static long ticks(long value) {
        return Math.multiplyExact(Math.max(1, value), TICK_NANOS);
    }

    private static void location(Location location) {
        Objects.requireNonNull(Objects.requireNonNull(location, "location").getWorld(), "location.world");
    }

    private final class Job implements TaskHandle {
        private final Entity entity;
        private final Runnable task;
        private final Runnable onRetired;
        private final Runnable onCancelled;
        private final long period;
        private final long sequence;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private long due;

        private Job(Entity entity, Runnable task, Runnable onRetired, Runnable onCancelled, long due, long period, long sequence) {
            this.entity = entity;
            this.task = task;
            this.onRetired = onRetired;
            this.onCancelled = onCancelled;
            this.due = due;
            this.period = period;
            this.sequence = sequence;
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                synchronized (queue) {
                    queue.remove(this);
                }
                onCancelled.run();
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
