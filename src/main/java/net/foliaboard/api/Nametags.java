package net.foliaboard.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Prefixes, suffixes and colours above players' heads. Obtain it from {@code FoliaBoard#nametags()}. */
public interface Nametags {

    @NotNull NametagBuilder builder(@NotNull Player target);

    /** The player's nametag, created if they had none. */
    @NotNull Nametag get(@NotNull Player target);

    @Nullable Nametag getIfPresent(@NotNull Player target);
}
