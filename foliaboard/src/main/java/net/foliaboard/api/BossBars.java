package net.foliaboard.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Boss bars, identified per player by a string id. Obtain it from {@code FoliaBoard#bossBars()}. */
public interface BossBars {

    @NotNull BossBarBuilder builder(@NotNull Player player, @NotNull String id);

    @Nullable ManagedBossBar get(@NotNull Player player, @NotNull String id);

    void hide(@NotNull Player player, @NotNull String id);
}
