package net.folianpc.internal.pathfinding;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class AStar {

    public interface WorldSampler {
        boolean solid(int x, int y, int z);
        default boolean passable(int x, int y, int z) { return !solid(x, y, z); }
        default boolean ground(int x, int y, int z) { return solid(x, y, z); }
        default double penalty(int x, int y, int z) { return 0; }
    }

    public record Node(int x, int y, int z) {
    }

    private record Open(Node node, double priority) {
    }

    private static final int[][] DIRECTIONS = {
            {0, 1}, {0, -1}, {1, 0}, {-1, 0},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private AStar() {
    }

    public static List<Node> find(WorldSampler world, Node start, Node goal, int maxNodes, int maxRadius) {
        return find(world, start, goal, new net.folianpc.api.NavigationOptions(maxNodes, Math.min(128, maxRadius), 1, 3,
                net.folianpc.api.NavigationOptions.TerrainPolicy.SOLID_GROUND, 0.6, 1.8, false), 0.6, 1.8);
    }

    public static List<Node> find(WorldSampler world, Node start, Node goal,
                                  net.folianpc.api.NavigationOptions options, double width, double height) {
        return find(world, start, goal, options, width, height, () -> false);
    }

    public static List<Node> find(WorldSampler world, Node start, Node goal,
                                  net.folianpc.api.NavigationOptions options, double width, double height, BooleanSupplier cancelled) {
        return find(world, start, options, width, height, cancelled,
                node -> reached(node, goal, options.arrivalRadius()), node -> estimate(node, goal, options.arrivalRadius()));
    }

    public static List<Node> find(WorldSampler world, Node start, net.folianpc.api.NavigationOptions options,
                                  double width, double height, BooleanSupplier cancelled,
                                  Predicate<Node> goal, ToDoubleFunction<Node> heuristic) {
        int maxNodes = options.maxNodes();
        int maxRadius = options.radius();
        Map<Node, Node> cameFrom = new HashMap<>();
        Map<Node, Double> gScore = new HashMap<>();
        Set<Node> closed = new HashSet<>();
        PriorityQueue<Open> open = new PriorityQueue<>(Comparator.comparingDouble(Open::priority));

        gScore.put(start, 0.0);
        open.add(new Open(start, heuristic.applyAsDouble(start)));
        int explored = 0;

        while (!open.isEmpty()) {
            if (cancelled.getAsBoolean()) return List.of();
            Node current = open.poll().node();
            if (!closed.add(current)) {
                continue;
            }
            if (goal.test(current)) {
                return reconstruct(cameFrom, current);
            }
            if (explored >= maxNodes) return List.of();
            explored++;

            for (Node neighbor : neighbors(world, current, options, width, height)) {
                if (closed.contains(neighbor)
                        || Math.abs(neighbor.x() - start.x()) > maxRadius
                        || Math.abs(neighbor.z() - start.z()) > maxRadius) {
                    continue;
                }
                double penalty = world.penalty(neighbor.x(), neighbor.y(), neighbor.z());
                if (!Double.isFinite(penalty) || penalty < 0) throw new IllegalArgumentException("Invalid terrain cost");
                double tentative = score(gScore, current) + stepCost(current, neighbor) + penalty;
                if (tentative < score(gScore, neighbor)) {
                    cameFrom.put(neighbor, current);
                    gScore.put(neighbor, tentative);
                    open.add(new Open(neighbor, tentative + heuristic.applyAsDouble(neighbor)));
                }
            }
        }
        return List.of();
    }

    private static double score(Map<Node, Double> gScore, Node n) {
        return gScore.getOrDefault(n, Double.MAX_VALUE);
    }

    private static List<Node> reconstruct(Map<Node, Node> cameFrom, Node current) {
        Deque<Node> path = new ArrayDeque<>();
        path.addFirst(current);
        Node at = current;
        while (cameFrom.containsKey(at)) {
            at = cameFrom.get(at);
            path.addFirst(at);
        }
        return new ArrayList<>(path);
    }

    private static double estimate(Node a, Node b, double radius) {
        double dx = Math.abs((double) a.x() - b.x());
        double dz = Math.abs((double) a.z() - b.z());
        return Math.max(0, Math.max(dx, dz) + (Math.sqrt(2) - 1) * Math.min(dx, dz)
                + 0.5 * Math.abs((double) a.y() - b.y()) - 2 * radius);
    }

    private static boolean reached(Node a, Node b, double radius) {
        double dx = (double) a.x() - b.x(), dy = (double) a.y() - b.y(), dz = (double) a.z() - b.z();
        return dx*dx + dy*dy + dz*dz <= radius*radius;
    }

    private static double stepCost(Node from, Node to) {
        double dx = (double) from.x() - to.x(), dz = (double) from.z() - to.z();
        double horizontal = Math.sqrt(dx*dx + dz*dz);
        return horizontal + Math.abs(to.y() - from.y()) * 0.5;
    }

    private static List<Node> neighbors(WorldSampler world, Node from, net.folianpc.api.NavigationOptions options, double width, double height) {
        List<Node> result = new ArrayList<>(8);
        for (int[] dir : DIRECTIONS) {
            for (int gap = 0; gap <= (dir[0] != 0 && dir[1] != 0 ? 0 : options.maxJumpGap()); gap++) {
                if (gap > 0) {
                    boolean unsupported = false;
                    for (int step = 1; step <= gap; step++) {
                        if (!world.ground(from.x() + dir[0] * step, from.y() - 1, from.z() + dir[1] * step)) unsupported = true;
                    }
                    if (!unsupported) continue;
                }
                Node landing = landingSpot(world, from, dir[0] * (gap + 1), dir[1] * (gap + 1), options, width, height);
                if (landing != null) result.add(landing);
            }
        }
        return result;
    }

    private static Node landingSpot(WorldSampler world, Node from, int dx, int dz, net.folianpc.api.NavigationOptions options, double width, double height) {
        boolean diagonal = dx != 0 && dz != 0;
        if (diagonal && !openCorner(world, from, dx, dz, width, height)) {
            return null;
        }
        for (int dy = options.stepHeight(); dy >= -options.maxDrop(); dy--) {
            Node candidate = new Node(from.x() + dx, from.y() + dy, from.z() + dz);
            if (clearance(world, candidate.x() + 0.5, candidate.y(), candidate.z() + 0.5, width, height)
                    && MovementGeometry.clear(world, from, candidate, width, height)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean openCorner(WorldSampler world, Node from, int dx, int dz, double width, double height) {
        return bodyClear(world, from.x() + dx + 0.5, from.y(), from.z() + 0.5, width, height)
                && bodyClear(world, from.x() + 0.5, from.y(), from.z() + dz + 0.5, width, height);
    }

    public static boolean clearance(WorldSampler world, double x, double y, double z, double width, double height) {
        int floorY = (int) Math.floor(y) - 1;
        for (int xx = (int) Math.floor(x - width / 2); xx <= (int) Math.floor(x + width / 2 - 1e-7); xx++) {
            for (int zz = (int) Math.floor(z - width / 2); zz <= (int) Math.floor(z + width / 2 - 1e-7); zz++) {
                if (!world.ground(xx, floorY, zz)) {
                    return false;
                }
            }
        }
        return bodyClear(world, x, y, z, width, height);
    }

    public static boolean bodyClear(WorldSampler world, double x, double y, double z, double width, double height) {
        for (int xx = (int) Math.floor(x - width / 2); xx <= (int) Math.floor(x + width / 2 - 1e-7); xx++) {
            for (int zz = (int) Math.floor(z - width / 2); zz <= (int) Math.floor(z + width / 2 - 1e-7); zz++) {
                for (int yy = (int) Math.floor(y); yy <= (int) Math.floor(y + height - 1e-7); yy++) {
                    if (!world.passable(xx, yy, zz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
