package net.foliabench.gui;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.item.GuiItem;
import org.bukkit.Material;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/** Building menu items. A paginated menu of a few thousand entries builds one per entry. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class GuiItemBenchmark {
    private MockServerState server;

    @Setup(Level.Trial)
    public void setUp() {
        server = new MockServerState();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        server.close();
    }

    @Benchmark
    public GuiItem plainItem() {
        return ItemBuilder.of(Material.DIAMOND).asGuiItem();
    }

    @Benchmark
    public GuiItem namedAndLoreItem() {
        return ItemBuilder.of(Material.DIAMOND).name("&bFancy item").lore("&7First line", "&7Second line").asGuiItem();
    }

    @Benchmark
    public Object namedAndLoreItemWithIdentity() {
        GuiItem item = ItemBuilder.of(Material.DIAMOND).name("&bFancy item").lore("&7First line", "&7Second line")
                .asGuiItem();
        return item.getUuid();
    }
}
