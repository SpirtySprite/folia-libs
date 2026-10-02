package net.foliaboard.api.display;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Last viewer evaluation; eligibility is distinct from asynchronous client acknowledgement. */
@ApiStatus.Experimental
public record NametagStatus(Reason reason, int elements) {
    /** Requires a reason and a nonnegative rendered element count. */
    public NametagStatus {
        Objects.requireNonNull(reason, "reason");
        if (elements < 0) throw new IllegalArgumentException("Element count cannot be negative");
    }
    /** Suppression or presentation state. */
    public enum Reason { ELIGIBLE, CLOSED, OFFLINE, NO_OWNER_DATA, OWNER_UNAVAILABLE, SELF_HIDDEN, EXCLUDED,
        GLOBAL_HIDDEN, WORLD, TRACKING, VANISHED, INVISIBLE, SNEAKING, SPECTATOR, RANGE, FILTER, EMPTY, FAILURE }
}
