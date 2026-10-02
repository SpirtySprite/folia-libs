package net.foliaboard.api.display;

import org.jetbrains.annotations.ApiStatus;

/** Optional visibility fade durations. Zero applies visibility immediately. */
@ApiStatus.Experimental
public record NametagTransition(int fadeInTicks, int fadeOutTicks) {
    /** Requires nonnegative fade durations. */
    public NametagTransition {
        if (fadeInTicks < 0 || fadeOutTicks < 0) throw new IllegalArgumentException("Fade duration cannot be negative");
    }
    /** Disables visibility transitions. */
    public static NametagTransition none() { return new NametagTransition(0, 0); }
}
