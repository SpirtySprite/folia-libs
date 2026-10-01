package net.foliaboard.api;

import net.foliaboard.internal.service.BossBarService;
import org.jetbrains.annotations.ApiStatus;
import net.foliaboard.internal.bossbar.ManagedBossBarImpl;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.ToDoubleFunction;

public final class BossBarBuilder {
    private final BossBarService board;
    private final Player player;
    private final String id;
    private Function<Player, String> text = viewer -> "";
    private ToDoubleFunction<Player> progress = viewer -> 1.0D;
    private Function<Player, BossBar.Color> color = viewer -> BossBar.Color.PURPLE;
    private BossBar.Overlay overlay = BossBar.Overlay.PROGRESS;
    private boolean placeholders;
    private int refreshTicks = -1;
    private boolean dynamic;
    private long lifetimeTicks = -1L;

    @ApiStatus.Internal
    public BossBarBuilder(@NotNull BossBarService board, @NotNull Player player, @NotNull String id) {
        this.board = board;
        this.player = player;
        this.id = id;
    }

    public synchronized @NotNull BossBarBuilder text(@NotNull String anyFormat) {
        this.text = viewer -> anyFormat;
        return this;
    }

    public synchronized @NotNull BossBarBuilder text(@NotNull Function<Player, String> text) {
        this.dynamic = true;
        this.text = text;
        return this;
    }

    public synchronized @NotNull BossBarBuilder progress(double progress) {
        double clamped = clamp(progress);
        this.progress = viewer -> clamped;
        return this;
    }

    public synchronized @NotNull BossBarBuilder progress(@NotNull ToDoubleFunction<Player> progress) {
        this.dynamic = true;
        this.progress = progress;
        return this;
    }

    public synchronized @NotNull BossBarBuilder color(@NotNull BossBar.Color color) {
        this.color = viewer -> color;
        return this;
    }

    public synchronized @NotNull BossBarBuilder color(@NotNull Function<Player, BossBar.Color> color) {
        this.dynamic = true;
        this.color = color;
        return this;
    }

    public synchronized @NotNull BossBarBuilder overlay(@NotNull BossBar.Overlay overlay) {
        this.overlay = overlay;
        return this;
    }

    public synchronized @NotNull BossBarBuilder placeholders(boolean enabled) {
        this.placeholders = enabled;
        return this;
    }

    public synchronized @NotNull BossBarBuilder refreshEvery(int ticks) {
        this.refreshTicks = Math.max(1, ticks);
        return this;
    }

    public synchronized @NotNull BossBarBuilder hideAfter(long ticks) {
        this.lifetimeTicks = Math.max(1L, ticks);
        return this;
    }

    public synchronized @NotNull ManagedBossBar show() {
        ManagedBossBarImpl bar = new ManagedBossBarImpl(board.runtime().plugin(), board.runtime().placeholders(), player, id,
                new ManagedBossBarImpl.Spec(text, progress, color, overlay, placeholders, refreshTicks > 0 ? refreshTicks : dynamic ? 20 : -1, lifetimeTicks),
                board::forget);
        bar.cleanupPlugin(board.runtime().cleanupPlugin());
        board.track(player, id, bar);
        bar.start();
        return bar;
    }

    static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
