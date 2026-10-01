package net.folianpc.api;

import net.foliacommons.scheduler.TaskHandle;
import org.jetbrains.annotations.ApiStatus;
import java.util.concurrent.CompletableFuture;

/** A managed patrol or follow operation. Manual movement, removal and shutdown terminate it. */
@ApiStatus.Experimental
public interface BehaviorTask extends TaskHandle, AutoCloseable {
    /** Returns an independent observer for the terminal outcome. A repeating patrol or follow remains pending until stopped. */
    CompletableFuture<MovementResult> result();
    /** Stops the behavior and its pending navigation. Safe from any thread. */
    @Override void cancel();
    /** Whether the behavior has terminated, including arrival or failure. */
    @Override boolean isCancelled();
    /** Stops the behavior. */
    @Override default void close() { cancel(); }
}
