package net.folianpc.internal.scheduler;

import net.foliacommons.scheduler.Scheduler;
import net.foliacommons.scheduler.TaskHandle;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/** FoliaNPC's view of the scheduler from folia-commons. */
public final class Schedulers {

    private static volatile boolean synchronousForTesting;

    private Schedulers() {
    }

    public static void setSynchronousForTesting(boolean value) {
        synchronousForTesting = value;
    }

    public static boolean synchronousForTesting() {
        return synchronousForTesting;
    }

    public interface Handle {
        void cancel();
    }

    private static Scheduler scheduler(Plugin plugin) {
        if (synchronousForTesting) {
            return Scheduler.synchronous();
        }
        return plugin == null ? null : Scheduler.forPlugin(plugin);
    }

    public static void onEntity(Plugin plugin, Entity entity, Runnable task) {
        Scheduler scheduler = scheduler(plugin);
        if (scheduler != null) {
            scheduler.runForEntity(entity, task, null);
        }
    }

    public static void onEntityLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        Scheduler scheduler = scheduler(plugin);
        if (scheduler == null) {
            return;
        }
        if (delayTicks <= 0) {
            scheduler.runForEntity(entity, task, null);
        } else {
            scheduler.runForEntityLater(entity, task, null, delayTicks);
        }
    }

    public static void global(Plugin plugin, Runnable task) {
        Scheduler scheduler = scheduler(plugin);
        if (scheduler != null) {
            scheduler.runGlobal(task);
        }
    }

    public static void onRegion(Plugin plugin, Location location, Runnable task) {
        Scheduler scheduler = scheduler(plugin);
        if (scheduler != null) {
            scheduler.runForLocation(location, task);
        }
    }

    public static Handle globalTimer(Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        Scheduler scheduler = scheduler(plugin);
        if (scheduler == null) {
            return () -> {
            };
        }
        TaskHandle handle = scheduler.runGlobalTimer(task, initialDelayTicks, periodTicks);
        return handle::cancel;
    }
}
