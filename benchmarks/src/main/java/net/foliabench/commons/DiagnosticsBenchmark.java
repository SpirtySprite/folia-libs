package net.foliabench.commons;

import net.foliacommons.diagnostics.Diagnostics;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/** Building and printing a diagnostics report. Run once per server start, so this only has to be not slow. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class DiagnosticsBenchmark {
    @Param({"10", "50"})
    public int features;

    @Benchmark
    public String buildAndPrint() {
        Diagnostics.Builder builder = Diagnostics.named("Bench 1.0").section("Features");
        for (int i = 0; i < features; i++) {
            if (i % 5 == 0) {
                builder.degraded("Feature " + i, "reduced");
            } else {
                builder.ok("Feature " + i);
            }
        }
        return builder.build().toString();
    }
}
