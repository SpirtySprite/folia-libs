package net.foliabench.npc;

import net.folianpc.internal.pathfinding.AStar;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Finding a walking route on a flat floor with scattered two-block-high obstacles. The world is a pure
 * function, so the benchmark measures the search, not chunk access.
 *
 * <p>{@code findRoute} always has a route: the start and goal columns are kept clear and the setup fails if
 * no route is found. {@code giveUpOnUnreachableGoal} asks for a goal beyond the search radius, which the
 * library has to abandon after exploring its node limit, so it shows the cost of the worst case.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class PathfindingBenchmark {
    private static final int MAX_NODES = 4000;
    private static final int MAX_RADIUS = 128;

    @Param({"16", "32", "64"})
    public int distance;

    /** Percentage of columns that hold an obstacle. */
    @Param({"0", "20"})
    public int obstacles;

    private AStar.WorldSampler world;
    private AStar.Node start;
    private AStar.Node goal;
    private AStar.Node farAway;

    @Setup
    public void setUp() {
        int density = obstacles;
        int goalX = distance;
        int goalZ = distance / 2;
        world = (x, y, z) -> {
            if (y <= 63) {
                return true;
            }
            if (y > 65 || density == 0) {
                return false;
            }
            boolean clearColumn = (x == 0 && z == 0) || (x == goalX && z == goalZ);
            int hash = (x * 73856093) ^ (z * 19349663);
            return Math.floorMod(hash, 100) < density && !clearColumn;
        };
        start = new AStar.Node(0, 64, 0);
        goal = new AStar.Node(goalX, 64, goalZ);
        farAway = new AStar.Node(MAX_RADIUS * 2, 64, 0);
        if (AStar.find(world, start, goal, MAX_NODES, MAX_RADIUS).isEmpty()) {
            throw new IllegalStateException("distance=" + distance + " obstacles=" + obstacles
                    + "% must have a route, or the benchmark measures a failed search");
        }
        if (!AStar.find(world, start, farAway, MAX_NODES, MAX_RADIUS).isEmpty()) {
            throw new IllegalStateException("a goal beyond the search radius must not be reachable");
        }
    }

    @Benchmark
    public List<AStar.Node> findRoute() {
        return AStar.find(world, start, goal, MAX_NODES, MAX_RADIUS);
    }

    @Benchmark
    public List<AStar.Node> giveUpOnUnreachableGoal() {
        return AStar.find(world, start, farAway, MAX_NODES, MAX_RADIUS);
    }
}
