package net.folianpc.api.agent;

import org.jetbrains.annotations.ApiStatus;
import java.util.concurrent.CompletionStage;

/** Executes an action without blocking. Live game access must use the context's owning scheduler and commit gate. */
@ApiStatus.Experimental
@FunctionalInterface
public interface AgentAction {
    /** Starts execution; the completion reports whether the action succeeded. Exceptions retain their cause in the goal result. */
    CompletionStage<Boolean> execute(AgentContext context);
}
