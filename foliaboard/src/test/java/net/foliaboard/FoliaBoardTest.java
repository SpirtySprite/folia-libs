package net.foliaboard;

import net.foliaboard.api.Nametag;
import net.foliaboard.api.ScoreObjective;
import net.foliaboard.api.Sidebar;
import net.foliaboard.api.layout.Layout;
import net.foliaboard.internal.packet.PacketAdapter;
import net.foliaboard.internal.scheduler.Schedulers;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FoliaBoardTest {

    private MockedStatic<Bukkit> bukkit;
    private PacketAdapter adapter;
    private Player player;

    @BeforeEach
    void setUp() {
        Schedulers.setSynchronousForTesting(true);
        bukkit = Mockito.mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(Mockito.mock(PluginManager.class));
        adapter = Mockito.mock(PacketAdapter.class);
        player = player("Steve");
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
        Schedulers.setSynchronousForTesting(false);
    }

    private static Plugin plugin(String name) {
        Plugin plugin = Mockito.mock(Plugin.class);
        lenient().when(plugin.isEnabled()).thenReturn(true);
        lenient().when(plugin.getName()).thenReturn(name);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("test-" + name));
        return plugin;
    }

    private static Player player(String name) {
        Player player = Mockito.mock(Player.class);
        lenient().when(player.isOnline()).thenReturn(true);
        lenient().when(player.getName()).thenReturn(name);
        lenient().when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private FoliaBoard board(String pluginName) {
        return FoliaBoard.withAdapter(plugin(pluginName), adapter);
    }

    @Test
    void eachFeatureHasItsOwnService() {
        FoliaBoard board = board("Shop");

        assertNotNull(board.boards());
        assertNotNull(board.tabs());
        assertNotNull(board.bossBars());
        assertNotNull(board.nametags());
        assertNotNull(board.objectives());
        assertSame(board.boards(), board.boards());
    }

    @Test
    void theShortcutsOnTheFacadeUseTheSameStateAsTheServices() {
        FoliaBoard board = board("Shop");

        Sidebar viaFacade = board.sidebar(player);
        Sidebar viaService = board.boards().sidebar(player);

        assertSame(viaFacade, viaService);
        assertSame(viaFacade, board.boards().sidebarIfPresent(player));
    }

    @Test
    void creatingASidebarSendsAnObjectiveNamedForTheOwningPlugin() {
        FoliaBoard board = board("Shop");

        board.boards().sidebar(player).title(Component.text("Hi")).line(0, Component.text("A"));

        ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
        verify(adapter, atLeastOnce()).createObjective(eq(player), id.capture(), any());
        assertTrue(id.getValue().startsWith("fb"));
        assertTrue(id.getValue().length() <= 16);
    }

    @Test
    void twoBoardsFromDifferentPluginsNeverShareAnObjectiveName() {
        FoliaBoard shop = board("Shop");
        FoliaBoard lobby = board("Lobby");
        Player other = player("Alex");

        shop.boards().sidebar(player).title(Component.text("Shop"));
        lobby.boards().sidebar(other).title(Component.text("Lobby"));

        ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
        verify(adapter, atLeastOnce()).createObjective(any(), ids.capture(), any());
        assertEquals(2, ids.getAllValues().stream().distinct().count(),
                "both plugins created their first sidebar, the names must differ");
    }

    @Test
    void layoutsAreLookedUpCaseInsensitively() {
        FoliaBoard board = board("Shop");
        Layout layout = Layout.named("Lobby", builder -> { });

        board.boards().registerLayout(layout);

        assertSame(layout, board.boards().layout("lobby"));
        assertSame(layout, board.boards().layout("LOBBY"));
        board.boards().unregisterLayout("Lobby");
        assertNull(board.boards().layout("lobby"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedFacadeMethodsStillForwardToTheServices() {
        FoliaBoard board = board("Shop");
        Layout layout = Layout.named("Spawn", builder -> { });

        FoliaBoard returned = board.registerLayout(layout);

        assertSame(board, returned, "the old fluent return type is preserved");
        assertSame(layout, board.layout("spawn"));
        assertSame(layout, board.boards().layout("spawn"));
        assertNull(board.sidebarIfPresent(player));
        board.sidebar(player);
        assertNotNull(board.sidebarIfPresent(player));
        board.removeSidebar(player);
        assertNull(board.sidebarIfPresent(player));
    }

    @Test
    void removingASidebarRemovesItsObjective() {
        FoliaBoard board = board("Shop");
        board.boards().sidebar(player).title(Component.text("Hi")).line(0, Component.text("A"));

        board.boards().remove(player);

        verify(adapter, atLeastOnce()).removeObjective(eq(player), anyString());
        assertNull(board.boards().sidebarIfPresent(player));
    }

    @Test
    void quittingRemovesTheSidebarAndTheNametag() {
        FoliaBoard board = board("Shop");
        board.boards().sidebar(player);
        Nametag nametag = board.nametags().get(player);
        assertNotNull(nametag);
        assertEquals(1, board.stats().activeNametags());

        board.lifecycle().onQuit(player);

        assertNull(board.boards().sidebarIfPresent(player));
        assertEquals(0, board.stats().activeNametags());
    }

    @Test
    void belowNameAndTabObjectivesAreCreatedOnceAndDistinct() {
        FoliaBoard board = board("Shop");

        ScoreObjective below = board.objectives().belowName();
        ScoreObjective tab = board.objectives().tabList();

        assertSame(below, board.objectives().belowName());
        assertSame(tab, board.objectives().tabList());
        assertNotSame(below, tab);
    }

    @Test
    void closingTwiceIsSafeAndStopsFurtherUse() {
        FoliaBoard board = board("Shop");
        board.boards().sidebar(player).title(Component.text("Hi")).line(0, Component.text("A"));

        board.close();
        board.close();

        verify(adapter, atLeastOnce()).removeObjective(eq(player), anyString());
        assertThrows(IllegalStateException.class, () -> board.createBoard(player));
        assertThrows(IllegalStateException.class, () -> board.boards().sidebar(player));
        assertThrows(IllegalStateException.class, () -> board.nametags().get(player));
        assertThrows(IllegalStateException.class, () -> board.objectives().belowName());
        assertThrows(IllegalStateException.class, () -> board.tabs().builder(player));
        assertThrows(IllegalStateException.class, () -> board.bossBars().builder(player, "x"));
    }

    @Test
    void joiningAfterCloseDoesNothing() {
        FoliaBoard board = board("Shop");
        board.close();

        board.lifecycle().onJoin(player);

        verify(adapter, never()).createObjective(any(), anyString(), any());
    }

    @Test
    void statsCountActiveSidebars() {
        FoliaBoard board = board("Shop");
        assertEquals(0, board.stats().activeSidebars());

        board.boards().sidebar(player);
        board.boards().sidebar(player("Alex"));

        assertEquals(2, board.stats().activeSidebars());
    }

    @Test
    void placeholdersAndPluginAreExposed() {
        Plugin plugin = plugin("Shop");
        FoliaBoard board = FoliaBoard.withAdapter(plugin, adapter);

        assertSame(plugin, board.plugin());
        assertNotNull(board.placeholders());
        assertNotEquals(board.plugin().getName(), "");
    }
}
