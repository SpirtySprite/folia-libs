package net.folianpc.internal.pathfinding;

import java.util.List;

final class MovementGeometry {
    private MovementGeometry() { }

    static List<double[]> transition(AStar.Node from, AStar.Node to) {
        double x = from.x() + 0.5;
        double z = from.z() + 0.5;
        double tx = to.x() + 0.5;
        double tz = to.z() + 0.5;
        double distance = Math.max(Math.abs(to.x() - from.x()), Math.abs(to.z() - from.z()));
        if (distance > 1) {
            double peak = Math.max(from.y(), to.y()) + 1.25;
            return List.of(new double[]{x, from.y() + 0.35, z},
                    new double[]{(x + tx) / 2, peak, (z + tz) / 2}, new double[]{tx, to.y(), tz});
        }
        if (to.y() > from.y()) {
            return List.of(new double[]{x, to.y() + 0.35, z},
                    new double[]{(x + tx) / 2, to.y() + 0.35, (z + tz) / 2}, new double[]{tx, to.y(), tz});
        }
        if (to.y() < from.y()) return List.of(new double[]{tx, from.y(), tz}, new double[]{tx, to.y(), tz});
        return List.of(new double[]{tx, to.y(), tz});
    }

    static boolean clear(AStar.WorldSampler world, AStar.Node from, AStar.Node to, double width, double height) {
        double[] last = {from.x() + 0.5, from.y(), from.z() + 0.5};
        for (double[] next : transition(from, to)) {
            double distance = Math.max(Math.abs(next[0] - last[0]), Math.max(Math.abs(next[1] - last[1]), Math.abs(next[2] - last[2])));
            int samples = Math.max(1, (int) Math.ceil(distance / 0.1));
            for (int sample = 0; sample <= samples; sample++) {
                double fraction = (double) sample / samples;
                if (!AStar.bodyClear(world, last[0] + (next[0] - last[0]) * fraction,
                        last[1] + (next[1] - last[1]) * fraction, last[2] + (next[2] - last[2]) * fraction, width, height)) return false;
            }
            last = next;
        }
        return true;
    }
}
