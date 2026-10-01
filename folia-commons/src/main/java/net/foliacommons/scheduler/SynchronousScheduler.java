package net.foliacommons.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

final class SynchronousScheduler implements Scheduler {
    static final SynchronousScheduler INSTANCE = new SynchronousScheduler();

    private SynchronousScheduler() {
    }

    @Override
    public boolean runForEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task").run();
        return true;
    }

    @Override
    public boolean runForEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired,
                                     long delayTicks) {
        return runForEntity(entity, task, retired);
    }

    @Override
    public @NotNull TaskHandle runForEntityTimer(@NotNull Entity entity, @NotNull Runnable task,
                                                 @Nullable Runnable retired, long initialDelayTicks,
                                                 long periodTicks) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        return TaskHandle.NOOP;
    }

    @Override
    public boolean runForLocation(@NotNull Location location, @NotNull Runnable task) {
        Objects.requireNonNull(Objects.requireNonNull(location, "location").getWorld(), "location.world");
        Objects.requireNonNull(task, "task").run();
        return true;
    }

    @Override
    public boolean runGlobal(@NotNull Runnable task) {
        Objects.requireNonNull(task, "task").run();
        return true;
    }

    @Override
    public @NotNull TaskHandle runGlobalTimer(@NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(task, "task");
        return TaskHandle.NOOP;
    }

    @Override
    public boolean runAsync(@NotNull Runnable task) {
        Objects.requireNonNull(task, "task").run();
        return true;
    }

    @Override
    public boolean runAsyncLater(@NotNull Runnable task, @NotNull Duration delay) {
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(task, "task").run();
        return true;
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public boolean ensureForEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        return runForEntity(entity, task, retired);
    }

    @Override
    public @NotNull TaskHandle scheduleGlobalLater(@NotNull Runnable task, long delayTicks) {
        runGlobal(task);
        return TaskHandle.NOOP;
    }
}
