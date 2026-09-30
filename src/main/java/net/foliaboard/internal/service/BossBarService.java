package net.foliaboard.internal.service;

import net.foliaboard.api.BossBarBuilder;
import net.foliaboard.api.BossBars;
import net.foliaboard.api.ManagedBossBar;
import net.foliaboard.internal.bossbar.ManagedBossBarImpl;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossBarService implements BossBars {
    private final BoardRuntime runtime;
    private final Map<UUID, Map<String, ManagedBossBarImpl>> bossBars = new ConcurrentHashMap<>();

    public BossBarService(@NotNull BoardRuntime runtime) {
        this.runtime = runtime;
    }

    public @NotNull BoardRuntime runtime() {
        return runtime;
    }

    @Override
    public @NotNull BossBarBuilder builder(@NotNull Player player, @NotNull String id) {
        runtime.ensureOpen();
        return new BossBarBuilder(this, player, id);
    }

    @Override
    public @Nullable ManagedBossBar get(@NotNull Player player, @NotNull String id) {
        Map<String, ManagedBossBarImpl> bars = bossBars.get(player.getUniqueId());
        return bars == null ? null : bars.get(id);
    }

    @Override
    public void hide(@NotNull Player player, @NotNull String id) {
        ManagedBossBar bar = get(player, id);
        if (bar != null) {
            bar.hide();
        }
    }

    public void track(@NotNull Player player, @NotNull String id, @NotNull ManagedBossBarImpl bar) {
        ManagedBossBarImpl previous = bossBars.computeIfAbsent(player.getUniqueId(), key -> new ConcurrentHashMap<>())
                .put(id, bar);
        if (previous != null && previous != bar) {
            previous.hideSilently();
        }
    }

    public void forget(@NotNull Player player, @NotNull String id) {
        bossBars.computeIfPresent(player.getUniqueId(), (key, bars) -> {
            ManagedBossBarImpl current = bars.get(id);
            if (current != null && current.hidden()) {
                bars.remove(id);
            }
            return bars.isEmpty() ? null : bars;
        });
    }

    public void onQuit(@NotNull Player player) {
        hideAll(player.getUniqueId());
    }

    public void closeAll() {
        for (UUID player : List.copyOf(bossBars.keySet())) {
            hideAll(player);
        }
    }

    private void hideAll(UUID player) {
        Map<String, ManagedBossBarImpl> bars = bossBars.remove(player);
        if (bars != null) {
            bars.values().forEach(ManagedBossBarImpl::hideSilently);
        }
    }
}
