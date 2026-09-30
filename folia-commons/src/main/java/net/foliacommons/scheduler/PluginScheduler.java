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

final class PluginScheduler implements Scheduler {
    private final Plugin plugin;

    PluginScheduler(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean runForEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
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
        if (!plugin.isEnabled()) {
            return false;
        }
        try {
            Bukkit.getRegionScheduler().execute(plugin, location, task);
            return true;
        } catch (IllegalPluginAccessException disabledMidCall) {
            return false;
        }
    }

    @Override
    public boolean runGlobal(@NotNull Runnable task) {
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
