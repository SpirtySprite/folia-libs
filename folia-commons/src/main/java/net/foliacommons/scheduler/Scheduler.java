package net.foliacommons.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.function.Consumer;

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
        DeferredHandle handle = new DeferredHandle();
        handle.bind(runForEntityTimer(entity, () -> task.accept(handle), retired, initialDelayTicks, periodTicks));
        return handle;
    }

    /** Runs {@code task} on the thread that owns the region containing {@code location}. */
    boolean runForLocation(@NotNull Location location, @NotNull Runnable task);

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
}
