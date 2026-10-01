package net.foliaboard.api;

import net.foliaboard.internal.service.TabService;
import org.jetbrains.annotations.ApiStatus;
import net.foliaboard.internal.tab.TabImpl;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class TabBuilder {
    private final TabService board;
    private final Player player;
    private Function<Player, String> header;
    private Function<Player, String> footer;
    private Function<Player, String> name;
    private ToIntFunction<Player> order;
    private boolean placeholders;
    private int refreshTicks = -1;
    private boolean dynamic;
    private boolean resetOnClose = true;

    @ApiStatus.Internal
    public TabBuilder(@NotNull TabService board, @NotNull Player player) {
        this.board = board;
        this.player = player;
    }

    public synchronized @NotNull TabBuilder header(@NotNull String anyFormat) {
        this.header = viewer -> anyFormat;
        return this;
    }

    public synchronized @NotNull TabBuilder header(@NotNull List<String> lines) {
        return header(String.join("\n", lines));
    }

    public synchronized @NotNull TabBuilder header(@NotNull Function<Player, String> header) {
        this.dynamic = true;
        this.header = header;
        return this;
    }

    public synchronized @NotNull TabBuilder footer(@NotNull String anyFormat) {
        this.footer = viewer -> anyFormat;
        return this;
    }

    public synchronized @NotNull TabBuilder footer(@NotNull List<String> lines) {
        return footer(String.join("\n", lines));
    }

    public synchronized @NotNull TabBuilder footer(@NotNull Function<Player, String> footer) {
        this.dynamic = true;
        this.footer = footer;
        return this;
    }

    public synchronized @NotNull TabBuilder name(@NotNull String anyFormat) {
        this.name = viewer -> anyFormat;
        return this;
    }

    public synchronized @NotNull TabBuilder name(@NotNull Function<Player, String> name) {
        this.dynamic = true;
        this.name = name;
        return this;
    }

    public synchronized @NotNull TabBuilder order(int order) {
        this.order = viewer -> order;
        return this;
    }

    public synchronized @NotNull TabBuilder order(@NotNull ToIntFunction<Player> order) {
        this.dynamic = true;
        this.order = order;
        return this;
    }

    public synchronized @NotNull TabBuilder orderByPermission(@NotNull String... permissionsHighestFirst) {
        String[] copy = permissionsHighestFirst.clone();
        this.dynamic = true;
        this.order = viewer -> {
            for (int index = 0; index < copy.length; index++) {
                if (viewer.hasPermission(copy[index])) {
                    return (copy.length - index) * 100;
                }
            }
            return 0;
        };
        return this;
    }

    public synchronized @NotNull TabBuilder placeholders(boolean enabled) {
        this.placeholders = enabled;
        return this;
    }

    public synchronized @NotNull TabBuilder refreshEvery(int ticks) {
        this.refreshTicks = Math.max(1, ticks);
        return this;
    }

    public synchronized @NotNull TabBuilder resetOnClose(boolean reset) {
        this.resetOnClose = reset;
        return this;
    }

    public synchronized @NotNull TabList build() {
        TabImpl tab = new TabImpl(board.runtime().plugin(), board.runtime().placeholders(), player,
                new TabImpl.Spec(header, footer, name, order, placeholders, refreshTicks > 0 ? refreshTicks : dynamic ? 20 : -1, resetOnClose));
        tab.cleanupPlugin(board.runtime().cleanupPlugin());
        board.track(player, tab);
        tab.start();
        return tab;
    }
}
