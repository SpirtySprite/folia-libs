package net.foliaboard.api.display;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Reusable developer-defined layout and presentation policies, with no predefined rank or status content. */
@ApiStatus.Experimental
public record NametagProfile(NametagLayout layout, DisplayVisibility visibility, NametagRefresh refresh,
                             NametagTransition transition, NametagDistance distance) {
    /** Requires immutable policy values. */
    public NametagProfile {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(refresh, "refresh");
        Objects.requireNonNull(transition, "transition");
        Objects.requireNonNull(distance, "distance");
    }
    /** Returns an empty default profile builder, confined to the calling thread. */
    public static Builder builder() {
        return new Builder(new NametagProfile(NametagLayout.empty(), DisplayVisibility.defaults(),
                NametagRefresh.everyTick(), NametagTransition.none(), NametagDistance.none()));
    }
    /** Copies settings into an independent builder. */
    public Builder toBuilder() {
        return new Builder(this);
    }
    /** Fluent immutable-profile configuration. */
    public static final class Builder {
        private NametagLayout layout;
        private DisplayVisibility visibility;
        private NametagRefresh refresh;
        private NametagTransition transition;
        private NametagDistance distance;

        private Builder(NametagProfile profile) {
            layout = profile.layout;
            visibility = profile.visibility;
            refresh = profile.refresh;
            transition = profile.transition;
            distance = profile.distance;
        }
        /** Sets the complete composition. */
        public Builder layout(NametagLayout value) {
            layout = value;
            return this;
        }
        /** Sets persistent visibility policy. */
        public Builder visibility(DisplayVisibility value) {
            visibility = value;
            return this;
        }
        /** Sets independent owner and viewer refresh periods. */
        public Builder refresh(NametagRefresh value) {
            refresh = value;
            return this;
        }
        /** Sets optional visibility fades. */
        public Builder transition(NametagTransition value) {
            transition = value;
            return this;
        }
        /** Sets optional distance styling. */
        public Builder distance(NametagDistance value) {
            distance = value;
            return this;
        }
        /** Returns a validated immutable profile. */
        public NametagProfile build() {
            return new NametagProfile(layout, visibility, refresh, transition, distance);
        }
    }
}
