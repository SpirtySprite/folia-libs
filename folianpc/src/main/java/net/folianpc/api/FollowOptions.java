package net.folianpc.api;

import org.jetbrains.annotations.ApiStatus;
import java.util.Objects;

/** Immutable follow settings. Targets outside maxDistance or in another world end with UNREACHABLE. */
@ApiStatus.Experimental
public record FollowOptions(double speed, double minDistance, double maxDistance, long repathTicks,
                            NavigationOptions navigation) {
    /** Requires positive finite speed, ordered finite distances and at least two ticks between target checks. */
    public FollowOptions {
        Objects.requireNonNull(navigation, "navigation");
        if (!Double.isFinite(speed) || speed <= 0 || !Double.isFinite(minDistance) || minDistance < 0
                || !Double.isFinite(maxDistance) || maxDistance <= minDistance || repathTicks < 2) {
            throw new IllegalArgumentException("Invalid follow options");
        }
    }
    /** Walks at four blocks per second, holding within two blocks and checking every ten ticks, up to 64 blocks away. */
    public static FollowOptions defaults() { return new FollowOptions(4, 2, 64, 10, NavigationOptions.defaults()); }
}
