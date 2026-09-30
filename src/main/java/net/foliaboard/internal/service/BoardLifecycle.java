package net.foliaboard.internal.service;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Routes player join, world change and quit, and shutdown, to every service in a fixed order. */
public final class BoardLifecycle {
    private final BoardRuntime runtime;
    private final SidebarService sidebars;
    private final TabService tabs;
    private final BossBarService bossBars;
    private final NametagService nametags;
    private final ObjectiveService objectives;

    public BoardLifecycle(@NotNull BoardRuntime runtime, @NotNull SidebarService sidebars, @NotNull TabService tabs,
                          @NotNull BossBarService bossBars, @NotNull NametagService nametags,
                          @NotNull ObjectiveService objectives) {
        this.runtime = runtime;
        this.sidebars = sidebars;
        this.tabs = tabs;
        this.bossBars = bossBars;
        this.nametags = nametags;
        this.objectives = objectives;
    }

    public void onJoin(@NotNull Player player) {
        if (runtime.closed()) {
            return;
        }
        nametags.onJoin(player);
        objectives.onJoin(player);
        sidebars.onJoinProvider(player);
        tabs.onJoin(player);
        sidebars.onJoinLayouts(player);
    }

    public void onWorldChange(@NotNull Player player) {
        if (runtime.closed()) {
            return;
        }
        sidebars.onWorldChange(player);
    }

    public void onQuit(@NotNull Player player) {
        sidebars.onQuit(player);
        tabs.onQuit(player);
        bossBars.onQuit(player);
        runtime.placeholders().forget(player.getUniqueId());
        nametags.onQuit(player);
        objectives.onQuit(player);
    }

    /** Stops everything. Returns false if it had already been closed. */
    public boolean close() {
        if (runtime.closed()) {
            return false;
        }
        runtime.markClosed();
        sidebars.closeAll();
        tabs.closeAll();
        bossBars.closeAll();
        nametags.closeAll();
        objectives.closeAll();
        return true;
    }
}
