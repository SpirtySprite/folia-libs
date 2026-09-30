package net.foliabench.board;

import net.foliabench.support.Stubs;
import net.foliaboard.internal.board.SidebarImpl;
import net.foliaboard.internal.scheduler.Schedulers;
import net.kyori.adventure.text.Component;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * What it costs to update a sidebar, and whether unchanged content really costs nothing on the wire.
 *
 * <p>The packet adapter only counts. The setup checks the counts, so the "unchanged" benchmarks fail
 * loudly if they ever start sending packets, and the "changed" ones fail if they stop.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class SidebarDiffBenchmark {
    @Param({"5", "15"})
    public int lines;

    private CountingAdapter adapter;
    private SidebarImpl sidebar;
    private List<Component> setA;
    private List<Component> setB;
    private boolean flip;

    @Setup(Level.Trial)
    public void setUp() {
        Schedulers.setSynchronousForTesting(true);
        adapter = new CountingAdapter();
        sidebar = new SidebarImpl(Stubs.plugin("Bench"), adapter, Stubs.player("Steve"), "fbbench", List.of());
        setA = new ArrayList<>();
        setB = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            setA.add(Component.text("Line " + i + " value A"));
            setB.add(Component.text("Line " + i + " value B"));
        }
        sidebar.title(Component.text("Bench"));
        sidebar.lines(setA);
        verify();
    }

    private void verify() {
        long before = adapter.packets;
        sidebar.line(0, setA.get(0));
        sidebar.lines(setA);
        if (adapter.packets != before) {
            throw new IllegalStateException("unchanged content sent " + (adapter.packets - before) + " packets");
        }
        sidebar.line(0, setB.get(0));
        if (adapter.packets == before) {
            throw new IllegalStateException("a changed line sent no packet");
        }
        sidebar.lines(setA);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        Schedulers.setSynchronousForTesting(false);
    }

    @Benchmark
    public Object setSameLine() {
        return sidebar.line(0, setA.get(0));
    }

    @Benchmark
    public Object changeOneLine() {
        flip = !flip;
        return sidebar.line(0, flip ? setB.get(0) : setA.get(0));
    }

    @Benchmark
    public Object setSameLines() {
        return sidebar.lines(setA);
    }

    @Benchmark
    public Object changeAllLines() {
        flip = !flip;
        return sidebar.lines(flip ? setB : setA);
    }
}
