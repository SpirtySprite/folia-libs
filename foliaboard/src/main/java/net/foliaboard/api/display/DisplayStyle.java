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
}
