package net.folianpc.api;

import org.jetbrains.annotations.ApiStatus;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Typed skin lookup outcome, including retry guidance without blocking a server thread. */
@ApiStatus.Experimental
public record SkinFetchResult(Status status, Optional<Skin> skin, Duration retryAfter, Optional<Throwable> failure) {
    /** Distinguishes successful lookup, missing profile, remote limits, malformed data, network failure, saturation and shutdown. */
    public enum Status { FOUND, NOT_FOUND, RATE_LIMITED, MALFORMED, UNAVAILABLE, BUSY, SHUTDOWN }
    /** Requires a skin exactly for FOUND and a nonnegative retry duration. */
    public SkinFetchResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(skin, "skin");
        Objects.requireNonNull(retryAfter, "retryAfter");
        Objects.requireNonNull(failure, "failure");
        if ((status == Status.FOUND) != skin.isPresent() || retryAfter.isNegative()) {
            throw new IllegalArgumentException("Invalid skin outcome");
        }
    }
    /** Returns the fetched skin or a required caller-supplied fallback. */
    public Skin skinOr(Skin fallback) { return skin.orElse(Objects.requireNonNull(fallback, "fallback")); }
}
