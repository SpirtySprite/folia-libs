package com.foliagui.gui;

import com.foliagui.FoliaGUI;
import com.foliagui.FoliaGUIService;
import com.foliagui.FoliaGUIStats;
import com.foliagui.item.GuiItem;
import com.foliagui.listener.GuiListener;
import com.foliagui.scheduler.Scheduler;
import com.foliagui.scheduler.TaskHandle;
import com.foliagui.util.ItemStackSerializer;
import net.foliacommons.diagnostics.Diagnostics;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpansionTest {
    private static ServerMock server;
    private PlayerMock player;
    private TestService service;
    private GuiListener listener;

    @BeforeAll
    static void server() {
        server = MockBukkit.mock();
        FoliaGUI.init(MockBukkit.createMockPlugin("Expansion"));
    }

    @AfterAll
    static void shutdown() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setup() {
        player = server.addPlayer();
        service = new TestService();
        listener = new GuiListener(service);
        server.getPluginManager().registerEvents(listener, service.plugin());
    }

    @AfterEach
    void cleanup() {
        service.close();
        HandlerList.unregisterAll(listener);
    }

    private Gui gui() {
        Gui gui = Gui.of(1, "Menu");
        gui.service(service);
        return gui;
    }

    private PaginatedGui pages(int capacity) {
        PaginatedGui gui = new PaginatedGui(2, Component.text("Pages"), capacity);
        gui.service(service);
        return gui;
    }

    private void open(BaseGui gui) {
        gui.open(player);
        service.queue.flush();
    }

    private void answer(String text) {
        player.chat(text);
        server.getScheduler().waitAsyncEventsFinished();
        service.queue.flush();
    }

    @Test
    void capacityUsesOnlyAvailableSlotsAndLastEntriesRemainReachable() {
        PaginatedGui gui = pages(45);
        for (int slot = 9; slot < 18; slot++) {
            gui.setItem(slot, new GuiItem(Material.BARRIER));
        }
        gui.addPageItem(IntStream.range(0, 20).mapToObj(index -> new GuiItem(new ItemStack(Material.STONE, index + 1))).toList());
        assertEquals(9, gui.pageCapacity());
        assertEquals(3, gui.getPagesCount());
        open(gui);
        gui.openLastPage();
        service.queue.flush();
        assertEquals(19, gui.getInventory().getItem(0).getAmount());
        assertEquals(20, gui.getInventory().getItem(1).getAmount());
    }

    @Test
    void supplierCachingIsBoundedAndTargetedInvalidationRendersOnlyThatEntry() {
        PaginatedGui gui = pages(9);
        AtomicInteger renders = new AtomicInteger();
        gui.cachePages(1).setPageItemSupplier(90, index -> {
            renders.incrementAndGet();
            return new GuiItem(Material.STONE);
        });
        open(gui);
        assertEquals(9, renders.get());
        gui.update();
        assertEquals(9, renders.get());
        gui.invalidateItem(0);
        assertEquals(10, renders.get());
        gui.next();
        gui.previous();
        assertEquals(28, renders.get());
        gui.invalidatePage(1);
        assertEquals(37, renders.get());
        gui.cachePages(0).update();
        gui.update();
        assertEquals(55, renders.get());
        assertThrows(IllegalArgumentException.class, () -> gui.cachePages(-1));
    }

    @Test
    void sharingIsRejectedAndClosingReleasesTheReservation() {
        Gui gui = gui();
        var first = gui.openAsync(player);
        PlayerMock other = server.addPlayer();
        var rejected = gui.openAsync(other);
        assertEquals(GuiOperationResult.REJECTED, rejected.join());
        service.queue.flush();
        assertEquals(GuiOperationResult.OPENED, first.join());
        var closed = gui.closeAsync(player);
        service.queue.flush();
        assertEquals(GuiOperationResult.CLOSED, closed.join());
        var second = gui.openAsync(other);
        service.queue.flush();
        assertEquals(GuiOperationResult.OPENED, second.join());
    }

    @Test
    void retiredAndClosedServicesProduceTerminalOpenResults() {
        Gui gui = gui();
        var pending = gui.openAsync(player);
        service.queue.retire();
        assertEquals(GuiOperationResult.RETIRED, pending.join());
        service.close();
        assertEquals(GuiOperationResult.REJECTED, gui.openAsync(player).join());
    }

    @Test
    void closingThisGuiDoesNotCloseAnotherWindow() {
        Gui first = gui();
        Gui second = gui();
        open(first);
        open(second);
        var result = first.closeAsync(player);
        service.queue.flush();
        assertEquals(GuiOperationResult.REJECTED, result.join());
        assertTrue(second.isOpenFor(player));
    }

    @Test
    void playerFactoryCreatesIndependentInstances() {
        var result = BaseGui.openFor(service, player, p -> Gui.of(1, p.getName()));
        service.queue.flush();
        assertEquals(GuiOperationResult.OPENED, result.join());
        assertEquals(com.foliagui.util.Text.label(player.getName()), service.guis().getOpenGui(player).title());
    }

    @Test
    void staleLoadsCannotReplaceNewerResultsOrTheirLoadingMarker() {
        Gui gui = gui();
        AtomicInteger value = new AtomicInteger();
        var old = AsyncContent.loadAsync(gui, player, () -> 1, value::set, null);
        service.queue.flush();
        var current = AsyncContent.loadAsync(gui, player, () -> 2, value::set, null);
        service.queue.flush();
        service.queue.async.remove(1).run();
        service.queue.flush();
        assertEquals(2, value.get());
        assertNull(gui.getGuiItem(4));
        service.queue.async.removeFirst().run();
        service.queue.flush();
        assertEquals(2, value.get());
        assertEquals(GuiOperationResult.SUPERSEDED, old.join());
        assertEquals(GuiOperationResult.OPENED, current.join());
    }

    @Test
    void closedSessionsRejectBothSuccessAndFailureCallbacks() {
        Gui gui = gui();
        AtomicInteger callbacks = new AtomicInteger();
        var loading = AsyncContent.loadAsync(gui, player, () -> { throw new IllegalStateException("fetch"); },
                value -> callbacks.incrementAndGet(), error -> callbacks.incrementAndGet());
        service.queue.flush();
        gui.close(player);
        service.queue.flush();
        service.queue.async.removeFirst().run();
        service.queue.flush();
        assertEquals(0, callbacks.get());
        assertEquals(GuiOperationResult.SUPERSEDED, loading.join());
    }

    @Test
    void loadingFailuresRemainObservableAndShowFeedback() {
        Gui gui = gui();
        AtomicInteger errors = new AtomicInteger();
        var result = AsyncContent.loadAsync(gui, player, () -> { throw new IllegalStateException("fetch"); },
                value -> { }, error -> errors.incrementAndGet());
        service.queue.flush();
        service.queue.async.removeFirst().run();
        service.queue.flush();
        assertTrue(result.isCompletedExceptionally());
        assertEquals(1, errors.get());
        assertEquals(Material.BARRIER, gui.getGuiItem(4).getItemStack().getType());
    }

    @Test
    void storageTitleAndRedrawPreserveDepositsAndSnapshotsAreIsolated() {
        StorageGui gui = new StorageGui(1, Component.text("Storage"));
        gui.service(service);
        gui.setItem(0, new GuiItem(Material.BARRIER));
        open(gui);
        gui.setStorageContents(new ItemStack[]{null, new ItemStack(Material.DIAMOND, 3)});
        gui.updateTitle("Renamed");
        gui.update();
        assertEquals(3, gui.getInventory().getItem(1).getAmount());
        var pending = gui.storageContentsAsync();
        service.queue.flush();
        ItemStack[] snapshot = pending.join();
        snapshot[1].setAmount(1);
        assertEquals(3, gui.getInventory().getItem(1).getAmount());
        assertNull(snapshot[0]);
    }

    @Test
    void snapshotsRejectStructuralMutationAndItemEditingRedraws() {
        Gui gui = gui();
        GuiItem item = new GuiItem(Material.STONE);
        gui.setItem(0, item);
        open(gui);
        assertThrows(UnsupportedOperationException.class, () -> gui.guiItemsSnapshot().clear());
        item.edit(stack -> stack.setAmount(5));
        gui.update();
        assertEquals(5, gui.getInventory().getItem(0).getAmount());
        ItemStack copy = item.itemStackSnapshot();
        copy.setAmount(1);
        assertEquals(5, item.getItemStack().getAmount());
        item.getItemStack().setAmount(7);
        gui.update();
        assertEquals(7, gui.getInventory().getItem(0).getAmount());
        PaginatedGui pages = pages(9);
        pages.addPageItem(item);
        assertThrows(UnsupportedOperationException.class, () -> pages.pageItemsSnapshot().clear());
        pages.replacePageItem(0, new GuiItem(Material.DIAMOND));
        assertEquals(Material.DIAMOND, pages.pageItemsSnapshot().getFirst().getItemStack().getType());
        assertTrue(pages.removePageItem(pages.pageItemsSnapshot().getFirst()));
    }

    @Test
    void filteringAndSortingPreserveSelectedEntryOrCurrentPage() {
        PaginatedGui gui = pages(3);
        PageView<Integer> view = gui.view(List.of(1, 2, 3, 4, 5, 6, 7), i -> new GuiItem(Material.STONE));
        gui.openPage(2);
        view.positionPolicy(PageView.PositionPolicy.KEEP_PAGE).filter(i -> i > 1);
        assertEquals(2, gui.getCurrentPage());
        view.positionPolicy(PageView.PositionPolicy.KEEP_SELECTED).selected(7).sort(Comparator.reverseOrder());
        assertEquals(1, gui.getCurrentPage());
        view.selected(2).refresh();
        assertEquals(2, gui.getCurrentPage());
        view.positionPolicy(PageView.PositionPolicy.RESET).refresh();
        assertEquals(1, gui.getCurrentPage());
    }

    @Test
    void searchableLifecycleAndAliasesStaySynchronized() {
        SearchablePaginatedGui gui = new SearchablePaginatedGui(1, Component.text("Search"), 0);
        gui.service(service);
        GuiItem apple = new GuiItem(Material.APPLE);
        GuiItem gem = new GuiItem(Material.DIAMOND);
        gui.addSearchableItem(apple, "RED APPLE").addSearchableItem(gem, "Blue gem");
        gui.search("apple red");
        assertEquals(List.of(apple), gui.pageItemsSnapshot());
        gui.searchKey(gem, "Blue gem", "Precious Stone").search("stone precious");
        assertEquals(List.of(gem), gui.pageItemsSnapshot());
        GuiItem fruit = new GuiItem(Material.MELON_SLICE);
        gui.replaceSearchableItem(gem, fruit, "Green fruit").search("fruit");
        assertTrue(gui.removeSearchableItem(fruit));
        gui.clearSearch();
        assertEquals(List.of(apple), gui.pageItemsSnapshot());
    }

    @Test
    void debouncedSearchRunsOffRenderingAndRejectsSupersededWork() {
        SearchablePaginatedGui gui = new SearchablePaginatedGui(1, Component.text("Search"), 0);
        gui.service(service);
        gui.addSearchableItem(new GuiItem(Material.APPLE), "apple").addSearchableItem(new GuiItem(Material.DIAMOND), "gem");
        open(gui);
        var first = gui.searchAsync(player, "apple", 2);
        var latest = gui.searchAsync(player, "gem", 2);
        service.queue.tick(2);
        assertEquals(GuiOperationResult.SUPERSEDED, first.join());
        service.queue.async.removeFirst().run();
        service.queue.flush();
        assertEquals(GuiOperationResult.OPENED, latest.join());
        assertEquals(Material.DIAMOND, gui.pageItemsSnapshot().getFirst().getItemStack().getType());
    }

    @Test
    void remotePaginationFetchesOnlyRequestedPagesAndRejectsLateResults() {
        PaginatedGui gui = pages(3);
        List<RemotePages.Request> requests = new ArrayList<>();
        List<CompletableFuture<RemotePages.Page<Integer>>> results = new ArrayList<>();
        RemotePages<Integer> remote = gui.remotePages(player, request -> {
            requests.add(request);
            var future = new CompletableFuture<RemotePages.Page<Integer>>();
            results.add(future);
            return future;
        }, amount -> new GuiItem(new ItemStack(Material.STONE, amount)));
        service.queue.flush();
        service.queue.async.removeFirst().run();
        assertEquals(new RemotePages.Request(1, 3), requests.getFirst());
        results.getFirst().complete(new RemotePages.Page<>(List.of(1, 2, 3), 9));
        service.queue.flush();
        gui.next();
        service.queue.async.removeFirst().run();
        var refresh = remote.refresh();
        service.queue.async.removeFirst().run();
        results.get(2).complete(new RemotePages.Page<>(List.of(4, 5, 6), 9));
        service.queue.flush();
        results.get(1).complete(new RemotePages.Page<>(List.of(7, 8, 9), 9));
        service.queue.flush();
        assertEquals(4, gui.getInventory().getItem(0).getAmount());
        assertEquals(GuiOperationResult.OPENED, refresh.join());
        remote.close();
        assertEquals(GuiOperationResult.REJECTED, remote.refresh().join());
    }

    @Test
    void textValidationRetriesAndReturnsOneSubmittedResult() {
        InputSession session = TextInput.builder().mode(TextInput.Mode.CHAT).timeout(20)
                .validate(InputValidator.nonblank("required").and(InputValidator.length(2, 4, "length")))
                .build().open(service, player);
        service.queue.flush();
        answer("x");
        assertFalse(session.result().isDone());
        answer("yes");
        assertEquals(InputResult.submitted("yes"), session.result().join());
        assertFalse(ChatPrompt.hasSession(service, player));
        service.queue.tick(25);
        assertEquals(InputResult.Status.SUBMITTED, session.result().join().status());
    }

    @Test
    void inputReplacementTimeoutCancellationAndDisconnectAreExplicit() {
        TextInput recipe = TextInput.builder().mode(TextInput.Mode.CHAT).timeout(4).build();
        InputSession first = recipe.open(service, player);
        service.queue.flush();
        InputSession second = recipe.open(service, player);
        service.queue.flush();
        assertEquals(InputResult.Status.CANCELLED, first.result().join().status());
        service.queue.tick(4);
        assertEquals(InputResult.Status.TIMED_OUT, second.result().join().status());
        InputSession cancelled = recipe.open(service, player);
        service.queue.flush();
        cancelled.close();
        service.queue.flush();
        assertEquals(InputResult.Status.CANCELLED, cancelled.result().join().status());
        InputSession disconnected = recipe.open(service, player);
        service.queue.flush();
        service.sessions().disconnect(player);
        assertEquals(InputResult.Status.DISCONNECTED, disconnected.result().join().status());
    }

    @Test
    void fallbackHandlesNativePresentationFailuresAndUsesChat() {
        InputSession session = TextInput.builder().fallback(TextInput.Mode.ANVIL, TextInput.Mode.CHAT).build().open(service, player);
        service.queue.flush();
        assertTrue(ChatPrompt.hasSession(service, player));
        answer("fallback");
        assertEquals(InputResult.Status.SUBMITTED, session.result().join().status());
    }

    @Test
    void validatorsRejectNonfiniteAndOutOfRangeNumbers() {
        InputValidator numbers = InputValidator.number(1, 10, "range");
        assertTrue(numbers.validate("NaN").isPresent());
        assertTrue(numbers.validate("Infinity").isPresent());
        assertTrue(numbers.validate("0").isPresent());
        assertTrue(numbers.validate("letters").isPresent());
        assertTrue(numbers.validate("5").isEmpty());
        assertTrue(InputValidator.nonblank("required").validate(" ").isPresent());
        assertThrows(IllegalArgumentException.class, () -> InputValidator.length(5, 1, "bad"));
        assertThrows(IllegalArgumentException.class, () -> InputValidator.number(10, 1, "bad"));
        assertThrows(IllegalArgumentException.class, () -> new InputResult(InputResult.Status.CANCELLED, Optional.of("bad")));
    }

    @Test
    void formsNavigateBackProduceTypedResultsAndRestoreOrigin() {
        Gui origin = gui();
        open(origin);
        TextInput input = TextInput.builder().mode(TextInput.Mode.CHAT).build();
        InputForm<String> form = InputForm.<String>builder(values -> values.get("name") + ":" + values.get("amount"))
                .step("name", input, text -> text).step("amount", input, Integer::parseInt).build();
        var session = form.open(service, player);
        service.queue.flush();
        answer("first");
        session.back();
        service.queue.flush();
        answer("second");
        answer("bad-number");
        assertFalse(session.result().isDone());
        answer("7");
        service.queue.flush();
        assertEquals("second:7", session.result().join().value().orElseThrow());
        assertTrue(origin.isOpenFor(player));
    }

    @Test
    void formsCancelAndShutdownTerminatePendingInput() {
        TextInput input = TextInput.builder().mode(TextInput.Mode.CHAT).build();
        InputForm<String> form = InputForm.<String>builder(values -> (String) values.get("name"))
                .step("name", input, text -> text).restoreMenu(false).build();
        var session = form.open(service, player);
        service.queue.flush();
        session.close();
        service.queue.flush();
        assertEquals(InputResult.Status.CANCELLED, session.result().join().status());
        InputSession pending = input.open(service, player);
        service.queue.flush();
        service.close();
        assertEquals(InputResult.Status.CANCELLED, pending.result().join().status());
    }

    @Test
    void playerThemesAndMessagesDriveControlsAndPromptTemplates() {
        GuiTheme themed = new GuiTheme().messages(key -> switch (key) {
            case PAGE -> "Page {0} of {1}";
            case LOADING -> "Wait";
            default -> key.defaultText();
        });
        service.themeResolver(viewer -> themed);
        PaginatedGui gui = pages(3);
        gui.pageControls(9, 13, 17).addPageItem(IntStream.range(0, 8).mapToObj(i -> new GuiItem(Material.STONE)).toList());
        open(gui);
        assertEquals(themed, gui.theme());
        assertEquals("Page 1 of 3", themed.message(GuiMessage.PAGE, 1, 3));
        assertEquals(Component.text("Page 1 of 3").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                gui.getInventory().getItem(13).getItemMeta().displayName());
        assertEquals("Wait", themed.message(GuiMessage.LOADING));
    }

    @Test
    void foreignServiceNavigationIsRejectedWithoutHistoryChanges() {
        TestService other = new TestService();
        Gui gui = gui();
        assertThrows(IllegalArgumentException.class, () -> other.navigation().open(player, gui));
        assertEquals(0, other.navigation().depth(player));
    }

    @Test
    void conditionalSessionRemovalDoesNotDiscardAReplacement() {
        SessionRegistry<String> registry = new SessionRegistry<>();
        registry.put(player, "old");
        registry.put(player, "new");
        assertFalse(registry.remove(player, "old"));
        assertEquals("new", registry.get(player));
    }

    @Test
    void serializersRejectNegativeHugeTruncatedAndUnexpectedData() throws Exception {
        for (int count : List.of(-1, Integer.MAX_VALUE)) {
            String data = Base64.getEncoder().encodeToString(ByteBuffer.allocate(4).putInt(count).array());
            assertThrows(IllegalStateException.class, () -> ItemStackSerializer.fromBase64Compact(data));
        }
        String truncated = Base64.getEncoder().encodeToString(ByteBuffer.allocate(8).putInt(1).putInt(1000).array());
        assertThrows(IllegalStateException.class, () -> ItemStackSerializer.fromBase64Compact(truncated));
        try (var bytes = new ByteArrayOutputStream(); var objects = new BukkitObjectOutputStream(bytes)) {
            objects.writeInt(1);
            objects.writeObject("unexpected");
            objects.flush();
            String encoded = Base64.getEncoder().encodeToString(bytes.toByteArray());
            assertThrows(IllegalStateException.class, () -> ItemStackSerializer.fromBase64(encoded));
        }
        ItemStack[] contents = ItemStackSerializer.fromBase64Compact(ItemStackSerializer.toBase64Compact(new ItemStack[]{null}));
        assertEquals(1, contents.length);
        assertNull(contents[0]);
    }

    @Test
    void nativeSessionIdentityDoesNotConsumeUnrelatedCloseEvents() throws Exception {
        AnvilGui anvil = AnvilGui.builder().service(service).build();
        service.sessions().anvil.put(player, anvil);
        Gui gui = gui();
        open(gui);
        var close = new org.bukkit.event.inventory.InventoryCloseEvent(player.getOpenInventory());
        assertFalse(AnvilGui.handleClose(service, close));
        assertEquals(anvil, service.sessions().anvil.get(player));
        var field = AnvilGui.class.getDeclaredField("openedInventory");
        field.setAccessible(true);
        field.set(anvil, gui.getInventory());
        assertTrue(AnvilGui.handleClose(service, close));
        assertFalse(AnvilGui.hasSession(service, player));
    }

    @Test
    void merchantPurchaseCallbacksIgnoreCancelledAndUnrelatedWindows() {
        AtomicInteger purchases = new AtomicInteger();
        org.bukkit.inventory.MerchantRecipe recipe = new org.bukkit.inventory.MerchantRecipe(new ItemStack(Material.DIAMOND), 100);
        recipe.setIngredients(List.of(new ItemStack(Material.EMERALD)));
        MerchantGui merchant = MerchantGui.builder().service(service).addRecipe(recipe)
                .onPurchase((p, trade) -> purchases.incrementAndGet()).onResultClick((p, trade) -> { }).build();
        merchant.open(player);
        service.queue.flush();
        assertTrue(MerchantGui.hasSession(service, player));
        org.bukkit.inventory.Merchant actor = ((org.bukkit.inventory.MerchantInventory) player.getOpenInventory().getTopInventory()).getMerchant();
        var event = new io.papermc.paper.event.player.PlayerPurchaseEvent(player, actor, recipe, false, true);
        event.setCancelled(true);
        MerchantGui.handlePurchase(service, event);
        assertEquals(0, purchases.get());
        event.setCancelled(false);
        MerchantGui.handlePurchase(service, event);
        assertEquals(1, purchases.get());
        org.bukkit.inventory.InventoryView previous = player.getOpenInventory();
        Gui unrelated = gui();
        open(unrelated);
        MerchantGui.handlePurchase(service, event);
        assertEquals(1, purchases.get());
        assertFalse(MerchantGui.handleClose(service, new org.bukkit.event.inventory.InventoryCloseEvent(previous)));
    }

    @Test
    void rejectedAsyncDispatchTerminatesContentAndSearchOperations() {
        Gui gui = gui();
        service.queue.rejectAsync = true;
        var result = AsyncContent.loadAsync(gui, player, () -> 1, value -> { }, null);
        service.queue.flush();
        assertEquals(GuiOperationResult.REJECTED, result.join());
        SearchablePaginatedGui search = new SearchablePaginatedGui(1, Component.text("Search"), 0);
        search.service(service);
        open(search);
        var searched = search.searchAsync(player, "term", 0);
        service.queue.flush();
        assertEquals(GuiOperationResult.REJECTED, searched.join());
    }

    @Test
    void remoteInvalidDataAndClosedWindowsHaveObservableOutcomes() {
        PaginatedGui gui = pages(3);
        var supplied = new CompletableFuture<RemotePages.Page<Integer>>();
        RemotePages<Integer> remote = gui.remotePages(player, request -> supplied, i -> new GuiItem(Material.STONE));
        service.queue.flush();
        service.queue.async.removeFirst().run();
        var refresh = remote.refresh();
        service.queue.async.removeFirst().run();
        supplied.complete(new RemotePages.Page<>(List.of(1, 2, 3, 4), 4));
        service.queue.flush();
        assertTrue(refresh.isCompletedExceptionally());
        assertThrows(IllegalArgumentException.class, () -> new RemotePages.Page<>(List.of(1), 0));
        assertThrows(IllegalArgumentException.class, () -> new RemotePages.Request(0, 3));
        remote.close();
    }

    @Test
    void quantityPresentationUsesPlayerLocalization() {
        service.themeResolver(p -> new GuiTheme().messages(key -> key == GuiMessage.QUANTITY_TITLE ? "Amount" : key.defaultText()));
        Gui gui = QuantityGui.builder().service(service).build(player);
        assertEquals(com.foliagui.util.Text.label("Amount"), gui.title());
    }

    @Test
    void staleTimeoutExecutionCannotRemoveTheReplacementChatPrompt() {
        AtomicInteger second = new AtomicInteger();
        ChatPrompt.ask(service, player, "first", 4, text -> { });
        QueueScheduler.Timer old = service.queue.timers.getFirst();
        ChatPrompt.ask(service, player, "second", 4, text -> second.incrementAndGet());
        service.queue.flush();
        old.action.run();
        assertTrue(ChatPrompt.hasSession(service, player));
        assertEquals(0, second.get());
        answer("new answer");
        assertEquals(1, second.get());
    }

    @Test
    void adapterReportsRejectedDispatchWithoutLeavingManagedOperationsPending() {
        var delegate = net.foliacommons.scheduler.Scheduler.deterministic();
        var adapter = new com.foliagui.scheduler.PaperFoliaScheduler(delegate);
        delegate.close();
        AtomicInteger stopped = new AtomicInteger();
        adapter.runForEntity(player, () -> { throw new AssertionError("must not run"); }, stopped::incrementAndGet);
        adapter.runForEntityLater(player, () -> { throw new AssertionError("must not run"); }, stopped::incrementAndGet, 2);
        assertEquals(2, stopped.get());
        assertFalse(adapter.tryRunAsync(() -> { }));
        assertFalse(adapter.tryRunForLocation(player.getLocation(), () -> { }));
    }

    @Test
    void explicitServiceSoundPlaybackIsScheduled() {
        int heard = player.getHeardSounds().size();
        new GuiTheme.ThemeSound(org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f).play(service, player);
        assertEquals(heard, player.getHeardSounds().size());
        service.queue.flush();
        assertEquals(heard + 1, player.getHeardSounds().size());
    }

    private static final class TestService implements FoliaGUIService {
        final QueueScheduler queue = new QueueScheduler();
        final GuiRegistry guis = new GuiRegistry(this);
        final GuiNavigation navigation = new GuiNavigation(this);
        final GuiSessions sessions = new GuiSessions();
        GuiTheme theme = new GuiTheme();
        java.util.function.Function<Player, GuiTheme> resolver;
        boolean closed;

        @Override public Plugin plugin() { return FoliaGUI.plugin(); }
        @Override public Scheduler scheduler() { return queue; }
        @Override public NamespacedKey itemKey() { return GuiItem.IDENTITY_KEY; }
        @Override public GuiTheme theme() { return theme; }
        @Override public GuiTheme theme(Player player) { return resolver == null ? theme : resolver.apply(player); }
        @Override public void theme(GuiTheme replacement) { theme = replacement; }
        @Override public void themeResolver(java.util.function.Function<Player, GuiTheme> resolver) { this.resolver = resolver; }
        @Override public GuiRegistry guis() { return guis; }
        @Override public GuiNavigation navigation() { return navigation; }
        @Override public GuiSessions sessions() { return sessions; }
        @Override public FoliaGUIStats stats() { return FoliaGUI.service().stats(); }
        @Override public Diagnostics diagnose() { return FoliaGUI.service().diagnose(); }
        @Override public void close() { closed = true; sessions.clearAll(); }
        @Override public boolean isClosed() { return closed; }
    }

    private static final class QueueScheduler implements Scheduler {
        private record Dispatch(Runnable task, Runnable retired) { }
        final Queue<Dispatch> entities = new ArrayDeque<>();
        final List<Runnable> async = new ArrayList<>();
        final List<Timer> timers = new ArrayList<>();
        long time;
        boolean rejectAsync;

        void flush() {
            int bound = 1000;
            while (!entities.isEmpty() && bound-- > 0) { entities.remove().task().run(); }
            assertTrue(bound > 0, "dispatch loop must terminate");
        }

        void retire() {
            Dispatch dispatch = entities.remove();
            if (dispatch.retired() != null) { dispatch.retired().run(); }
        }

        void tick(long ticks) {
            time += ticks;
            for (Timer timer : List.copyOf(timers)) {
                if (!timer.cancelled && timer.at <= time) {
                    timer.at += timer.period;
                    entities.add(new Dispatch(timer.action, null));
                }
            }
            flush();
        }

        @Override public void runForEntity(Entity entity, Runnable task, Runnable retired) { entities.add(new Dispatch(task, retired)); }
        @Override public void runForEntityLater(Entity entity, Runnable task, Runnable retired, long delay) {
            Timer timer = new Timer(task, time + delay, Long.MAX_VALUE / 2);
            timers.add(timer);
        }
        @Override public TaskHandle runForEntityTimer(Entity entity, Runnable task, Runnable retired, long delay, long period) {
            Timer timer = new Timer(task, time + delay, period);
            timers.add(timer);
            return timer;
        }
        @Override public void runForLocation(Location location, Runnable task) { entities.add(new Dispatch(task, null)); }
        @Override public void runGlobal(Runnable task) { entities.add(new Dispatch(task, null)); }
        @Override public void runAsync(Runnable task) { async.add(task); }
        @Override public boolean tryRunAsync(Runnable task) {
            if (rejectAsync) { return false; }
            runAsync(task);
            return true;
        }
        @Override public boolean isFolia() { return false; }

        private static final class Timer implements TaskHandle {
            final Runnable action;
            long at;
            final long period;
            boolean cancelled;
            Timer(Runnable action, long at, long period) { this.action = action; this.at = at; this.period = period; }
            @Override public void cancel() { cancelled = true; }
            @Override public boolean isCancelled() { return cancelled; }
        }
    }
}
