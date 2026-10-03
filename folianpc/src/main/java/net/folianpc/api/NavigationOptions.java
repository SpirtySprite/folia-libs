package net.folianpc.api;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.Map;
import org.bukkit.Material;

/** Immutable limits for one snapshot-based search. Dimensions of zero derive conservative clearance from the NPC. */
@ApiStatus.Experimental
public record NavigationOptions(int maxNodes, int radius, int stepHeight, int maxDrop,
                                TerrainPolicy terrain, double width, double height, boolean groundFollowing,
                                int maxJumpGap, double arrivalRadius, Map<Material, Double> terrainCosts) {
    /** SOLID_GROUND permits any solid support; AVOID_HAZARDS also rejects liquids, fire and damaging support. */
    public enum TerrainPolicy { SOLID_GROUND, AVOID_HAZARDS }

    /** Validates search budgets and finite bounded clearance dimensions. */
    public NavigationOptions {
        Objects.requireNonNull(terrain, "terrain");
        terrainCosts = Map.copyOf(terrainCosts);
        if (maxNodes < 1 || maxNodes > 100_000 || radius < 1 || radius > 128
                || stepHeight < 0 || stepHeight > 4 || maxDrop < 0 || maxDrop > 16
                || !Double.isFinite(width) || !Double.isFinite(height)
                || width < 0 || width > 16 || height < 0 || height > 32 || maxJumpGap < 0 || maxJumpGap > 3
                || !Double.isFinite(arrivalRadius) || arrivalRadius < 0 || arrivalRadius > 8 || terrainCosts.size() > 64
                || terrainCosts.values().stream().anyMatch(cost -> !Double.isFinite(cost) || cost < 0 || cost > 1000)) {
            throw new IllegalArgumentException("Invalid navigation limits or dimensions");
        }
    }

    /** Preserves the original constructor, with no gap jumping, exact arrival and no terrain penalties. */
    public NavigationOptions(int maxNodes, int radius, int stepHeight, int maxDrop, TerrainPolicy terrain,
                             double width, double height, boolean groundFollowing) {
        this(maxNodes, radius, stepHeight, maxDrop, terrain, width, height, groundFollowing, 0, 0, Map.of());
    }

    /** Copies all settings into an independent builder. */
    public Builder toBuilder() {
        return builder().maxNodes(maxNodes).radius(radius).stepHeight(stepHeight).maxDrop(maxDrop).terrain(terrain)
                .dimensions(width, height).groundFollowing(groundFollowing).maxJumpGap(maxJumpGap)
                .arrivalRadius(arrivalRadius).terrainCosts(terrainCosts);
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
        private int maxJumpGap;
        private double arrivalRadius;
        private Map<Material, Double> terrainCosts = Map.of();

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
        /** Permits collision-checked cardinal jumps over up to three missing support blocks. Zero disables gap jumping. */
        public Builder maxJumpGap(int value) { maxJumpGap = value; return this; }
        /** Accepts any reachable standing point within this radius of the target, allowing approach to occupied blocks. */
        public Builder arrivalRadius(double value) { arrivalRadius = value; return this; }
        /** Copies additional nonnegative costs for support and foot materials. Hazard policy still determines whether a cell is traversable. */
        public Builder terrainCosts(Map<Material, Double> values) { terrainCosts = Map.copyOf(values); return this; }
        /** Freezes and validates these options. */
        public NavigationOptions build() {
            return new NavigationOptions(maxNodes, radius, stepHeight, maxDrop, terrain, width, height, groundFollowing,
                    maxJumpGap, arrivalRadius, terrainCosts);
        }
    }
}
