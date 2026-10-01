package net.foliaboard.internal.scheduler;

import net.foliacommons.FoliaEnvironment;
import net.foliacommons.scheduler.Scheduler;
import net.foliacommons.scheduler.TaskHandle;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.function.Consumer;

/** FoliaBoard's view of the scheduler from folia-commons. */
public final class Schedulers {
    private static volatile boolean synchronousForTesting = false;
    private static volatile Scheduler schedulerForTesting;

    private Schedulers() {
    }

    public static boolean isFolia() {
        return FoliaEnvironment.isFolia();
    }

    public static void setSynchronousForTesting(boolean value) {
        synchronousForTesting = value;
    }

    private static Scheduler scheduler(Plugin plugin) {
        if (schedulerForTesting != null) {
            return schedulerForTesting;
        }
        return synchronousForTesting ? Scheduler.synchronous() : Scheduler.forPlugin(plugin);
    }

    public static void setSchedulerForTesting(Scheduler scheduler) {
        schedulerForTesting = scheduler;
    }

    public static TaskHandle entityLater(Plugin plugin, Entity entity, Runnable task, long ticks) {
        return synchronousForTesting ? TaskHandle.NOOP : scheduler(plugin).scheduleForEntityLater(entity, task, null, ticks);
    }

    public static TaskHandle entityTaskTimer(Plugin plugin, Entity entity, Consumer<TaskHandle> task,
                                             long delay, long period) {
        return scheduler(plugin).repeatForEntity(entity, task, null, delay, period);
    }

    public static @NotNull ScheduledHandle globalTimer(@NotNull Plugin plugin, @NotNull Runnable task,
                                                       long delayTicks, long periodTicks) {
        TaskHandle handle = scheduler(plugin).runGlobalTimer(task, delayTicks, periodTicks);
        return handle::cancel;
    }

    public static void async(@NotNull Plugin plugin, @NotNull Runnable task) {
        scheduler(plugin).runAsync(task);
    }

    public static void asyncLater(@NotNull Plugin plugin, @NotNull Runnable task, @NotNull Duration delay) {
        scheduler(plugin).runAsyncLater(task, delay);
    }

    public static void global(@NotNull Plugin plugin, @NotNull Runnable task) {
        scheduler(plugin).runGlobal(task);
    }

    public static boolean onEntity(@NotNull Plugin plugin, @NotNull Entity entity,
                                   @NotNull Runnable task, @Nullable Runnable retired) {
        return scheduler(plugin).runForEntity(entity, task, retired);
    }

    public static boolean onEntity(@NotNull Plugin plugin, @NotNull Entity entity, @NotNull Runnable task) {
        return onEntity(plugin, entity, task, null);
    }

    public static @NotNull ScheduledHandle entityTimer(@NotNull Plugin plugin, @NotNull Entity entity,
                                                       @NotNull Consumer<ScheduledHandle> task,
                                                       long delayTicks, long periodTicks) {
        TaskHandle handle = scheduler(plugin).repeatForEntity(entity, self -> task.accept(self::cancel), null,
                delayTicks, periodTicks);
        return handle::cancel;
    }

    @FunctionalInterface
    public interface ScheduledHandle {
        void cancel();
    }
}
