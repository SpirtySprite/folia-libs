package net.foliabench.board;

import net.foliaboard.api.text.Text;
import net.kyori.adventure.text.Component;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/** Turning strings into components, and what the parse cache saves. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class TextBenchmark {
    private static final String SIMPLE = "<gray>Online: <green>128";
    private static final String GRADIENT = "<gradient:#00c6ff:#0072ff><bold>MY SERVER</bold></gradient>";
    private static final String LEGACY = "&7Online: &a128";

    private int unique;

    @Setup
    public void warm() {
        Text.cached(SIMPLE);
    }

    @Benchmark
    public Component miniSimple() {
        return Text.mini(SIMPLE);
    }

    @Benchmark
    public Component miniGradient() {
        return Text.mini(GRADIENT);
    }

    @Benchmark
    public Component parseLegacy() {
        return Text.parse(LEGACY);
    }

    @Benchmark
    public Component cachedHit() {
        return Text.cached(SIMPLE);
    }

    /** Every call is a string the cache has not seen, so this is parse cost plus the cache bookkeeping. */
    @Benchmark
    public Component cachedMiss() {
        return Text.cached("<gray>Online: <green>" + (unique++));
    }
}
