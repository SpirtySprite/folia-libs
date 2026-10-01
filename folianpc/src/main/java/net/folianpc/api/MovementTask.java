package net.folianpc.api;

import net.foliacommons.scheduler.TaskHandle;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.CompletableFuture;

/** One cancellable movement request. Continuations may run on a region, async worker, or cancelling caller. */
@ApiStatus.Experimental
public interface MovementTask extends TaskHandle, AutoCloseable {
    /** Reports route setup, or the outcome that prevented setup. The returned future is an isolated observer. */
    CompletableFuture<MovementResult> route();

    /** Reports arrival or a terminal interruption. The returned future is an isolated observer. */
    CompletableFuture<MovementResult> result();

    /** Cancels the request and prevents late terrain or route results from starting movement. Safe from any thread. */
    @Override
    void cancel();

    /** True after the request has reached any terminal outcome. */
    @Override
    boolean isCancelled();

    /** Cancels this movement request. */
    @Override
    default void close() {
        cancel();
    }
}
