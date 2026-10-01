package net.foliaboard;

import net.foliaboard.api.BoardBuilder;
import net.foliaboard.api.LayoutStore;
import net.foliaboard.api.ManagedBossBar;
import net.foliaboard.api.ScoreObjective;
import net.foliaboard.api.Sidebar;
import net.foliaboard.api.SidebarState;
import net.foliaboard.api.PresentationStats;
import net.foliaboard.internal.listener.FoliaBoardListener;
import org.bukkit.event.server.PluginDisableEvent;
import net.foliaboard.api.animation.Animation;
import net.foliaboard.api.layout.Layout;
import net.foliaboard.api.layout.LayoutScope;
import net.foliaboard.api.layout.LayoutSection;
import net.foliaboard.api.layout.SidebarRotation;
import net.foliaboard.api.format.NumberFormat;
import net.foliaboard.internal.packet.PacketAdapter;
import net.foliaboard.internal.scheduler.Schedulers;
import net.foliacommons.scheduler.DeterministicScheduler;
import net.foliacommons.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;

class PresentationTest {
    private MockedStatic<Bukkit> bukkit;
    private DeterministicScheduler scheduler;
    private FoliaBoard board;
    private Player player;
    private World world;
    private PacketAdapter adapter;

    @Test
    void unchangedRefreshesIncreaseRequestsWithoutApplyingMoreOperations() {
        Sidebar sidebar = board.createBoard(player).title("Metrics").line("Stable").build();
        scheduler.advanceTicks(3);
        PresentationStats before = board.presentationStats();
        sidebar.refresh();
        scheduler.advanceTicks(3);
        PresentationStats after = board.presentationStats();
        assertTrue(after.surface(PresentationStats.Surface.SIDEBAR).requests()
                > before.surface(PresentationStats.Surface.SIDEBAR).requests());
        assertEquals(before.surface(PresentationStats.Surface.SIDEBAR).changedOperations(),
                after.surface(PresentationStats.Surface.SIDEBAR).changedOperations());
        assertThrows(UnsupportedOperationException.class, () -> before.surfaces().clear());
        board.objectives().belowName().hide();
        scheduler.advanceTicks(2);
        long hiddenChanges = board.presentationStats().surface(PresentationStats.Surface.OBJECTIVE).changedOperations();
        board.objectives().belowName().score("Entry", 5);
        scheduler.advanceTicks(2);
        assertEquals(hiddenChanges, board.presentationStats().surface(PresentationStats.Surface.OBJECTIVE).changedOperations());
    }

    @Test
    void anEnabledHostDispatchesCleanupAfterOwnerDisable() throws Exception {
        Plugin owner = board.plugin();
        Plugin host = mock(Plugin.class);
        when(host.isEnabled()).thenReturn(true);
        var constructor = FoliaBoard.class.getDeclaredConstructor(Plugin.class, Plugin.class, PacketAdapter.class);
        constructor.setAccessible(true);
        board = constructor.newInstance(owner, host, adapter);
        board.createBoard(player).title("Cleanup").line("Row").build();
        board.nametag(player).apply();
        board.objectives().belowName().score("Entry", 1);
        board.tab(player).header("Header").name("Name").order(5).build();
        board.bossBar(player, "cleanup").text("Boss").show();
        scheduler.advanceTicks(4);
        when(owner.isEnabled()).thenReturn(false);
        clearInvocations(adapter, player);
        try (var dispatch = mockStatic(Schedulers.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            new FoliaBoardListener(board.lifecycle(), owner, board::close).onDisable(new PluginDisableEvent(owner));
            dispatch.verify(() -> Schedulers.onEntity(eq(host), eq(player), any(Runnable.class)), org.mockito.Mockito.atLeast(5));
        }
        scheduler.advanceTicks(3);
        verify(adapter, atLeastOnce()).removeObjective(eq(player), any());
        verify(adapter, atLeastOnce()).removeTeam(eq(player), any());
        verify(player).hideBossBar(any());
        verify(player).sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        verify(player).playerListName(null);
        assertEquals(0, board.stats().activeSidebars());
    }

    @BeforeEach
    void setup() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getName()).thenReturn("Steve");
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(player.getWorld()).thenReturn(world);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("Presentation");
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("presentation"));
        adapter = mock(PacketAdapter.class);
        board = FoliaBoard.withAdapter(plugin, adapter);
        scheduler = Scheduler.deterministic();
        Schedulers.setSchedulerForTesting(scheduler);
    }

    @AfterEach
    void cleanup() {
        Schedulers.setSchedulerForTesting(null);
        scheduler.close();
        bukkit.close();
    }

    @Test
    void sharedScoresPreserveViewerOverridesAndHiddenMutationsStayHidden() {
        ScoreObjective objective = board.objectives().belowName();
        objective.score("Steve", 1).scoreFor(player, "Steve", 33).score("Steve", 9);
        scheduler.advanceTicks(1);
        verify(adapter, atLeastOnce()).setScore(eq(player), any(), eq("Steve"), eq(33), isNull(), isNull());
        verify(adapter, never()).setScore(eq(player), any(), eq("Steve"), eq(9), isNull(), isNull());
        objective.hide();
        scheduler.advanceTicks(1);
        clearInvocations(adapter);
        objective.title(Component.text("Hidden")).score("Steve", 12).removeFor(player, "Steve");
        scheduler.advanceTicks(2);
        verify(adapter, never()).createObjective(any(), any(), any());
        objective.show();
        scheduler.advanceTicks(1);
        verify(adapter).createObjective(eq(player), any(), eq(Component.text("Hidden")));
        verify(adapter).setScore(eq(player), any(), eq("Steve"), eq(12), isNull(), isNull());
        objective.hide();
        objective.score("Steve", 18);
        objective.show();
        scheduler.advanceTicks(1);
        verify(adapter, atLeastOnce()).setScore(eq(player), any(), eq("Steve"), eq(18), isNull(), isNull());
    }

    @Test
    void buildersFreezeQueuedFramesAndReplacementPreservesFormatsAtomically() {
        BoardBuilder recipe = board.createBoard(player).title("Original").line("First", NumberFormat.fixed(Component.text("x")));
        Sidebar sidebar = recipe.build();
        recipe.title("Changed").line(0, "Second");
        scheduler.advanceTicks(3);
        assertEquals(Component.text("Original"), sidebar.title());
        assertEquals(List.of(Component.text("First")), sidebar.lines());
        assertTrue(sidebar.snapshot().lines().getFirst().format().isPresent());
        SidebarState state = new SidebarState(Component.text("Atomic"),
                List.of(new SidebarState.Line(Component.text("Row"), Optional.of(NumberFormat.blank()))), false);
        sidebar.replace(state);
        assertEquals(state, sidebar.snapshot());
        assertThrows(UnsupportedOperationException.class, () -> state.lines().clear());
        assertThrows(IllegalArgumentException.class, () -> recipe.line(Integer.MAX_VALUE, "bad"));
        assertThrows(IllegalArgumentException.class, () -> recipe.line(-1, Component.empty()));
        assertThrows(IllegalArgumentException.class, () -> recipe.line(64, (Function<Player, String>) p -> "bad"));
        assertThrows(IllegalArgumentException.class, () -> recipe.line(64, (Animation<Component>) Component::empty));
        assertThrows(IllegalArgumentException.class, () -> sidebar.refreshLine(64));
    }

    @Test
    void animatedTitlesDoNotReevaluateExpensiveRowsAtTheirCadence() {
        AtomicInteger titles = new AtomicInteger();
        AtomicInteger balances = new AtomicInteger();
        Sidebar sidebar = board.createBoard(player)
                .title((Animation<Component>) () -> Component.text("Title " + titles.incrementAndGet()))
                .line(p -> "Balance " + balances.incrementAndGet()).lineRefreshEvery(0, 20).build();
        scheduler.advanceTicks(60);
        assertTrue(titles.get() >= 19);
        assertTrue(balances.get() <= 4);
        int titleBefore = titles.get();
        int balanceBefore = balances.get();
        sidebar.refreshLine(0);
        scheduler.advanceTicks(1);
        assertEquals(titleBefore, titles.get());
        assertEquals(balanceBefore + 1, balances.get());
        sidebar.refresh();
        scheduler.advanceTicks(1);
        assertTrue(titles.get() > titleBefore);
    }

    @Test
    void failedRowsAndProcessorsPreservePreviousValuesWithoutStoppingOtherRows() {
        AtomicInteger calls = new AtomicInteger();
        Sidebar sidebar = board.createBoard(player).title("Good")
                .line(p -> { if (calls.incrementAndGet() > 1) { throw new IllegalStateException("row"); } return "Last good"; })
                .line("Still good").processor((p, row, text) -> { throw new IllegalArgumentException("processor"); }).build();
        scheduler.advanceTicks(3);
        sidebar.refresh();
        scheduler.advanceTicks(3);
        assertEquals(List.of(Component.text("Last good"), Component.text("Still good")), sidebar.lines());
        verify(adapter, atLeastOnce()).createObjective(eq(player), any(), eq(Component.text("Good")));
    }

    @Test
    void clearingGlobalStopsFutureJoinsAndWorldLayoutsYieldToManualSelection() {
        Layout global = Layout.named("global", builder -> builder.title("Global"));
        Layout contextual = Layout.named("context", builder -> builder.title("World"));
        board.boards().setGlobal(global);
        scheduler.advanceTicks(4);
        assertEquals(Component.text("Global"), board.sidebar(player).title());
        board.boards().clearGlobal();
        board.boards().remove(player);
        board.lifecycle().onJoin(player);
        scheduler.advanceTicks(4);
        assertEquals(null, board.boards().sidebarIfPresent(player));
        board.boards().registerLayout(contextual).worldLayout("world", "context");
        board.lifecycle().onWorldChange(player);
        scheduler.advanceTicks(4);
        assertEquals(Component.text("World"), board.sidebar(player).title());
        board.createBoard(player).title("Manual").build();
        board.lifecycle().onWorldChange(player);
        scheduler.advanceTicks(4);
        assertEquals(Component.text("Manual"), board.sidebar(player).title());
    }

    @Test
    void scopesNestRestoreDynamicRecipesAndCannotOverwriteManualSelections() {
        AtomicInteger count = new AtomicInteger();
        Sidebar sidebar = board.createBoard(player).title(p -> "Base " + count.incrementAndGet()).build();
        scheduler.advanceTicks(3);
        LayoutScope outer = board.boards().temporaryLayout(player, Layout.named("outer", b -> b.title("Outer")));
        scheduler.advanceTicks(3);
        LayoutScope inner = board.boards().temporaryLayout(player, Layout.named("inner", b -> b.title("Inner")));
        scheduler.advanceTicks(3);
        assertEquals(Component.text("Inner"), sidebar.title());
        outer.close();
        scheduler.advanceTicks(2);
        assertEquals(Component.text("Inner"), sidebar.title());
        inner.close();
        scheduler.advanceTicks(4);
        assertTrue(count.get() >= 2);
        LayoutScope timed = board.boards().temporaryLayout(player, Layout.named("timed", b -> b.title("Temporary")), 4);
        scheduler.advanceTicks(3);
        board.createBoard(player).title("New manual").build();
        scheduler.advanceTicks(10);
        assertTrue(timed.isCancelled());
        assertEquals(Component.text("New manual"), sidebar.title());
    }

    @Test
    void rememberedResultsAreDiscardedAfterANewerSelectionOrWorldChange() {
        CompletableFuture<String> remembered = new CompletableFuture<>();
        LayoutStore store = mock(LayoutStore.class);
        when(store.lastLayout(player.getUniqueId())).thenReturn(remembered);
        board.boards().registerLayout(Layout.named("old", b -> b.title("Old"))).layoutStore(store);
        board.lifecycle().onJoin(player);
        scheduler.advanceTicks(2);
        board.createBoard(player).title("New").build();
        remembered.complete("old");
        scheduler.advanceTicks(5);
        assertEquals(Component.text("New"), board.sidebar(player).title());
    }

    @Test
    void rotationCopiesItsPagesAndRestoresTheBaseOnClose() {
        board.createBoard(player).title("Base").build();
        scheduler.advanceTicks(3);
        LayoutSection header = new LayoutSection("header", b -> b.title("Header"));
        Layout first = Layout.sections("first", List.of(header)).withSection(new LayoutSection("row", b -> b.line("One")));
        Layout second = Layout.named("second", b -> b.title("Header").line("Two"));
        SidebarRotation rotation = board.boards().rotate(player, List.of(first, second), 10);
        scheduler.advanceTicks(4);
        assertEquals(List.of(Component.text("One")), board.sidebar(player).lines());
        rotation.next();
        scheduler.advanceTicks(3);
        assertEquals(1, rotation.page());
        assertEquals(2, rotation.pageCount());
        assertEquals(List.of(Component.text("Two")), board.sidebar(player).lines());
        rotation.previous();
        rotation.close();
        scheduler.advanceTicks(4);
        assertEquals(Component.text("Base"), board.sidebar(player).title());
        assertTrue(rotation.isCancelled());
        assertThrows(IllegalArgumentException.class, () -> board.boards().rotate(player, List.of(), 1));
    }

    @Test
    void functionTabsRefreshAndBossExpiryIsIndependentOfItsRefreshInterval() {
        AtomicInteger headers = new AtomicInteger();
        board.tab(player).header(p -> "Header " + headers.incrementAndGet()).build();
        AtomicInteger progress = new AtomicInteger();
        ManagedBossBar bar = board.bossBar(player, "expiry").text(p -> "Tick")
                .progress(p -> progress.incrementAndGet() / 10.0).refreshEvery(100).hideAfter(5).show();
        scheduler.advanceTicks(4);
        assertFalse(bar.hidden());
        assertEquals(1, progress.get());
        scheduler.advanceTicks(1);
        assertTrue(bar.hidden());
        scheduler.advanceTicks(20);
        assertTrue(headers.get() >= 2);
        assertEquals(1, progress.get());
    }
}
