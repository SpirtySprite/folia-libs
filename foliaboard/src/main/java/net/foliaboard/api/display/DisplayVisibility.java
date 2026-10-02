package net.foliaboard.api.display;

import org.jetbrains.annotations.ApiStatus;

/** Persistent visibility policy. Self hiding and exclusions always take precedence over viewer predicates. */
@ApiStatus.Experimental
public record DisplayVisibility(double range, boolean selfVisible, boolean hideInvisible,
                                boolean hideSneaking, boolean hideSpectators) {
    /** Requires a finite positive viewing range. */
    public DisplayVisibility {
        if (!Double.isFinite(range) || range <= 0 || range > 1024) {
            throw new IllegalArgumentException("Range must be positive and at most 1024 blocks");
        }
    }

    /** Returns a 48-block policy that never shows attached displays to their owner. */
    public static DisplayVisibility defaults() {
        return new DisplayVisibility(48, false, true, false, true);
    }
}
