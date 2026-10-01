package net.folianpc.internal.pathfinding;

import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public final class RoutePlanner {

    private static final double JUMP_ARC_HEIGHT = 0.35;

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
        if (options.groundFollowing()) {
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
        if (!AStar.clearance(sampler, toX, toY, toZ, width, height)) {
            return List.of();
        }
        AStar.Node start = new AStar.Node(floor(fromX), floor(fromY), floor(fromZ));
        AStar.Node goal = new AStar.Node(floor(toX), floor(toY), floor(toZ));
        List<AStar.Node> path = AStar.find(sampler, start, goal, options, width, height);
        if (path.isEmpty()) {
            return List.of();
        }
        List<double[]> waypoints = new ArrayList<>(path.size());
        AStar.Node previous = null;
        for (AStar.Node node : path) {
            if (previous != null && node.y() > previous.y()) {
                waypoints.add(arcPeak(previous, node));
            }
            waypoints.add(new double[]{node.x() + 0.5, node.y(), node.z() + 0.5});
            previous = node;
        }
        waypoints.set(waypoints.size() - 1, new double[]{toX, toY, toZ});
        return waypoints;
    }

    private static double[] arcPeak(AStar.Node from, AStar.Node to) {
        double midX = (from.x() + to.x()) / 2.0 + 0.5;
        double midZ = (from.z() + to.z()) / 2.0 + 0.5;
        return new double[]{midX, to.y() + JUMP_ARC_HEIGHT, midZ};
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
