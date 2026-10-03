package net.folianpc.api.agent;

import org.jetbrains.annotations.ApiStatus;
import net.foliacommons.scheduler.TaskHandle;
import java.util.concurrent.CompletableFuture;

/** One submitted goal. Cancelling an observer future does not cancel the goal; cancel this handle explicitly. */
@ApiStatus.Experimental
public interface AgentTask extends TaskHandle, AutoCloseable {
    /** Returns an independent observer of the terminal outcome. Continuations must establish ownership before accessing game state. */
    CompletableFuture<AgentResult> result();
    /** Returns the most recent completed planning pass, when available. */
    java.util.Optional<AgentPlan> plan();
    /** Returns the number of completed action attempts. */
    int executedActions();
    /** Cancels unfinished work. Already committed world or inventory changes are retained. */
    @Override
    void cancel();
    /** Reports whether this goal has entered a terminal outcome and stopped admitting new work. */
    @Override
    boolean isCancelled();
    /** Cancels unfinished work, matching movement and behavior handles. */
    @Override
    default void close() { cancel(); }
}
