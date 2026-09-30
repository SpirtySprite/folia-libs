package net.foliabench.npc;

import net.folianpc.internal.NpcManager;
import net.folianpc.internal.PlayerTracker;
import net.folianpc.internal.Position;
import net.folianpc.internal.scheduler.Schedulers;
import net.foliabench.support.Settings;
import net.foliabench.support.Stubs;
import org.bukkit.entity.Player;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

/**
 * One visibility pass over every NPC, which the library runs every two game ticks (ten times a second).
 *
 * <p>A server tick is 50 ms, and the pass runs on one thread, so a few milliseconds here is the budget
 * the README's claim about large NPC counts has to fit into. Two shapes matter:
 * <ul>
 *   <li>{@code tickSteadyState}: players stand still, nothing changes. This is the common case.</li>
 *   <li>{@code tickPlayersMoving}: a tenth of the players move a few blocks before each pass, so NPCs
 *       keep coming into and leaving view.</li>
 * </ul>
 * {@code worldEdge} controls density: a 4000 block world is sparse (few NPCs near any player), a 400
 * block world is dense (every player sees many NPCs). All positions come from a fixed seed.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class NpcTickBenchmark {
    @Param({"100", "1000", "10000"})
    public int npcs;

    @Param({"10", "100", "500"})
    public int players;

    @Param({"4000", "400"})
    public int worldEdge;

    private NpcManager manager;
    private PlayerTracker tracker;
    private CountingBackend backend;
    private Player[] online;
    private double[] x;
    private double[] z;
    private SplittableRandom random;
    private int cursor;

    @Setup(Level.Trial)
    public void setUp() {
        Schedulers.setSynchronousForTesting(true);
        random = new SplittableRandom(Settings.SEED);
        backend = new CountingBackend();
        tracker = new PlayerTracker();
        manager = new NpcManager(Stubs.plugin("Bench"), backend, tracker);
        manager.events(event -> {
        });

        for (int i = 0; i < npcs; i++) {
            manager.create("npc" + i, new Position("world", random.nextDouble(worldEdge),
                    64, random.nextDouble(worldEdge), 0, 0));
        }
        online = new Player[players];
        x = new double[players];
        z = new double[players];
        for (int i = 0; i < players; i++) {
            online[i] = Stubs.player("player" + i);
            x[i] = random.nextDouble(worldEdge);
            z[i] = random.nextDouble(worldEdge);
            track(i);
        }
        manager.tick();
        if (backend.shows == 0 && worldEdge <= 400) {
            throw new IllegalStateException("a dense world should have shown some NPCs");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        Schedulers.setSynchronousForTesting(false);
    }

    private void track(int index) {
        tracker.put(new PlayerTracker.Tracked(online[index], "world", x[index], 64, z[index]));
    }

    @Benchmark
    public void tickSteadyState() {
        manager.tick();
    }

    @Benchmark
    public void tickPlayersMoving() {
        int movers = Math.max(1, players / 10);
        for (int i = 0; i < movers; i++) {
            int index = cursor++ % players;
            x[index] = clamp(x[index] + random.nextDouble(-8, 8));
            z[index] = clamp(z[index] + random.nextDouble(-8, 8));
            track(index);
        }
        manager.tick();
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(worldEdge, value));
    }
}
