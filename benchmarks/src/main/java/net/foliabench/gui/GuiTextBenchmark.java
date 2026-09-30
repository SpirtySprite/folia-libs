package net.foliabench.gui;

import com.foliagui.util.Text;
import net.kyori.adventure.text.Component;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class GuiTextBenchmark {
    private final Map<String, String> values = Map.of("player", "Steve", "coins", "1,250");

    @Benchmark
    public Component legacyTitle() {
        return Text.of("&8Main &bMenu");
    }

    @Benchmark
    public Component miniGradient() {
        return Text.mini("<gradient:#00c6ff:#0072ff><bold>Shop</bold></gradient>");
    }

    @Benchmark
    public Component templateWithValues() {
        return Text.of("&7Hello &f{player}&7, you have &6{coins}", values);
    }
}
