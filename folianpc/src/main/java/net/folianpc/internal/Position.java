package net.folianpc.internal;

public record Position(String world, double x, double y, double z, float yaw, float pitch) {

    public Position {
        java.util.Objects.requireNonNull(world, "world");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Position values must be finite");
        }
    }

    public static final double EYE_HEIGHT = 1.62;

    public double distanceSquared(double ox, double oy, double oz) {
        double dx = x - ox;
        double dy = y - oy;
        double dz = z - oz;
        return dx * dx + dy * dy + dz * dz;
    }
}
