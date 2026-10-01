package com.foliagui;

import com.foliagui.gui.GuiNavigation;
import com.foliagui.gui.GuiRegistry;
import com.foliagui.gui.GuiSessions;
import com.foliagui.gui.GuiTheme;
import com.foliagui.scheduler.Scheduler;
import net.foliacommons.diagnostics.Diagnostics;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * One independent FoliaGUI instance, owned by one plugin.
 *
 * <p>Everything FoliaGUI remembers at runtime (open GUIs, navigation history, anvil, sign, merchant
 * and chat sessions, the theme, the event listener) lives in a service. Create one with
 * {@link FoliaGUI#create(Plugin)} and hand it to the GUIs you build, or call
 * {@link FoliaGUI#init(Plugin)} once and use the static helpers, which work on a default service.
 */
public interface FoliaGUIService {

    @NotNull Plugin plugin();

    @NotNull Scheduler scheduler();

    @NotNull NamespacedKey itemKey();

    @NotNull GuiTheme theme();

    void theme(@NotNull GuiTheme replacement);

    /** Resolves presentation on the player thread. The built-in service returns its default theme when called off-thread. */
    @ApiStatus.Experimental
    default @NotNull GuiTheme theme(@NotNull org.bukkit.entity.Player player) {
        return theme();
    }

    /** Installs a per-player theme resolver evaluated during player-owned rendering. */
    @ApiStatus.Experimental
    default void themeResolver(@NotNull java.util.function.Function<org.bukkit.entity.Player, GuiTheme> resolver) {
        throw new UnsupportedOperationException("Player themes are not supported by this service");
    }

    /** The GUIs this service currently has open, per player. */
    @NotNull GuiRegistry guis();

    /** Back-navigation history for this service's GUIs. */
    @NotNull GuiNavigation navigation();

    @ApiStatus.Internal
    @NotNull GuiSessions sessions();

    /** What this service is doing right now. */
    @NotNull FoliaGUIStats stats();

    /** A report of the server and which features work here. Log it or paste it into a bug report. */
    @NotNull Diagnostics diagnose();

    /**
     * Closes every open GUI, forgets all state, and unregisters the listener. Safe to call more than
     * once. GUIs bound to a closed service should not be opened again.
     */
    void close();

    boolean isClosed();
}
