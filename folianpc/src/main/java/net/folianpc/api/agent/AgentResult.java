package net.folianpc.api.agent;

import org.jetbrains.annotations.ApiStatus;
import java.util.Objects;
import java.util.Optional;

/** A terminal goal outcome, including action counts and the original cause on failure. */
@ApiStatus.Experimental
public record AgentResult(Status status, int executedActions, Optional<Throwable> failure) {
    /** Distinguishes goal completion, bounded-work exhaustion, failure and lifecycle cancellation. */
    public enum Status { COMPLETED, UNREACHABLE, BUDGET_EXHAUSTED, CANCELLED, SUPERSEDED, TIMED_OUT, REMOVED, SHUTDOWN, FAILED }
    /** Validates action counts and requires a cause exactly for FAILED. */
    public AgentResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(failure, "failure");
        if (executedActions < 0 || (status == Status.FAILED) != failure.isPresent()) throw new IllegalArgumentException("Invalid agent result");
    }
}
