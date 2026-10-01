package net.folianpc.api;

import org.jetbrains.annotations.ApiStatus;
import java.util.Objects;
import java.util.Optional;

/** Result of applying a pending skin without replacing a newer skin choice. */
@ApiStatus.Experimental
public record SkinApplyResult(Status status, Optional<Throwable> failure) {
    /** APPLIED changes the skin; supersession, removal and shutdown discard it; FAILED preserves the fetch cause. */
    public enum Status { APPLIED, SUPERSEDED, REMOVED, SHUTDOWN, FAILED }
    /** Requires a failure cause exactly for FAILED. */
    public SkinApplyResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(failure, "failure");
        if ((status == Status.FAILED) != failure.isPresent()) throw new IllegalArgumentException("Invalid skin apply result");
    }
}
