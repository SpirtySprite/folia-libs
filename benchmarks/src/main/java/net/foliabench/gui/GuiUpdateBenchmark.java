package net.foliabench.gui;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.Gui;
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

/**
 * Redrawing a full six-row menu. The interesting number is how little an update costs when almost
 * nothing changed, because menus that auto-refresh do that every few ticks.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class GuiUpdateBenchmark {
    private static final int SLOTS = 54;

    private MockServerState server;
    private Gui gui;
    private GuiItem[] setA;
    private GuiItem[] setB;
    private boolean flip;

    @Setup(Level.Trial)
    public void setUp() {
        server = new MockServerState();
        gui = Gui.builder().service(server.service).rows(6).title("&8Bench").create();
        setA = new GuiItem[SLOTS];
        setB = new GuiItem[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            setA[i] = ItemBuilder.of(Material.STONE).name("&7A " + i).asGuiItem();
            setB[i] = ItemBuilder.of(Material.DIRT).name("&7B " + i).asGuiItem();
            gui.setItem(i, setA[i]);
        }
        gui.update();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        server.close();
    }

    @Benchmark
    public void updateNothingChanged() {
        gui.update();
    }

    @Benchmark
    public void replaceOneSlotAndUpdate() {
        flip = !flip;
        gui.setItem(10, flip ? setB[10] : setA[10]);
        gui.update();
    }

    @Benchmark
    public void replaceAllSlotsAndUpdate() {
        flip = !flip;
        GuiItem[] items = flip ? setB : setA;
        for (int i = 0; i < SLOTS; i++) {
            gui.setItem(i, items[i]);
        }
        gui.update();
    }
}
