package net.folianpc.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The players of one world, bucketed into square cells on the horizontal plane, so that "who is within
 * R blocks of this point" only looks at the few cells around the point instead of at every player.
 *
 * <p>The result is a superset of the players within R blocks (everything in the overlapping cells); the caller
 * still does the exact distance check. A grid is rebuilt for every visibility pass and is only used by the
 * thread that runs the pass.
 */
final class PlayerGrid {

    /** Edge length of a cell in blocks. Chosen near the default view distance so a query touches about 9 cells. */
    static final int CELL = 32;

    /** With this few players, looking at all of them is cheaper than looking up cells. */
    static final int LINEAR_LIMIT = 16;

    private final Map<Long, List<PlayerTracker.Tracked>> cells = new HashMap<>();
    private final List<PlayerTracker.Tracked> all = new ArrayList<>();

    void add(PlayerTracker.Tracked player) {
        all.add(player);
        cells.computeIfAbsent(key(cell(player.x()), cell(player.z())), k -> new ArrayList<>(4)).add(player);
    }

    int size() {
        return all.size();
    }

    /**
     * The players that might be within {@code radius} blocks of ({@code x}, {@code z}): never misses one that is,
     * may include some that are not. The result is either this grid's own list of everybody, which must not be
     * modified, or {@code scratch} after it was filled. Callers clear {@code scratch} before each call.
     */
    List<PlayerTracker.Tracked> near(double x, double z, double radius, List<PlayerTracker.Tracked> scratch) {
        if (all.size() <= LINEAR_LIMIT || !(radius < 1.0e9)) {
            // Few players, or a radius so large (or NaN) that its cell arithmetic would overflow: take everyone.
            return all;
        }
        long minX = cell(x - radius);
        long maxX = cell(x + radius);
        long minZ = cell(z - radius);
        long maxZ = cell(z + radius);
        long wanted = (maxX - minX + 1) * (maxZ - minZ + 1);
        if (wanted > cells.size()) {
            // A big radius would visit more empty cells than there are occupied ones: take everyone.
            return all;
        }
        for (long cx = minX; cx <= maxX; cx++) {
            for (long cz = minZ; cz <= maxZ; cz++) {
                List<PlayerTracker.Tracked> bucket = cells.get(key(cx, cz));
                if (bucket != null) {
                    scratch.addAll(bucket);
                }
            }
        }
        return scratch;
    }

    private static long cell(double coordinate) {
        return (long) Math.floor(coordinate / CELL);
    }

    private static long key(long cx, long cz) {
        return (cx << 32) ^ (cz & 0xFFFFFFFFL);
    }
}
