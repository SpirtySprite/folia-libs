package net.foliabench.commons;

import net.foliacommons.text.Legacy;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/** Converting legacy colour codes to MiniMessage, which every board line, GUI title and nametag goes through. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class LegacyBenchmark {
    private final String plain = "Welcome to the server, enjoy your stay";
    private final String codes = "&aWelcome &lto &7the &fserver &cenjoy";
    private final String hex = "&#ff00aaWelcome &#00ffbbto the &#0000ffserver";

    @Benchmark
    public String toMiniPlain() {
        return Legacy.toMini(plain);
    }

    @Benchmark
    public String toMiniColourCodes() {
        return Legacy.toMini(codes);
    }

    @Benchmark
    public String toMiniHex() {
        return Legacy.toMini(hex);
    }

    @Benchmark
    public String stripColourCodes() {
        return Legacy.strip(codes);
    }
}
