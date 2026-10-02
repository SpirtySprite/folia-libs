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
    /** Returns a builder initialized with default settings. Builders are confined to the calling thread. */
    public static Builder builder() {
        return defaults().toBuilder();
    }

    /** Returns an independent builder initialized with these settings. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Fluent configuration builder. Build immutable settings before sharing them between threads. */
    @ApiStatus.Experimental
    public static final class Builder {
        private double range;
        private boolean selfVisible;
        private boolean hideInvisible;
        private boolean hideSneaking;
        private boolean hideSpectators;

        private Builder(DisplayVisibility initial) {
            range = initial.range();
            selfVisible = initial.selfVisible();
            hideInvisible = initial.hideInvisible();
            hideSneaking = initial.hideSneaking();
            hideSpectators = initial.hideSpectators();
        }

        /** Sets the positive maximum range, at most 1024 blocks. */
        public Builder range(double value) {
            range = value;
            return this;
        }

        /** Explicitly controls owner visibility for attached displays. */
        public Builder selfVisible(boolean value) {
            selfVisible = value;
            return this;
        }

        /** Hides displays when the owner is invisible. */
        public Builder hideInvisible(boolean value) {
            hideInvisible = value;
            return this;
        }

        /** Hides displays when the owner is sneaking. */
        public Builder hideSneaking(boolean value) {
            hideSneaking = value;
            return this;
        }

        /** Hides displays when the owner is a spectator. */
        public Builder hideSpectators(boolean value) {
            hideSpectators = value;
            return this;
        }

        /** Validates settings and returns an immutable snapshot. */
        public DisplayVisibility build() {
            return new DisplayVisibility(range, selfVisible, hideInvisible, hideSneaking, hideSpectators);
        }
    }

}
