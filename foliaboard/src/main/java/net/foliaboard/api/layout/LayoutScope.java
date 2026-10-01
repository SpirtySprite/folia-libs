package net.foliaboard.api.layout;

import net.foliacommons.scheduler.TaskHandle;
import org.jetbrains.annotations.ApiStatus;

/** Temporary layout lifetime. Closing from any thread schedules restoration on the player's thread. */
@ApiStatus.Experimental
public interface LayoutScope extends TaskHandle, AutoCloseable {
    /** Ends this scope once. Closing a superseded scope does not overwrite a newer manual selection. */
    @Override
    void close();

    /** Equivalent to closing this scope. */
    @Override
    default void cancel() {
        close();
    }
}
