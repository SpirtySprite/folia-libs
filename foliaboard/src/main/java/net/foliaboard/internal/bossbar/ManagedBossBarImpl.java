package net.foliaboard.internal.bossbar;

import net.foliaboard.api.ManagedBossBar;
import net.foliaboard.api.placeholder.Placeholders;
import net.foliaboard.api.text.Text;
import net.foliaboard.internal.scheduler.Schedulers;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

public final class ManagedBossBarImpl implements ManagedBossBar {
    private static final int DEFAULT_PLACEHOLDER_REFRESH = 20;

    public record Spec(Function<Player, String> text, ToDoubleFunction<Player> progress,
                       Function<Player, BossBar.Color> color, BossBar.Overlay overlay,
                       boolean placeholders, int refreshTicks, long lifetimeTicks) {
    }

    private final Plugin plugin;
    private Plugin cleanupPlugin;
    private net.foliaboard.internal.metrics.PacketMetrics metrics = new net.foliaboard.internal.metrics.PacketMetrics();
    private final Placeholders placeholders;
    private final Player player;
    private final String id;
    private final Spec spec;
    private final BiConsumer<Player, String> onHide;
    private final BossBar bar;
    private volatile boolean hidden;
    private volatile Schedulers.ScheduledHandle timer;
    private String sentText;
    private volatile net.foliacommons.scheduler.TaskHandle expiry;

    public ManagedBossBarImpl(Plugin plugin, Placeholders placeholders, Player player, String id, Spec spec,
                              BiConsumer<Player, String> onHide) {
        this.plugin = plugin;
        this.cleanupPlugin = plugin;
        this.placeholders = placeholders;
        this.player = player;
        this.id = id;
        this.spec = spec;
        this.onHide = onHide;
        this.bar = BossBar.bossBar(Component.empty(), 1.0F, BossBar.Color.PURPLE, spec.overlay());
    }

    public void start() {
        Schedulers.onEntity(plugin, player, () -> {
            update();
            if (!hidden) {
                player.showBossBar(bar);
            metrics.changed(net.foliaboard.api.PresentationStats.Surface.BOSS_BAR);
            }
        });
        int interval = spec.refreshTicks() > 0 ? spec.refreshTicks()
                : spec.placeholders() ? DEFAULT_PLACEHOLDER_REFRESH : -1;
        if (interval > 0) {
            timer = Schedulers.entityTimer(plugin, player, handle -> {
                if (hidden || !player.isOnline()) {
                    handle.cancel();
                    return;
                }
                update();
            }, interval, interval);
        }
        if (spec.lifetimeTicks() > 0) {
            expiry = Schedulers.entityLater(plugin, player, this::hide, spec.lifetimeTicks());
            if (hidden) {
                expiry.cancel();
            }
        }
    }

    public synchronized void update() {
        metrics.requested(net.foliaboard.api.PresentationStats.Surface.BOSS_BAR);
        if (hidden) {
            return;
        }
        String raw = safely(() -> spec.text().apply(player), sentText);
        String text = raw == null ? "" : spec.placeholders() ? placeholders.resolveForMiniMessage(player, raw) : raw;
        if (!text.equals(sentText)) {
            bar.name(Text.cached(text));
            metrics.changed(net.foliaboard.api.PresentationStats.Surface.BOSS_BAR);
            sentText = text;
        }
        float progress = (float) clamp(safely(() -> spec.progress().applyAsDouble(player), (double) bar.progress()));
        if (bar.progress() != progress) {
            bar.progress(progress);
            metrics.changed(net.foliaboard.api.PresentationStats.Surface.BOSS_BAR);
        }
        BossBar.Color color = safely(() -> spec.color().apply(player), bar.color());
        if (color != null && bar.color() != color) {
            bar.color(color);
            metrics.changed(net.foliaboard.api.PresentationStats.Surface.BOSS_BAR);
        }
    }

    private <T> T safely(java.util.function.Supplier<T> render, T previous) {
        try {
            return render.get();
        } catch (RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Boss-bar renderer failed; preserving its previous value", failure);
            return previous;
        }
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    public void metrics(net.foliaboard.internal.metrics.PacketMetrics metrics) {
        this.metrics = metrics;
    }

    public void cleanupPlugin(Plugin cleanupPlugin) {
        this.cleanupPlugin = java.util.Objects.requireNonNull(cleanupPlugin, "cleanupPlugin");
    }

    @Override
    public @NotNull String id() {
        return id;
    }

    @Override
    public @NotNull Player player() {
        return player;
    }

    @Override
    public @NotNull BossBar bar() {
        return bar;
    }

    @Override
    public @NotNull ManagedBossBar refresh() {
        Schedulers.onEntity(plugin, player, this::update);
        return this;
    }

    @Override
    public synchronized void hide() {
        metrics.requested(net.foliaboard.api.PresentationStats.Surface.BOSS_BAR);
        if (hidden) {
            return;
        }
        hidden = true;
        net.foliacommons.scheduler.TaskHandle expiration = expiry;
        if (expiration != null) {
            expiration.cancel();
        }
        Schedulers.ScheduledHandle running = timer;
        timer = null;
        if (running != null) {
            running.cancel();
        }
        Schedulers.onEntity(cleanupPlugin, player, () -> {
            player.hideBossBar(bar);
            metrics.changed(net.foliaboard.api.PresentationStats.Surface.BOSS_BAR);
        });
        onHide.accept(player, id);
    }

    public synchronized void hideSilently() {
        metrics.requested(net.foliaboard.api.PresentationStats.Surface.BOSS_BAR);
        if (hidden) {
            return;
        }
        hidden = true;
        net.foliacommons.scheduler.TaskHandle expiration = expiry;
        if (expiration != null) {
            expiration.cancel();
        }
        Schedulers.ScheduledHandle running = timer;
        timer = null;
        if (running != null) {
            running.cancel();
        }
        Schedulers.onEntity(cleanupPlugin, player, () -> {
            player.hideBossBar(bar);
            metrics.changed(net.foliaboard.api.PresentationStats.Surface.BOSS_BAR);
        });
    }

    @Override
    public boolean hidden() {
        return hidden;
    }
}
