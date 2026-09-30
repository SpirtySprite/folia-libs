package net.foliabench.commons;

import net.foliacommons.version.ServerVersion;
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class ServerVersionBenchmark {
    private final ServerVersion version = ServerVersion.parse("1.21.4");

    @Benchmark
    public ServerVersion parse() {
        return ServerVersion.parse("1.21.4-R0.1-SNAPSHOT");
    }

    @Benchmark
    public boolean isAtLeast() {
        return version.isAtLeast(20, 6);
    }
}
