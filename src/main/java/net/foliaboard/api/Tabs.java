package net.foliaboard.api;

import net.kyori.adventure.text.ComponentLike;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The tab list: header and footer, entry names and ordering. Obtain it from {@code FoliaBoard#tabs()}. */
public interface Tabs {

    @NotNull TabBuilder builder(@NotNull Player player);

    @Nullable TabList get(@NotNull Player player);

    void remove(@NotNull Player player);

    /** Applies {@code layout} to every online player and to everyone who joins later. */
    void setGlobal(@NotNull TabLayout layout);

    void clearGlobal();

    void name(@NotNull Player target, @NotNull String miniMessage);

    void name(@NotNull Player target, @NotNull ComponentLike name);

    void resetName(@NotNull Player target);

    void order(@NotNull Player target, int order);

    boolean orderSupported();

    /** Shows {@code target} to one {@code viewer} under a different name. */
    void nameFor(@NotNull Player viewer, @NotNull Player target, @NotNull String miniMessage);

    void nameFor(@NotNull Player viewer, @NotNull Player target, @NotNull ComponentLike name);

    void resetNameFor(@NotNull Player viewer, @NotNull Player target);

    boolean perViewerSupported();

    void headerFooter(@NotNull Player player, @NotNull String header, @NotNull String footer);

    void headerFooter(@NotNull Player player, @NotNull ComponentLike header, @NotNull ComponentLike footer);

    void clearHeaderFooter(@NotNull Player player);
}
