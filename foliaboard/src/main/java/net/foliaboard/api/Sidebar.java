package net.foliaboard.api;

import net.foliaboard.api.format.NumberFormat;
import net.foliaboard.api.hook.LineProcessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface Sidebar {
    /** Applies a complete frame. Library sidebars replace it atomically; custom implementations use sequential setters. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    default @NotNull Sidebar replace(@NotNull SidebarState state) {
        title(state.title());
        clearLines();
        for (int index = 0; index < state.lines().size(); index++) {
            SidebarState.Line row = state.lines().get(index);
            if (row.format().isPresent()) {
                line(index, row.text(), row.format().get());
            } else {
                line(index, row.text());
            }
        }
        return visible(state.visible());
    }

    /** Immutable desired frame. Custom implementations without formatting access return default formats. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    default @NotNull SidebarState snapshot() {
        return new SidebarState(title(), lines().stream().map(SidebarState.Line::new).toList(), visible());
    }

    /** Requests immediate reevaluation of builder-backed content on the player's thread. Safe from any thread. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    default @NotNull Sidebar refresh() {
        return this;
    }

    /** Reevaluates only the specified builder row; -1 selects the title. Safe from any thread. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    default @NotNull Sidebar refreshLine(int index) {
        if (index < -1 || index > 63) {
            throw new IllegalArgumentException("Refresh index must be -1 to 63");
        }
        return refresh();
    }
    @NotNull Player player();

    @NotNull Component title();

    @NotNull List<Component> lines();

    int lineCount();

    @NotNull Sidebar title(@NotNull ComponentLike title);

    @NotNull Sidebar line(int index, @NotNull ComponentLike text);

    @NotNull Sidebar line(int index, @NotNull ComponentLike text, @NotNull NumberFormat numberFormat);

    @NotNull Sidebar lines(@NotNull List<? extends ComponentLike> lines);

    @NotNull Sidebar removeLine(int index);

    @NotNull Sidebar clearLines();

    @NotNull Sidebar lineProcessors(@NotNull List<LineProcessor> processors);

    @NotNull Sidebar visible(boolean visible);

    boolean visible();

    void close();

    boolean closed();
}
