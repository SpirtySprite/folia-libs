package net.foliaboard.api.display;

import org.jetbrains.annotations.ApiStatus;

/** Optional distance fade and scale. Element distance bands independently select layout details. */
@ApiStatus.Experimental
public record NametagDistance(double fadeStart, double fadeEnd, float nearScale, float farScale) {
    /** Requires ordered finite nonnegative distances and positive finite scales. */
    public NametagDistance {
        if (!Double.isFinite(fadeStart) || !Double.isFinite(fadeEnd) || fadeStart < 0 || fadeEnd < fadeStart
                || !Float.isFinite(nearScale) || !Float.isFinite(farScale) || nearScale <= 0 || farScale <= 0) {
            throw new IllegalArgumentException("Invalid distance presentation");
        }
    }
    /** Disables distance styling within the supported display range. */
    public static NametagDistance none() { return new NametagDistance(1024, 1024, 1, 1); }
    /** Returns text opacity from zero to one at the supplied distance. */
    public double opacity(double distance) {
        if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Distance must be finite and nonnegative");
        return distance <= fadeStart ? 1 : distance >= fadeEnd ? 0 : (fadeEnd - distance) / (fadeEnd - fadeStart);
    }
    /** Returns the scale multiplier at the supplied distance. */
    public float scale(double distance) {
        double opacity = opacity(distance);
        return (float) (farScale + (nearScale - farScale) * opacity);
    }
}
