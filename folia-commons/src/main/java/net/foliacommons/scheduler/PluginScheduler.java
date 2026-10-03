package net.foliacommons.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.foliacommons.FoliaEnvironment;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.BiFunction;

final class PluginScheduler implements Scheduler {
    private final Plugin plugin;

    PluginScheduler(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean runForEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return false;
        }
        try {
            return entity.getScheduler().run(plugin, scheduled -> task.run(), retired) != null;
        } catch (IllegalPluginAccessException disabledMidCall) {
            return false;
        }
    }

    @Override
    public boolean runForEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired,
                                     long delayTicks) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return false;
        }
        try {
            return entity.getScheduler()
                    .runDelayed(plugin, scheduled -> task.run(), retired, Math.max(1L, delayTicks)) != null;
        } catch (IllegalPluginAccessException disabledMidCall) {
            return false;
        }
    }

    @Override
    public @NotNull TaskHandle runForEntityTimer(@NotNull Entity entity, @NotNull Runnable task,
                                                 @Nullable Runnable retired, long initialDelayTicks,
                                                 long periodTicks) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return TaskHandle.NOOP;
        }
        try {
            ScheduledTask scheduled = entity.getScheduler().runAtFixedRate(plugin, t -> task.run(), retired,
                    Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
            return scheduled == null ? TaskHandle.NOOP : new Handle(scheduled);
        } catch (IllegalPluginAccessException disabledMidCall) {
            return TaskHandle.NOOP;
        }
    }

    @Override
    public boolean runForLocation(@NotNull Location location, @NotNull Runnable task) {
        Location snapshot = locationSnapshot(location);
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return false;
        }
        try {
            Bukkit.getRegionScheduler().execute(plugin, snapshot, task);
            return true;
        } catch (IllegalPluginAccessException disabledMidCall) {
            return false;
        }
    }

    @Override
    public boolean runGlobal(@NotNull Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return false;
        }
        try {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
            return true;
        } catch (IllegalPluginAccessException disabledMidCall) {
            return false;
        }
    }

    @Override
    public @NotNull TaskHandle runGlobalTimer(@NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return TaskHandle.NOOP;
        }
        try {
            ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(),
                    Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
            return scheduled == null ? TaskHandle.NOOP : new Handle(scheduled);
        } catch (IllegalPluginAccessException disabledMidCall) {
            return TaskHandle.NOOP;
        }
    }

    @Override
    public boolean runAsync(@NotNull Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return false;
        }
        try {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
            return true;
        } catch (IllegalPluginAccessException disabledMidCall) {
            return false;
        }
    }

    @Override
    public boolean runAsyncLater(@NotNull Runnable task, @NotNull Duration delay) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        if (!plugin.isEnabled()) {
            return false;
        }
        try {
            Bukkit.getAsyncScheduler().runDelayed(plugin, t -> task.run(),
                    Math.max(1L, delay.toMillis()), TimeUnit.MILLISECONDS);
            return true;
        } catch (IllegalPluginAccessException disabledMidCall) {
            return false;
        }
    }

    @Override
    public boolean isFolia() {
        return FoliaEnvironment.isFolia();
    }

    @Override
    public boolean ensureForEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        return plugin.isEnabled() && Scheduler.super.ensureForEntity(entity, task, retired);
    }

    @Override
    public <T> @NotNull CompletableFuture<T> callForEntity(@NotNull Entity entity, @NotNull Supplier<T> task) {
        Objects.requireNonNull(entity, "entity");
        return call(task, (run, retired) -> runForEntity(entity, run, retired));
    }

    @Override
    public <T> @NotNull CompletableFuture<T> callGlobal(@NotNull Supplier<T> task) {
        return call(task, (run, retired) -> runGlobal(run));
    }

    @Override
    public <T> @NotNull CompletableFuture<T> callAsync(@NotNull Supplier<T> task) {
        return call(task, (run, retired) -> runAsync(run));
    }

    @Override
    public <T> @NotNull CompletableFuture<T> callForLocation(@NotNull Location location, @NotNull Supplier<T> task) {
        Location snapshot = locationSnapshot(location);
        return call(task, (run, retired) -> runForLocation(snapshot, run));
    }

    private <T> CompletableFuture<T> call(Supplier<T> task, BiFunction<Runnable, Runnable, Boolean> dispatch) {
        Objects.requireNonNull(task, "task");
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            PluginCalls.track(plugin, future);
            if (future.isDone()) {
                return future;
            }
            Runnable run = () -> {
                if (!future.isDone()) {
                    try {
                        future.complete(task.get());
                    } catch (Throwable failure) {
                        future.completeExceptionally(failure);
                    }
                }
            };
            Runnable retired = () -> future.completeExceptionally(new SchedulingException("The entity was retired"));
            if (!dispatch.apply(run, retired)) {
                future.completeExceptionally(new SchedulingException("The call could not be scheduled"));
            }
        } catch (RuntimeException failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    @Override
    public @NotNull TaskHandle scheduleForEntityLater(@NotNull Entity entity, @NotNull Runnable task,
                                                      @Nullable Runnable retired, long delayTicks) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return TaskHandle.NOOP;
        }
        try {
            return handle(entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), retired,
                    Math.max(1, delayTicks)));
        } catch (IllegalPluginAccessException disabled) {
            return TaskHandle.NOOP;
        }
    }

    @Override
    public @NotNull TaskHandle scheduleGlobalLater(@NotNull Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return TaskHandle.NOOP;
        }
        try {
            return handle(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduled -> task.run(),
                    Math.max(1, delayTicks)));
        } catch (IllegalPluginAccessException disabled) {
            return TaskHandle.NOOP;
        }
    }

    @Override
    public @NotNull TaskHandle scheduleAsyncLater(@NotNull Runnable task, @NotNull Duration delay) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(delay, "delay");
        if (!plugin.isEnabled()) {
            return TaskHandle.NOOP;
        }
        try {
            return handle(Bukkit.getAsyncScheduler().runDelayed(plugin, scheduled -> task.run(),
                    Math.max(1, delay.toMillis()), TimeUnit.MILLISECONDS));
        } catch (IllegalPluginAccessException disabled) {
            return TaskHandle.NOOP;
        }
    }

    private static TaskHandle handle(ScheduledTask task) {
        return task == null ? TaskHandle.NOOP : new Handle(task);
    }

    private static Location locationSnapshot(Location location) {
        Location snapshot = Objects.requireNonNull(location, "location").clone();
        Objects.requireNonNull(snapshot.getWorld(), "location.world");
        return snapshot;
    }

    private record Handle(ScheduledTask task) implements TaskHandle {
        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }
}
