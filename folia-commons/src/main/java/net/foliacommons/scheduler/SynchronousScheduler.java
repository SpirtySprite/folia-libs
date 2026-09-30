package net.foliacommons.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

final class SynchronousScheduler implements Scheduler {
    static final SynchronousScheduler INSTANCE = new SynchronousScheduler();

    private SynchronousScheduler() {
    }

    @Override
    public boolean runForEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        task.run();
        return true;
    }

    @Override
    public boolean runForEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired,
                                     long delayTicks) {
        task.run();
        return true;
    }

    @Override
    public @NotNull TaskHandle runForEntityTimer(@NotNull Entity entity, @NotNull Runnable task,
                                                 @Nullable Runnable retired, long initialDelayTicks,
                                                 long periodTicks) {
        return TaskHandle.NOOP;
    }

    @Override
    public boolean runForLocation(@NotNull Location location, @NotNull Runnable task) {
        task.run();
        return true;
    }

    @Override
    public boolean runGlobal(@NotNull Runnable task) {
        task.run();
        return true;
    }

    @Override
    public @NotNull TaskHandle runGlobalTimer(@NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        return TaskHandle.NOOP;
    }

    @Override
    public boolean runAsync(@NotNull Runnable task) {
        task.run();
        return true;
    }

    @Override
    public boolean runAsyncLater(@NotNull Runnable task, @NotNull Duration delay) {
        task.run();
        return true;
    }

    @Override
    public boolean isFolia() {
        return false;
    }
}
