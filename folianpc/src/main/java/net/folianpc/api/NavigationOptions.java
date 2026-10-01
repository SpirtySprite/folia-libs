package net.folianpc.api;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Immutable limits for one snapshot-based search. Dimensions of zero derive conservative clearance from the NPC. */
@ApiStatus.Experimental
public record NavigationOptions(int maxNodes, int radius, int stepHeight, int maxDrop,
                                TerrainPolicy terrain, double width, double height, boolean groundFollowing) {
    /** SOLID_GROUND permits any solid support; AVOID_HAZARDS also rejects liquids, fire and damaging support. */
    public enum TerrainPolicy { SOLID_GROUND, AVOID_HAZARDS }

    /** Validates search budgets and finite bounded clearance dimensions. */
    public NavigationOptions {
        Objects.requireNonNull(terrain, "terrain");
        if (maxNodes < 1 || maxNodes > 100_000 || radius < 1 || radius > 128
                || stepHeight < 0 || stepHeight > 4 || maxDrop < 0 || maxDrop > 16
                || !Double.isFinite(width) || !Double.isFinite(height)
                || width < 0 || width > 16 || height < 0 || height > 32) {
            throw new IllegalArgumentException("Invalid navigation limits or dimensions");
        }
    }

    /** Preserves the existing node, radius, step and drop limits, with hazard avoidance enabled. */
    public static NavigationOptions defaults() {
        return builder().build();
    }

    /** Starts an independent options builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Configures bounded terrain sampling and route computation. */
    public static final class Builder {
        private int maxNodes = 4000;
        private int radius = 128;
        private int stepHeight = 1;
        private int maxDrop = 3;
        private TerrainPolicy terrain = TerrainPolicy.AVOID_HAZARDS;
        private double width;
        private double height;
        private boolean groundFollowing;

        private Builder() {
        }

        /** Bounds explored search nodes, from 1 to 100000. */
        public Builder maxNodes(int value) { maxNodes = value; return this; }
        /** Bounds horizontal search distance from its origin, from 1 to 128 blocks. */
        public Builder radius(int value) { radius = value; return this; }
        /** Sets the permitted integer rise, from zero to four blocks. */
        public Builder stepHeight(int value) { stepHeight = value; return this; }
        /** Sets the permitted drop, from zero to sixteen blocks. */
        public Builder maxDrop(int value) { maxDrop = value; return this; }
        /** Selects ground and hazard rules. */
        public Builder terrain(TerrainPolicy value) { terrain = value; return this; }
        /** Overrides clearance width and height in blocks. Zero derives the respective dimension. */
        public Builder dimensions(double width, double height) { this.width = width; this.height = height; return this; }
        /** Resolves the target's landing height within the configured rise and drop limits. */
        public Builder groundFollowing(boolean value) { groundFollowing = value; return this; }
        /** Freezes and validates these options. */
        public NavigationOptions build() {
            return new NavigationOptions(maxNodes, radius, stepHeight, maxDrop, terrain, width, height, groundFollowing);
        }
    }
}
