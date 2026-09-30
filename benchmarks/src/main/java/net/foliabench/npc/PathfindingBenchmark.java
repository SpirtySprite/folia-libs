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
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class PathfindingBenchmark {
    @Param({"16", "32", "64"})
    public int distance;

    /** Percentage of columns that hold an obstacle. */
    @Param({"0", "20"})
    public int obstacles;

    private AStar.WorldSampler world;
    private AStar.Node start;
    private AStar.Node goal;

    @Setup
    public void setUp() {
        int density = obstacles;
        world = (x, y, z) -> {
            if (y <= 63) {
                return true;
            }
            if (y > 65 || density == 0) {
                return false;
            }
            int hash = (x * 73856093) ^ (z * 19349663);
            return Math.floorMod(hash, 100) < density && !(x == 0 && z == 0);
        };
        start = new AStar.Node(0, 64, 0);
        goal = new AStar.Node(distance, 64, distance / 2);
        if (obstacles == 0 && AStar.find(world, start, goal, 4000, 128).isEmpty()) {
            throw new IllegalStateException("an empty floor must have a route");
        }
    }

    @Benchmark
    public List<AStar.Node> findRoute() {
        return AStar.find(world, start, goal, 4000, 128);
    }
}
