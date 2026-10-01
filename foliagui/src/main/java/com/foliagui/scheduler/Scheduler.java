package com.foliagui.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Scheduler {

    void runForEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired);

    void runForEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks);

    @NotNull
    TaskHandle runForEntityTimer(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired,
                                 long initialDelayTicks, long periodTicks);

    void runForLocation(@NotNull Location location, @NotNull Runnable task);

    void runGlobal(@NotNull Runnable task);

    void runAsync(@NotNull Runnable task);

    /** Reports acceptance for asynchronous work. Legacy implementations assume acceptance after dispatch. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    default boolean tryRunAsync(@NotNull Runnable task) {
        runAsync(task);
        return true;
    }

    /** Reports acceptance for region-owned location work. Legacy implementations assume acceptance after dispatch. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    default boolean tryRunForLocation(@NotNull Location location, @NotNull Runnable task) {
        runForLocation(location, task);
        return true;
    }

    boolean isFolia();
}
