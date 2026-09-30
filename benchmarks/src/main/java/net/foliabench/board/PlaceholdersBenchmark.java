package net.foliabench.board;

import net.foliabench.support.Stubs;
import net.foliaboard.api.placeholder.Placeholders;
import org.bukkit.entity.Player;
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

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Resolving %placeholders% in a board line, which happens for every dynamic line on every refresh. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class PlaceholdersBenchmark {
    private final AtomicLong counter = new AtomicLong();
    private Placeholders placeholders;
    private Player player;

    @Setup
    public void setUp() {
        placeholders = new Placeholders();
        placeholders.register("money", p -> "1,250");
        placeholders.register("kills", p -> String.valueOf(counter.incrementAndGet()), Duration.ofMinutes(10));
        player = Stubs.player("Steve");
    }

    @Benchmark
    public String noPlaceholders() {
        return placeholders.resolveForMiniMessage(player, "<gray>Welcome to the server");
    }

    @Benchmark
    public String builtInPlaceholders() {
        return placeholders.resolveForMiniMessage(player, "<gray>%player% Ping: %ping% HP: %health% Lv %level%");
    }

    @Benchmark
    public String customUncachedPlaceholder() {
        return placeholders.resolveForMiniMessage(player, "<gold>Coins: %money%");
    }

    @Benchmark
    public String customCachedPlaceholder() {
        return placeholders.resolveForMiniMessage(player, "<red>Kills: %kills%");
    }

    @Benchmark
    public net.kyori.adventure.text.Component resolveAndParseToComponent() {
        return placeholders.component(player, "<gray>%player% <white>%ping%ms");
    }
}
