package net.foliaboard.api;

import net.foliaboard.api.hook.LineProcessor;
import net.foliaboard.api.layout.Layout;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sidebars, layouts and global sidebars. Obtain it from {@code FoliaBoard#boards()}. */
public interface Boards {

    /** Starts a fluent description of a player's sidebar. */
    @NotNull BoardBuilder create(@NotNull Player player);

    /** The player's sidebar, created empty if they had none. */
    @NotNull Sidebar sidebar(@NotNull Player player);

    @Nullable Sidebar sidebarIfPresent(@NotNull Player player);

    void remove(@NotNull Player player);

    /** Shows {@code layout} to every online player and to everyone who joins later. */
    void setGlobal(@NotNull Layout layout);

    /** Drives every player's sidebar from {@code provider}, refreshing on its own interval. */
    void setGlobal(@NotNull SidebarProvider provider);

    void clearGlobal();

    @NotNull Boards registerLayout(@NotNull Layout layout);

    @Nullable Layout layout(@NotNull String name);

    @NotNull Boards unregisterLayout(@NotNull String name);

    @NotNull Sidebar applyLayout(@NotNull Player player, @NotNull String layoutName);

    @NotNull Sidebar applyLayout(@NotNull Player player, @NotNull Layout layout);

    /** Persists which layout each player last used, so it can be restored when they rejoin. */
    @NotNull Boards layoutStore(@NotNull LayoutStore store);

    @NotNull Boards worldLayout(@NotNull String worldName, @NotNull String layoutName);

    @NotNull Boards clearWorldLayout(@NotNull String worldName);

    /** Adds a processor that runs on every sidebar line of every board. */
    @NotNull Boards addLineProcessor(@NotNull LineProcessor processor);

    @NotNull Boards removeLineProcessor(@NotNull LineProcessor processor);
}
