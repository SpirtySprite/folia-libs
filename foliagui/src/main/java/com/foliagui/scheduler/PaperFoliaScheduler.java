package com.foliagui.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** FoliaGUI's {@link Scheduler}, backed by the scheduler in folia-commons. */
public final class PaperFoliaScheduler implements Scheduler {

    private static final TaskHandle STOPPED = new TaskHandle() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };

    private final net.foliacommons.scheduler.Scheduler delegate;

    public PaperFoliaScheduler(@NotNull Plugin plugin) {
        this(net.foliacommons.scheduler.Scheduler.forPlugin(plugin));
    }

    /** Wraps any commons scheduler, for example {@code Scheduler.synchronous()} in tests. */
    public PaperFoliaScheduler(@NotNull net.foliacommons.scheduler.Scheduler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void runForEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        delegate.runForEntity(entity, task, retired);
    }

    @Override
    public void runForEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired,
                                  long delayTicks) {
        delegate.runForEntityLater(entity, task, retired, delayTicks);
    }

    @Override
    public @NotNull TaskHandle runForEntityTimer(@NotNull Entity entity, @NotNull Runnable task,
                                                 @Nullable Runnable retired, long initialDelayTicks,
                                                 long periodTicks) {
        net.foliacommons.scheduler.TaskHandle handle =
                delegate.runForEntityTimer(entity, task, retired, initialDelayTicks, periodTicks);
        return handle == net.foliacommons.scheduler.TaskHandle.NOOP ? STOPPED : new Adapter(handle);
    }

    @Override
    public void runForLocation(@NotNull Location location, @NotNull Runnable task) {
        delegate.runForLocation(location, task);
    }

    @Override
    public void runGlobal(@NotNull Runnable task) {
        delegate.runGlobal(task);
    }

    @Override
    public void runAsync(@NotNull Runnable task) {
        delegate.runAsync(task);
    }

    @Override
    public boolean isFolia() {
        return delegate.isFolia();
    }

    private record Adapter(net.foliacommons.scheduler.TaskHandle handle) implements TaskHandle {
        @Override
        public void cancel() {
            handle.cancel();
        }

        @Override
        public boolean isCancelled() {
            return handle.isCancelled();
        }
    }
}
