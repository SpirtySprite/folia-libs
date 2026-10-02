package net.foliaboard.api.display;

import org.bukkit.entity.Display;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Immutable display rendering properties. Brightness -1 uses the world's lighting. */
@ApiStatus.Experimental
public record DisplayStyle(Display.Billboard billboard, DisplayTransform transform,
                           int blockLight, int skyLight, int interpolationTicks, int teleportTicks,
                           float shadowRadius, float shadowStrength, float width, float height,
                           boolean glowing, int glowColor) {
    /** Validates rendering limits without accessing the server. */
    public DisplayStyle {
        Objects.requireNonNull(billboard, "billboard");
        Objects.requireNonNull(transform, "transform");
        if (blockLight < -1 || blockLight > 15 || skyLight < -1 || skyLight > 15
                || (blockLight == -1) != (skyLight == -1)) {
            throw new IllegalArgumentException("Both light values must be -1 or between 0 and 15");
        }
        if (interpolationTicks < 0 || teleportTicks < 0 || teleportTicks > 59
                || !Float.isFinite(shadowRadius) || shadowRadius < 0
                || !Float.isFinite(shadowStrength) || shadowStrength < 0 || shadowStrength > 1
                || !Float.isFinite(width) || width < 0 || !Float.isFinite(height) || height < 0) {
            throw new IllegalArgumentException("Invalid display rendering limits");
        }
    }

    /** Returns a centered billboard with no shadow or culling bounds. */
    public static DisplayStyle defaults() {
        return new DisplayStyle(Display.Billboard.CENTER, DisplayTransform.identity(), -1, -1,
                0, 0, 0, 1, 0, 0, false, -1);
    }

    /** Returns a copy with the supplied transformation. */
    public DisplayStyle transformed(DisplayTransform value) {
        return new DisplayStyle(billboard, value, blockLight, skyLight, interpolationTicks, teleportTicks,
                shadowRadius, shadowStrength, width, height, glowing, glowColor);
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
        private Display.Billboard billboard;
        private DisplayTransform transform;
        private int blockLight;
        private int skyLight;
        private int interpolationTicks;
        private int teleportTicks;
        private float shadowRadius;
        private float shadowStrength;
        private float width;
        private float height;
        private boolean glowing;
        private int glowColor;

        private Builder(DisplayStyle initial) {
            billboard = initial.billboard();
            transform = initial.transform();
            blockLight = initial.blockLight();
            skyLight = initial.skyLight();
            interpolationTicks = initial.interpolationTicks();
            teleportTicks = initial.teleportTicks();
            shadowRadius = initial.shadowRadius();
            shadowStrength = initial.shadowStrength();
            width = initial.width();
            height = initial.height();
            glowing = initial.glowing();
            glowColor = initial.glowColor();
        }

        /** Sets the billboard constraint. */
        public Builder billboard(Display.Billboard value) {
            billboard = value;
            return this;
        }

        /** Sets the immutable local transformation. */
        public Builder transform(DisplayTransform value) {
            transform = value;
            return this;
        }

        /** Sets block light; use -1 together with sky light for world lighting. */
        public Builder blockLight(int value) {
            blockLight = value;
            return this;
        }

        /** Sets sky light from 0 to 15 or -1 for world lighting. */
        public Builder skyLight(int value) {
            skyLight = value;
            return this;
        }

        /** Sets transformation interpolation duration. */
        public Builder interpolationTicks(int value) {
            interpolationTicks = value;
            return this;
        }

        /** Sets position interpolation duration from 0 to 59 ticks. */
        public Builder teleportTicks(int value) {
            teleportTicks = value;
            return this;
        }

        /** Sets the nonnegative shadow radius. */
        public Builder shadowRadius(float value) {
            shadowRadius = value;
            return this;
        }

        /** Sets shadow opacity from 0 to 1. */
        public Builder shadowStrength(float value) {
            shadowStrength = value;
            return this;
        }

        /** Sets nonnegative culling width; zero disables horizontal culling. */
        public Builder width(float value) {
            width = value;
            return this;
        }

        /** Sets nonnegative culling height; zero disables vertical culling. */
        public Builder height(float value) {
            height = value;
            return this;
        }

        /** Enables the glowing outline. */
        public Builder glowing(boolean value) {
            glowing = value;
            return this;
        }

        /** Sets packed RGB glow color or -1 for the default. */
        public Builder glowColor(int value) {
            glowColor = value;
            return this;
        }

        /** Validates settings and returns an immutable snapshot. */
        public DisplayStyle build() {
            return new DisplayStyle(billboard, transform, blockLight, skyLight, interpolationTicks, teleportTicks, shadowRadius, shadowStrength, width, height, glowing, glowColor);
        }
    }

}
