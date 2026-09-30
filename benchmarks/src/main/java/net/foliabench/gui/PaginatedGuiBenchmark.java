package net.foliabench.gui;

import com.foliagui.builder.item.ItemBuilder;
import com.foliagui.gui.PaginatedGui;
import com.foliagui.item.GuiItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
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

import java.util.concurrent.TimeUnit;

/** Turning pages in a menu with many entries. Cost should depend on the page size, not the entry count. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class PaginatedGuiBenchmark {
    @Param({"100", "1000", "10000"})
    public int entries;

    private MockServerState server;
    private PaginatedGui gui;
    private int pages;
    private int page;

    @Setup(Level.Trial)
    public void setUp() {
        server = new MockServerState();
        GuiItem[] items = new GuiItem[entries];
        for (int i = 0; i < entries; i++) {
            items[i] = ItemBuilder.of(Material.PAPER).name("&7Entry " + i).asGuiItem();
        }
        gui = new PaginatedGui(6, Component.text("Bench"), 0);
        gui.service(server.service);
        gui.pageControls(true);
        gui.setPageItemSupplier(entries, index -> items[index]);
        pages = gui.getPagesCount();
        if (pages < 2) {
            throw new IllegalStateException("expected several pages for " + entries + " entries");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        server.close();
    }

    @Benchmark
    public Object jumpToNextPage() {
        page = page % pages + 1;
        return gui.openPage(page);
    }

    @Benchmark
    public boolean stepForwardOrWrap() {
        if (!gui.next()) {
            gui.openPage(1);
            return false;
        }
        return true;
    }
}
