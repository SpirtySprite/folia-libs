package net.foliacommons.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs work on the right thread on both Folia and Paper.
 *
 * <p>On Folia every entity and every region of the world has its own thread, so code that touches a
 * player must run on that player's thread. On Paper all of these run on the main thread. A scheduler
 * hides the difference: call it from anywhere and the task runs where it is allowed to.
 *
 * <p>Nothing is scheduled once the plugin is disabled. Methods that can tell you return {@code false}
 * (or {@link TaskHandle#NOOP}) in that case instead of throwing.
 */
public interface Scheduler {

    /**
     * Runs {@code task} on the thread that owns {@code entity}. If the entity is removed before the task
     * can run, {@code retired} runs instead (it may be {@code null}).
     *
     * @return false if nothing was scheduled
     */
    boolean runForEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired);

    /** Like {@link #runForEntity} after a delay of at least one tick. */
    boolean runForEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired,
                              long delayTicks);

    /** Repeats {@code task} on the entity's thread. Stops by itself if the entity is removed. */
    @NotNull TaskHandle runForEntityTimer(@NotNull Entity entity, @NotNull Runnable task,
                                          @Nullable Runnable retired, long initialDelayTicks, long periodTicks);

    /**
     * Like {@link #runForEntityTimer}, but the task receives a handle it can use to cancel its own
     * repetition, for example once a player has left.
     */
    default @NotNull TaskHandle repeatForEntity(@NotNull Entity entity, @NotNull Consumer<TaskHandle> task,
                                                @Nullable Runnable retired, long initialDelayTicks,
                                                long periodTicks) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        DeferredHandle handle = new DeferredHandle();
        handle.bind(runForEntityTimer(entity, () -> task.accept(handle), retired, initialDelayTicks, periodTicks));
        return handle;
    }

    /**
     * Runs {@code task} right now if the current thread already owns {@code entity}, otherwise hands it
     * to the entity's thread. Use it to avoid a needless one-tick delay.
     *
     * @return false if the task could not be scheduled
     */
    default boolean ensureForEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        if (Bukkit.getServer().isOwnedByCurrentRegion(entity)) {
            task.run();
            return true;
        }
        return runForEntity(entity, task, retired);
    }

    /**
     * Runs {@code task} on the entity's thread and delivers its result as a future, so callers on
     * other threads can chain on it. The future fails with a {@link SchedulingException} if the task
     * could not run because the plugin is disabled or the entity was removed, and with the task's own
     * exception if it throws.
     */
    default <T> @NotNull CompletableFuture<T> callForEntity(@NotNull Entity entity, @NotNull Supplier<T> task) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable retired = () -> future.completeExceptionally(
                new SchedulingException("The entity was removed before the task could run"));
        try {
            if (!runForEntity(entity, () -> complete(future, task), retired)) {
                future.completeExceptionally(new SchedulingException(
                        "The task could not be scheduled (the plugin is disabled or the entity was removed)"));
            }
        } catch (RuntimeException failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    /** Like {@link #callForEntity} for the global region thread. */
    default <T> @NotNull CompletableFuture<T> callGlobal(@NotNull Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            if (!runGlobal(() -> complete(future, task))) {
                future.completeExceptionally(new SchedulingException("The task could not be scheduled (the plugin is disabled)"));
            }
        } catch (RuntimeException failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    private static <T> void complete(CompletableFuture<T> future, Supplier<T> task) {
        if (future.isDone()) {
            return;
        }
        try {
            future.complete(task.get());
        } catch (Throwable failure) {
            future.completeExceptionally(failure);
        }
    }

    /** Runs {@code task} on the thread that owns the region containing {@code location}. */
    boolean runForLocation(@NotNull Location location, @NotNull Runnable task);

    /**
     * Returns a result from the region owning a snapshot of {@code location}. No other region may be
     * accessed there. Non-async continuations may run on that region or the attaching thread if the
     * future is already complete. Plugin-bound calls fail on owner disable.
     */
    @ApiStatus.Experimental
    default <T> @NotNull CompletableFuture<T> callForLocation(@NotNull Location location, @NotNull Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        Location snapshot = Objects.requireNonNull(location, "location").clone();
        Objects.requireNonNull(snapshot.getWorld(), "location.world");
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            if (!runForLocation(snapshot, () -> complete(future, task))) {
                future.completeExceptionally(new SchedulingException("The location call could not be scheduled"));
            }
        } catch (RuntimeException failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    /**
     * Schedules a cancellable one-shot entity task, after at least one tick. Retirement is not
     * cancellation. Custom implementations using this default suppress cancelled callbacks; plugin
     * schedulers also cancel the underlying task. Safe from any thread.
     */
    @ApiStatus.Experimental
    default @NotNull TaskHandle scheduleForEntityLater(@NotNull Entity entity, @NotNull Runnable task,
                                                       @Nullable Runnable retired, long delayTicks) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        CancellableTask handle = new CancellableTask();
        return runForEntityLater(entity, handle.guard(task), retired, Math.max(1, delayTicks)) ? handle : TaskHandle.NOOP;
    }

    /** Schedules one cancellable global callback after at least one tick. Safe from any thread. */
    @ApiStatus.Experimental
    default @NotNull TaskHandle scheduleGlobalLater(@NotNull Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        DeferredHandle handle = new DeferredHandle();
        handle.bind(runGlobalTimer(() -> {
            if (!handle.isCancelled()) {
                handle.cancel();
                task.run();
            }
        }, Math.max(1, delayTicks), 1));
        return handle;
    }

    /** Schedules cancellable asynchronous work after at least one millisecond. Safe from any thread. */
    @ApiStatus.Experimental
    default @NotNull TaskHandle scheduleAsyncLater(@NotNull Runnable task, @NotNull Duration delay) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        CancellableTask handle = new CancellableTask();
        return runAsyncLater(handle.guard(task), delay) ? handle : TaskHandle.NOOP;
    }

    /** Runs {@code task} on the global region thread (the main thread on Paper). */
    boolean runGlobal(@NotNull Runnable task);

    @NotNull TaskHandle runGlobalTimer(@NotNull Runnable task, long initialDelayTicks, long periodTicks);

    /** Runs {@code task} on a thread that is not tied to the world. Never touch players or blocks there. */
    boolean runAsync(@NotNull Runnable task);

    boolean runAsyncLater(@NotNull Runnable task, @NotNull Duration delay);

    boolean isFolia();

    /** A scheduler bound to {@code plugin}. */
    static @NotNull Scheduler forPlugin(@NotNull Plugin plugin) {
        return new PluginScheduler(plugin);
    }

    /**
     * A scheduler that runs every one-shot task immediately on the calling thread and ignores timers.
     * Meant for unit tests that have no server.
     */
    static @NotNull Scheduler synchronous() {
        return SynchronousScheduler.INSTANCE;
    }

    /** Creates a virtual-time test scheduler; callbacks run only when time is advanced, without a server. */
    @ApiStatus.Experimental
    static @NotNull DeterministicScheduler deterministic() {
        return new DeterministicScheduler();
    }
}
