package net.folianpc.api;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.Optional;

/** A route setup or terminal movement outcome. FAILED retains its cause without blocking a server thread. */
@ApiStatus.Experimental
public record MovementResult(Status status, Optional<Throwable> failure) {
    /** ROUTE_FOUND belongs to route setup; ARRIVED belongs to completed travel. Other values end the request. */
    public enum Status { ROUTE_FOUND, ARRIVED, CANCELLED, SUPERSEDED, UNREACHABLE, FAILED, REMOVED, SHUTDOWN }

    /** Requires a cause exactly when the outcome is FAILED. */
    public MovementResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(failure, "failure");
        if ((status == Status.FAILED) != failure.isPresent()) {
            throw new IllegalArgumentException("Only FAILED carries a cause");
        }
    }

    /** Creates an outcome without a failure cause. */
    public static MovementResult of(Status status) {
        return new MovementResult(status, Optional.empty());
    }

    /** Creates a failed outcome retaining the original cause. */
    public static MovementResult failed(Throwable cause) {
        return new MovementResult(Status.FAILED, Optional.of(cause));
    }
}
