package net.folianpc.internal.pathfinding;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public final class RoutePlanner {

    private final int maxNodes;
    private final int maxRadius;

    public RoutePlanner() {
        this(4000, 128);
    }

    public RoutePlanner(int maxNodes, int maxRadius) {
        this.maxNodes = maxNodes;
        this.maxRadius = maxRadius;
    }

    public List<double[]> route(World world, double fromX, double fromY, double fromZ,
                                double toX, double toY, double toZ) {
        return route(new BukkitWorldSampler(world), fromX, fromY, fromZ, toX, toY, toZ,
                new net.folianpc.api.NavigationOptions(maxNodes, maxRadius, 1, 3,
                        net.folianpc.api.NavigationOptions.TerrainPolicy.SOLID_GROUND, 0.6, 1.8, false), 0.6, 1.8);
    }

    public List<double[]> route(AStar.WorldSampler sampler, double fromX, double fromY, double fromZ,
                                double toX, double toY, double toZ, net.folianpc.api.NavigationOptions options,
                                double width, double height) {
        return route(sampler, fromX, fromY, fromZ, toX, toY, toZ, options, width, height, () -> false);
    }

    public List<double[]> route(AStar.WorldSampler sampler, double fromX, double fromY, double fromZ,
                                double toX, double toY, double toZ, net.folianpc.api.NavigationOptions options,
                                double width, double height, java.util.function.BooleanSupplier cancelled) {
        if (options.groundFollowing() && options.arrivalRadius() == 0) {
            boolean found = false;
            for (int offset = options.stepHeight(); offset >= -options.maxDrop(); offset--) {
                double landing = Math.floor(toY) + offset;
                if (AStar.clearance(sampler, toX, landing, toZ, width, height)) {
                    toY = landing;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return List.of();
            }
        }
        if (options.arrivalRadius() == 0 && !AStar.clearance(sampler, toX, toY, toZ, width, height)) {
            return List.of();
        }
        AStar.Node start = new AStar.Node(floor(fromX), floor(fromY), floor(fromZ));
        AStar.Node goal = new AStar.Node(floor(toX), floor(toY), floor(toZ));
        final double targetX = toX, targetY = toY, targetZ = toZ;
        List<AStar.Node> path = options.arrivalRadius() == 0
                ? AStar.find(sampler, start, goal, options, width, height, cancelled)
                : AStar.find(sampler, start, options, width, height, cancelled,
                        node -> distance(node, targetX, targetY, targetZ) <= options.arrivalRadius() * options.arrivalRadius()
                                && AStar.clearance(sampler, node.x() + 0.5, node.y(), node.z() + 0.5, width, height),
                        node -> estimate(node, targetX, targetY, targetZ, options.arrivalRadius()));
        if (path.isEmpty()) {
            return List.of();
        }
        List<double[]> waypoints = new ArrayList<>(path.size());
        AStar.Node previous = null;
        for (AStar.Node node : path) {
            if (previous != null) waypoints.addAll(MovementGeometry.transition(previous, node));
            else waypoints.add(new double[]{node.x() + 0.5, node.y(), node.z() + 0.5});
            previous = node;
        }
        if (options.arrivalRadius() == 0) waypoints.set(waypoints.size() - 1, new double[]{toX, toY, toZ});
        double[] last = new double[]{fromX, fromY, fromZ};
        for (double[] next : waypoints) {
            if (cancelled.getAsBoolean()) return List.of();
            double distance = Math.max(Math.abs(next[0] - last[0]),
                    Math.max(Math.abs(next[1] - last[1]), Math.abs(next[2] - last[2])));
            int samples = Math.max(1, (int) Math.ceil(distance / 0.1));
            for (int sample = 0; sample <= samples; sample++) {
                double fraction = (double) sample / samples;
                if (!AStar.bodyClear(sampler, last[0] + (next[0] - last[0]) * fraction,
                        last[1] + (next[1] - last[1]) * fraction, last[2] + (next[2] - last[2]) * fraction, width, height)) {
                    return List.of();
                }
            }
            last = next;
        }
        return waypoints;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static double distance(AStar.Node node, double x, double y, double z) {
        double dx = node.x() + 0.5 - x, dy = node.y() - y, dz = node.z() + 0.5 - z;
        return dx*dx + dy*dy + dz*dz;
    }

    private static double estimate(AStar.Node node, double x, double y, double z, double radius) {
        double dx = Math.abs(node.x() + 0.5 - x), dz = Math.abs(node.z() + 0.5 - z);
        return Math.max(0, Math.max(dx, dz) + (Math.sqrt(2) - 1) * Math.min(dx, dz)
                + Math.abs(node.y() - y) * 0.5 - 2 * radius);
    }
}
