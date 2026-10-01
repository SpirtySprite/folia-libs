package net.folianpc.internal.protocol;

public record BodySize(double width, double height) {
    public BodySize {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width < 0 || height < 0) {
            throw new IllegalArgumentException("Invalid entity dimensions");
        }
    }
}
