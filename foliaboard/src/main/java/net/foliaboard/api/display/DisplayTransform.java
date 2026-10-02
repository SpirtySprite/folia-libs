package net.foliaboard.api.display;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Immutable translation, rotations and scale, safe to share between threads. */
@ApiStatus.Experimental
public record DisplayTransform(Vector translation, Rotation leftRotation, Vector scale, Rotation rightRotation) {
    /** Requires finite components and nonzero, normalized rotation quaternions. */
    public DisplayTransform {
        Objects.requireNonNull(translation, "translation");
        Objects.requireNonNull(leftRotation, "leftRotation");
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(rightRotation, "rightRotation");
    }

    /** Returns an untransformed display. */
    public static DisplayTransform identity() {
        return new DisplayTransform(new Vector(0, 0, 0), new Rotation(0, 0, 0, 1),
                new Vector(1, 1, 1), new Rotation(0, 0, 0, 1));
    }

    /** Returns a copy with a translation in display-local coordinates. */
    public DisplayTransform translated(float x, float y, float z) {
        return new DisplayTransform(new Vector(x, y, z), leftRotation, scale, rightRotation);
    }

    /** Returns a copy with the requested scale. */
    public DisplayTransform scaled(float x, float y, float z) {
        return new DisplayTransform(translation, leftRotation, new Vector(x, y, z), rightRotation);
    }

    /** A finite immutable three-dimensional vector. */
    public record Vector(float x, float y, float z) {
        /** Rejects non-finite coordinates. */
        public Vector {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                throw new IllegalArgumentException("Vector components must be finite");
            }
        }
    }

    /** An immutable unit quaternion in x, y, z, w order. */
    public record Rotation(float x, float y, float z, float w) {
        /** Normalizes finite, nonzero input. */
        public Rotation {
            double length = Math.sqrt((double) x * x + (double) y * y + (double) z * z + (double) w * w);
            if (!Double.isFinite(length) || length == 0) {
                throw new IllegalArgumentException("Rotation must be finite and nonzero");
            }
            x = (float) (x / length);
            y = (float) (y / length);
            z = (float) (z / length);
            w = (float) (w / length);
        }
    }
}
