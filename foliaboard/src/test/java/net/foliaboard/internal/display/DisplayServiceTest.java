package net.foliaboard.internal.display;

import net.foliaboard.api.display.DisplayStyle;
import net.foliaboard.api.display.DisplayTransform;
import net.foliaboard.api.display.DisplayVisibility;
import net.foliaboard.api.display.ManagedTextDisplay;
import net.foliaboard.api.display.TextDisplayStyle;
import net.foliaboard.internal.packet.PacketAdapter;
import net.foliaboard.internal.scheduler.Schedulers;
import net.foliaboard.internal.service.BoardRuntime;
import net.foliacommons.scheduler.DeterministicScheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DisplayServiceTest {
    private MockedStatic<Bukkit> bukkit;
    private DeterministicScheduler scheduler;
    private DisplayService displays;
    private FakeTransport transport;
    private Player owner;
    private Player viewer;
    private World world;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        scheduler = new DeterministicScheduler();
        Schedulers.setSchedulerForTesting(scheduler);
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("display-test"));
        world = world();
        owner = player(UUID.randomUUID());
        viewer = player(UUID.randomUUID());
        when(owner.getTrackedBy()).thenReturn(Set.of(viewer));
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(owner, viewer));
        transport = new FakeTransport();
        displays = new DisplayService(new BoardRuntime(plugin, mock(PacketAdapter.class)), transport);
    }

    private World world() {
        World value = mock(World.class);
        when(value.getUID()).thenReturn(UUID.randomUUID());
        return value;
    }

    private Player player(UUID id) {
        Player value = mock(Player.class);
        when(value.getUniqueId()).thenReturn(id);
        when(value.isOnline()).thenReturn(true);
        when(value.isValid()).thenReturn(true);
        when(value.getLocation()).thenReturn(new Location(world, 1, 64, 1));
        when(value.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(value.getEntityId()).thenReturn(id.hashCode() & Integer.MAX_VALUE);
        when(value.canSee(any(Player.class))).thenReturn(true);
        when(value.getTrackedBy()).thenReturn(Set.of());
        when(value.getPassengers()).thenReturn(List.of());
        return value;
    }

    @AfterEach
    void tearDown() {
        displays.closeAll();
        scheduler.close();
        Schedulers.setSchedulerForTesting(null);
        bukkit.close();
    }

    private ManagedTextDisplay nametag() {
        ManagedTextDisplay result = displays.nametag(owner, Component.text("owner"));
        scheduler.advanceTicks(4);
        return result;
    }

    private List<DisplayFrame> frames(Player player) {
        return transport.connections.get(player.getUniqueId()).frames.stream()
                .filter(frame -> frame.accepted().getAsBoolean()).toList();
    }

    @Test
    void defaultSelfHidingSurvivesRefreshVisibilityAndTeleport() {
        var tag = nametag();
        assertTrue(frames(owner).isEmpty());
        assertEquals(owner.getEntityId(), frames(viewer).getFirst().vehicle());
        for (int i = 0; i < 20; i++) {
            tag.show(owner.getUniqueId());
            tag.refresh();
            tag.visible(false);
            tag.visible(true);
            displays.onTransition(owner);
            scheduler.advanceTicks(4);
            assertTrue(frames(owner).isEmpty());
            assertEquals(1, frames(viewer).size());
        }
    }

    @Test
    void queuedPresentationIsRejectedDuringTeleportAndUsesActualArrival() {
        nametag();
        DisplayFrame old = frames(viewer).getFirst();
        displays.onTransition(owner);
        assertFalse(old.accepted().getAsBoolean());
        when(owner.getLocation()).thenReturn(new Location(world, 20, 70, 5));
        scheduler.advanceTicks(1);
        assertTrue(frames(viewer).isEmpty());
        scheduler.advanceTicks(4);
        DisplayFrame arrived = frames(viewer).getFirst();
        assertEquals(20, arrived.x());
        assertEquals(70, arrived.y());
        assertNotEquals(old.generation(), arrived.generation());
    }

    @Test
    void viewerTransitionRejectsQueuedOldWorldFrames() {
        var tag = nametag();
        DisplayFrame old = frames(viewer).getFirst();
        displays.onTransition(viewer);
        World destination = world();
        when(viewer.getLocation()).thenReturn(new Location(destination, 1, 64, 1));
        scheduler.advanceTicks(4);
        assertFalse(old.accepted().getAsBoolean());
        assertTrue(frames(viewer).isEmpty());
        assertFalse(tag.isClosed());
    }

    @Test
    void hiddenViewerCannotReturnThroughRefreshOrReconnect() {
        var tag = nametag();
        DisplayFrame old = frames(viewer).getFirst();
        tag.hide(viewer.getUniqueId());
        assertFalse(old.accepted().getAsBoolean());
        tag.refresh();
        displays.onQuit(viewer);
        Player reconnect = player(viewer.getUniqueId());
        when(owner.getTrackedBy()).thenReturn(Set.of(reconnect));
        displays.onJoin(reconnect);
        scheduler.advanceTicks(4);
        assertTrue(frames(reconnect).isEmpty());
        tag.show(reconnect.getUniqueId());
        scheduler.advanceTicks(2);
        assertEquals(1, frames(reconnect).size());
    }

    @Test
    void ownerDisconnectClosesHandleAndDoesNotReuseSessionOnReconnect() {
        var tag = nametag();
        DisplayFrame old = frames(viewer).getFirst();
        displays.onQuit(owner);
        assertTrue(tag.isClosed());
        assertFalse(old.accepted().getAsBoolean());
        Player reconnect = player(owner.getUniqueId());
        displays.onJoin(reconnect);
        displays.onQuit(owner);
        assertEquals(0, displays.stats().viewers());
        assertThrows(IllegalStateException.class, tag::refresh);
    }

    @Test
    void shutdownRejectsUnpresentedHandlesAndQueuedFrames() {
        var tag = displays.nametag(owner, Component.text("pending"));
        displays.closeAll();
        scheduler.advanceTicks(5);
        assertTrue(tag.isClosed());
        assertEquals(0, displays.stats().handles());
        assertEquals(0, displays.stats().viewers());
        assertThrows(IllegalStateException.class, () -> displays.text(new Location(world, 0, 64, 0), Component.empty()));
    }

    @Test
    void deathInvisibilitySneakingAndSpectatorPoliciesSuppressPresentation() {
        var tag = nametag();
        when(owner.isDead()).thenReturn(true);
        scheduler.advanceTicks(2);
        assertTrue(frames(viewer).isEmpty());
        when(owner.isDead()).thenReturn(false);
        when(owner.isInvisible()).thenReturn(true);
        scheduler.advanceTicks(2);
        assertTrue(frames(viewer).isEmpty());
        when(owner.isInvisible()).thenReturn(false);
        when(owner.getGameMode()).thenReturn(GameMode.SPECTATOR);
        scheduler.advanceTicks(2);
        assertTrue(frames(viewer).isEmpty());
        when(owner.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(owner.isSneaking()).thenReturn(true);
        tag.visibility(new DisplayVisibility(48, false, true, true, true));
        scheduler.advanceTicks(2);
        assertTrue(frames(viewer).isEmpty());
    }

    @Test
    void nativeTrackingVanishAndRangeAreRequired() {
        nametag();
        when(owner.getTrackedBy()).thenReturn(Set.of());
        scheduler.advanceTicks(2);
        assertTrue(frames(viewer).isEmpty());
        when(owner.getTrackedBy()).thenReturn(Set.of(viewer));
        when(viewer.canSee(owner)).thenReturn(false);
        scheduler.advanceTicks(2);
        assertTrue(frames(viewer).isEmpty());
        when(viewer.canSee(owner)).thenReturn(true);
        when(viewer.getLocation()).thenReturn(new Location(world, 100, 64, 1));
        scheduler.advanceTicks(2);
        assertTrue(frames(viewer).isEmpty());
    }

    @Test
    void failingProvidersAreIsolatedAndSelfPolicyPrecedesCallbacks() {
        var tag = nametag();
        tag.textFor(player -> {
            assertNotEquals(owner.getUniqueId(), player.getUniqueId());
            throw new IllegalStateException("provider unavailable");
        });
        scheduler.advanceTicks(2);
        assertTrue(frames(viewer).isEmpty());
        assertTrue(displays.stats().providerFailures() > 0);
        tag.text(Component.text("restored"));
        scheduler.advanceTicks(2);
        assertEquals(Component.text("restored"), frames(viewer).getFirst().text());
    }

    @Test
    void providerMayCloseHandleWithoutPublishingItAgain() {
        var tag = nametag();
        tag.textFor(player -> {
            tag.close();
            return Component.text("discarded");
        });
        scheduler.advanceTicks(2);
        assertTrue(frames(viewer).isEmpty());
        assertTrue(tag.isClosed());
    }

    @Test
    void fixedLocationIsCopiedAndCanBeReattached() {
        Location input = new Location(world, 3, 65, 3);
        var tag = displays.text(input, Component.text("fixed"));
        input.setX(100);
        scheduler.advanceTicks(4);
        assertEquals(3, frames(viewer).getFirst().x());
        assertEquals(-1, frames(viewer).getFirst().vehicle());
        tag.attach(owner, 1);
        scheduler.advanceTicks(2);
        assertEquals(owner.getEntityId(), frames(viewer).getFirst().vehicle());
        tag.location(new Location(world, 5, 66, 5));
        scheduler.advanceTicks(2);
        assertEquals(-1, frames(viewer).getFirst().vehicle());
    }

    @Test
    void nativePassengersAndPoseCorrectionAreCapturedOnOwnerTick() {
        Player passenger = player(UUID.randomUUID());
        when(owner.getPassengers()).thenReturn(List.of(passenger));
        transport.correction = 0.45f;
        nametag();
        assertEquals(List.of(passenger.getEntityId()), frames(viewer).getFirst().nativePassengers());
        assertEquals(0.7f, frames(viewer).getFirst().mountCorrection(), 0.001);
        transport.correction = 0.2f;
        scheduler.advanceTicks(2);
        assertEquals(0.45f, frames(viewer).getFirst().mountCorrection(), 0.001);
    }

    @Test
    void itemInputAndProviderResultsAreCopied() {
        ItemStack input = item(2);
        var item = displays.item(new Location(world, 1, 64, 1), input);
        input.setAmount(10);
        scheduler.advanceTicks(4);
        assertEquals(2, frames(viewer).getFirst().item().getAmount());
        ItemStack provided = item(3);
        item.itemFor(player -> provided);
        scheduler.advanceTicks(2);
        var frame = frames(viewer).getFirst();
        provided.setAmount(9);
        assertEquals(3, frame.item().getAmount());
    }

    @Test
    void explicitSelfVisibilityCanBeEnabledAndDisabled() {
        var tag = nametag();
        tag.visibility(new DisplayVisibility(48, true, true, false, true));
        scheduler.advanceTicks(2);
        DisplayFrame own = frames(owner).getFirst();
        tag.visibility(DisplayVisibility.defaults());
        assertFalse(own.accepted().getAsBoolean());
        scheduler.advanceTicks(2);
        assertTrue(frames(owner).isEmpty());
    }

    @Test
    void stylesValidateAndUseImmutableTransforms() {
        assertThrows(IllegalArgumentException.class, () -> new DisplayVisibility(Double.NaN, false, true, false, true));
        assertThrows(IllegalArgumentException.class, () -> new DisplayTransform.Vector(Float.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new DisplayTransform.Rotation(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TextDisplayStyle(0, 0, 255, true, false, false,
                org.bukkit.entity.TextDisplay.TextAlignment.CENTER));
        var tag = nametag();
        tag.style(DisplayStyle.defaults().transformed(DisplayTransform.identity().scaled(2, 2, 2)));
        scheduler.advanceTicks(2);
        assertEquals(2, frames(viewer).getFirst().style().transform().scale().x());
        assertEquals(new DisplayTransform.Rotation(0, 0, 0, 1), new DisplayTransform.Rotation(0, 0, 0, 5));
    }

    @Test
    void mergingPassengersPreservesNativeOrderAndDeduplicates() {
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{4, 2, 9, 10},
                PassengerLists.merge(new int[]{4, 2}, new int[]{9, 2, 10}));
    }

    @Test
    void quitBeforeBootstrapClosesPendingOwnerHandle() {
        var tag = displays.nametag(owner, Component.text("pending"));
        displays.onQuit(owner);
        scheduler.advanceTicks(4);
        assertTrue(tag.isClosed());
        assertEquals(0, displays.stats().handles());
        assertEquals(0, displays.stats().viewers());
    }

    @Test
    void replacingOwnerSessionDoesNotLeaveIdleViewerTasks() {
        var tag = nametag();
        displays.onJoin(player(owner.getUniqueId()));
        assertTrue(tag.isClosed());
        assertEquals(0, displays.stats().viewers());
    }

    @Test
    void queuedFramesRejectNewInvisibilityTrackingAndViewerRange() {
        nametag();
        var old = frames(viewer).getFirst();
        when(owner.isInvisible()).thenReturn(true);
        scheduler.advanceTicks(2);
        assertFalse(old.accepted().getAsBoolean());
        when(owner.isInvisible()).thenReturn(false);
        scheduler.advanceTicks(2);
        old = frames(viewer).getFirst();
        when(owner.getTrackedBy()).thenReturn(Set.of());
        scheduler.advanceTicks(2);
        assertFalse(old.accepted().getAsBoolean());
        when(owner.getTrackedBy()).thenReturn(Set.of(viewer));
        scheduler.advanceTicks(2);
        old = frames(viewer).getFirst();
        when(viewer.getLocation()).thenReturn(new Location(world, 500, 64, 1));
        scheduler.advanceTicks(2);
        assertFalse(old.accepted().getAsBoolean());
    }

    @Test
    void queuedFramesRejectProviderFiltersAndAnyContentMutation() {
        var tag = nametag();
        var old = frames(viewer).getFirst();
        tag.text(Component.text("new"));
        assertFalse(old.accepted().getAsBoolean());
        assertTrue(old.retained().getAsBoolean());
        scheduler.advanceTicks(2);
        old = frames(viewer).getFirst();
        when(viewer.canSee(owner)).thenReturn(false);
        scheduler.advanceTicks(2);
        assertFalse(old.accepted().getAsBoolean());
        assertFalse(old.retained().getAsBoolean());
    }

    @Test
    void fixedMovesPreserveClientGenerationWhileRejectingQueuedPositions() {
        var tag = displays.text(new Location(world, 1, 64, 1), Component.text("fixed"));
        scheduler.advanceTicks(4);
        var old = frames(viewer).getFirst();
        tag.location(new Location(world, 3, 65, 3));
        assertFalse(old.accepted().getAsBoolean());
        scheduler.advanceTicks(2);
        assertEquals(old.generation(), frames(viewer).getFirst().generation());
        assertEquals(3, frames(viewer).getFirst().x());
    }

    @Test
    void failedTransportRetriesAfterBackoffWithoutClosingHandles() {
        var tag = nametag();
        FakeConnection broken = transport.connections.get(viewer.getUniqueId());
        broken.closed = true;
        scheduler.advanceTicks(5);
        assertEquals(broken, transport.connections.get(viewer.getUniqueId()));
        scheduler.advanceTicks(20);
        assertNotEquals(broken, transport.connections.get(viewer.getUniqueId()));
        assertEquals(1, frames(viewer).size());
        assertFalse(tag.isClosed());
    }

    @Test
    void buildersPreserveIndependentSettingsAndValidateOnBuild() {
        var builder = DisplayStyle.builder().shadowRadius(2).shadowStrength(0.5f).blockLight(15).skyLight(10)
                .billboard(org.bukkit.entity.Display.Billboard.FIXED).width(3).height(4).glowing(true).glowColor(0xff0000)
                .teleportTicks(5).interpolationTicks(8).transform(DisplayTransform.identity().scaled(2, 2, 2));
        var style = builder.build();
        builder.shadowRadius(7);
        assertEquals(2, style.shadowRadius());
        assertEquals(15, style.blockLight());
        assertEquals(10, style.skyLight());
        assertEquals(style, style.toBuilder().build());
        var tag = nametag();
        tag.style(style);
        tag.textStyle(TextDisplayStyle.builder().lineWidth(150).background(0x80000000).opacity(200).shadow(false)
                .seeThrough(true).defaultBackground(true).alignment(org.bukkit.entity.TextDisplay.TextAlignment.LEFT).build());
        tag.visibility(DisplayVisibility.builder().range(90).selfVisible(false).hideInvisible(false)
                .hideSneaking(true).hideSpectators(false).build());
        assertEquals(style, tag.style());
        assertEquals(150, tag.textStyle().lineWidth());
        assertEquals(tag.textStyle(), tag.textStyle().toBuilder().build());
        assertEquals(90, tag.visibility().range());
        assertEquals(tag.visibility(), tag.visibility().toBuilder().build());
        tag.visible(false);
        assertFalse(tag.isVisible());
        assertThrows(IllegalArgumentException.class, () -> DisplayStyle.builder().blockLight(5).build());
        assertThrows(IllegalArgumentException.class, () -> TextDisplayStyle.builder().opacity(256).build());
        assertThrows(IllegalArgumentException.class, () -> DisplayVisibility.builder().range(-1).build());
        var item = displays.item(new Location(world, 1, 64, 1), item(1));
        item.itemTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.GUI);
        assertEquals(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.GUI, item.itemTransform());
    }

    @Test
    void viewerStylesAreResolvedAfterSelfPolicyAndResetBySharedSetters() {
        var tag = nametag();
        var style = DisplayStyle.builder().glowing(true).glowColor(0x112233).build();
        var text = TextDisplayStyle.builder().background(0x80000000).build();
        tag.styleFor(player -> {
            assertEquals(viewer, player);
            return style;
        });
        tag.textStyleFor(player -> text);
        scheduler.advanceTicks(2);
        assertTrue(frames(owner).isEmpty());
        assertEquals(style, frames(viewer).getFirst().style());
        assertEquals(text, frames(viewer).getFirst().textStyle());
        tag.styleFor(player -> { throw new IllegalStateException("unavailable"); });
        scheduler.advanceTicks(2);
        assertTrue(frames(viewer).isEmpty());
        tag.style(DisplayStyle.defaults());
        tag.textStyle(TextDisplayStyle.defaults());
        scheduler.advanceTicks(2);
        assertEquals(DisplayStyle.defaults(), frames(viewer).getFirst().style());
        assertEquals(TextDisplayStyle.defaults(), frames(viewer).getFirst().textStyle());
    }

    @Test
    void retiredOwnerRejectsCreationAndReleasesActiveHandles() {
        var tag = nametag();
        scheduler.retire(owner);
        assertTrue(tag.isClosed());
        assertEquals(0, displays.stats().viewers());
        var late = displays.nametag(owner, Component.text("retired"));
        assertTrue(late.isClosed());
        scheduler.advanceTicks(4);
        assertEquals(0, displays.stats().handles());
        assertEquals(0, displays.stats().viewers());
        displays.text(new Location(world, 1, 64, 1), Component.text("fresh"));
        scheduler.advanceTicks(4);
        assertEquals(1, frames(viewer).size());
    }

    private net.foliaboard.api.display.NametagProfile profile(String text) {
        return net.foliaboard.api.display.NametagProfile.builder().layout(
                net.foliaboard.api.display.NametagLayout.builder().text("line", Component.text(text), 0.25).build()).build();
    }

    @Test
    void compositionUpdatesWholeLayoutsAndPreservesElementKeys() {
        var tag = displays.nametag(owner, profile("first"));
        scheduler.advanceTicks(4);
        var old=frames(viewer).getFirst();
        tag.layout(net.foliaboard.api.display.NametagLayout.builder().text("line", Component.text("new"), 0.3)
                .text("second", Component.text("second"), 0.6).item("icon", item(2), 1).build());
        assertFalse(old.accepted().getAsBoolean());
        scheduler.advanceTicks(2);
        assertEquals(3, frames(viewer).size());
        assertEquals(old.id(), frames(viewer).stream().filter(frame -> Component.text("new").equals(frame.text())).findFirst().orElseThrow().id());
        assertEquals(3, tag.diagnose(viewer.getUniqueId()).elements());
        assertEquals(0, frames(owner).size());
    }

    @Test
    void compositionSamplerAndRendererUseIndependentRefreshPeriods() {
        var samples=new java.util.concurrent.atomic.AtomicInteger();
        var renders=new java.util.concurrent.atomic.AtomicInteger();
        var selected=profile("empty").toBuilder().refresh(new net.foliaboard.api.display.NametagRefresh(5, 3)).build();
        var tag=displays.nametag(owner, selected, entity -> { assertEquals(owner, entity); return samples.incrementAndGet(); },
                (player, data, current) -> { assertEquals(viewer, player); renders.incrementAndGet(); return current.layout(); });
        scheduler.advanceTicks(12);
        assertEquals(3, samples.get());
        assertTrue(renders.get() >= 3 && renders.get() <= 5);
        assertEquals(3, tag.snapshot().orElseThrow());
        tag.refreshData(); scheduler.advanceTicks(1);
        assertEquals(4, samples.get());
    }

    @Test
    void permanentOwnerLockOverridesProfilesLayersRefreshAndFixedPlacement() {
        var ownVisible=profile("visible").toBuilder().visibility(DisplayVisibility.builder().selfVisible(true).build()).build();
        var tag=displays.nametag(owner, ownVisible);
        scheduler.advanceTicks(4); assertEquals(1, frames(owner).size());
        var old=frames(owner).getFirst();
        tag.lockOwnerHidden(); assertFalse(old.accepted().getAsBoolean());
        tag.profile(ownVisible); tag.layer(ownVisible, 1, 0); tag.show(owner.getUniqueId()); tag.refresh();
        scheduler.advanceTicks(4); assertTrue(frames(owner).isEmpty());
        tag.location(new Location(world, 1, 64, 1)); scheduler.advanceTicks(2);
        assertTrue(frames(owner).isEmpty()); assertEquals(1, frames(viewer).size());
        assertTrue(tag.isOwnerHiddenLocked());
    }

    @Test
    void layersUsePriorityNewestTieAndExpiryWithoutLosingBaseUpdates() {
        var tag=displays.nametag(owner, profile("base"));
        var low=tag.layer(profile("low"), 1, 0);
        var high=tag.layer(profile("high"), 2, 5);
        var tie=tag.layer(profile("tie"), 2, 0);
        scheduler.advanceTicks(3); assertEquals(Component.text("tie"), frames(viewer).getFirst().text());
        tag.profile(profile("changed")); tie.close(); scheduler.advanceTicks(1);
        assertEquals(Component.text("high"), frames(viewer).getFirst().text());
        scheduler.advanceTicks(2); assertTrue(high.isClosed()); assertEquals(Component.text("low"), frames(viewer).getFirst().text());
        low.close(); scheduler.advanceTicks(2); assertEquals(Component.text("changed"), frames(viewer).getFirst().text());
        low.close();
    }

    @Test
    void persistentDefinitionsRebindFreshPlayerAndKeepExclusions() {
        var tag=displays.nametag(owner, profile("persistent"), entity -> System.identityHashCode(entity),
                (player, data, selected) -> selected.layout());
        tag.persistent(true); tag.hide(viewer.getUniqueId());
        scheduler.advanceTicks(4); displays.onQuit(owner);
        assertFalse(tag.isClosed()); assertTrue(tag.snapshot().isEmpty());
        Player fresh=player(owner.getUniqueId()); when(fresh.getTrackedBy()).thenReturn(Set.of(viewer));
        displays.onJoin(fresh); displays.onQuit(owner); scheduler.advanceTicks(4);
        assertEquals(System.identityHashCode(fresh), tag.snapshot().orElseThrow());
        assertTrue(frames(viewer).isEmpty()); tag.show(viewer.getUniqueId()); scheduler.advanceTicks(2);
        assertEquals(1, frames(viewer).size()); tag.close(); assertEquals(0, displays.stats().handles());
    }

    @Test
    void compositionDistanceBandsFadeAndScaleAreOptional() {
        var layout=net.foliaboard.api.display.NametagLayout.builder()
                .element(new net.foliaboard.api.display.NametagLayout.Text("near", Component.text("near"), 0.25,
                        DisplayStyle.defaults(), TextDisplayStyle.defaults(), 0, 10))
                .element(new net.foliaboard.api.display.NametagLayout.Text("far", Component.text("far"), 0.25,
                        DisplayStyle.defaults(), TextDisplayStyle.defaults(), 10, 40)).build();
        var selected=profile("unused").toBuilder().layout(layout)
                .distance(new net.foliaboard.api.display.NametagDistance(10, 30, 1, 0.5f)).build();
        displays.nametag(owner, selected); scheduler.advanceTicks(4);
        assertEquals(Component.text("near"), frames(viewer).getFirst().text());
        when(viewer.getLocation()).thenReturn(new Location(world, 21, 64, 1)); scheduler.advanceTicks(2);
        assertEquals(Component.text("far"), frames(viewer).getFirst().text());
        assertEquals(128, frames(viewer).getFirst().textStyle().opacity());
        assertEquals(0.75f, frames(viewer).getFirst().style().transform().scale().x(), 0.001);
    }

    @Test
    void briefSuppressionRebuildsLayoutBeforeLongViewerRefreshDeadline() {
        var renders = new java.util.concurrent.atomic.AtomicInteger();
        var selected = profile("visible").toBuilder()
                .refresh(new net.foliaboard.api.display.NametagRefresh(1, 1000)).build();
        displays.nametag(owner, selected, entity -> Boolean.TRUE, (player, data, active) -> {
            renders.incrementAndGet();
            return active.layout();
        });
        scheduler.advanceTicks(4);
        assertEquals(1, renders.get());
        for (String suppression : List.of("tracking", "range", "invisibility")) {
            switch (suppression) {
                case "tracking" -> when(owner.getTrackedBy()).thenReturn(Set.of());
                case "range" -> when(viewer.getLocation()).thenReturn(new Location(world, 100, 64, 1));
                case "invisibility" -> when(owner.isInvisible()).thenReturn(true);
            }
            scheduler.advanceTicks(2);
            assertTrue(frames(viewer).isEmpty(), suppression);
            int before = renders.get();
            when(owner.getTrackedBy()).thenReturn(Set.of(viewer));
            when(viewer.getLocation()).thenReturn(new Location(world, 1, 64, 1));
            when(owner.isInvisible()).thenReturn(false);
            scheduler.advanceTicks(2);
            assertEquals(1, frames(viewer).size(), suppression);
            assertEquals(before + 1, renders.get(), suppression);
        }
    }

    @Test
    void compositionFadesGlobalVisibilityButExclusionsRemoveImmediately() {
        var selected=profile("fade").toBuilder().transition(new net.foliaboard.api.display.NametagTransition(4, 4)).build();
        var tag=displays.nametag(owner, selected); scheduler.advanceTicks(6);
        assertEquals(255, frames(viewer).getFirst().textStyle().opacity());
        tag.visible(false); scheduler.advanceTicks(1);
        assertTrue(frames(viewer).getFirst().textStyle().opacity() < 255);
        tag.hide(viewer.getUniqueId()); scheduler.advanceTicks(1); assertTrue(frames(viewer).isEmpty());
        assertEquals(net.foliaboard.api.display.NametagStatus.Reason.EXCLUDED, tag.diagnose(viewer.getUniqueId()).reason());
        tag.show(viewer.getUniqueId()); scheduler.advanceTicks(5); assertTrue(frames(viewer).isEmpty());
    }

    @Test
    void entityAttachmentsUseEntityTrackingAndRetirement() {
        var mob=mock(org.bukkit.entity.LivingEntity.class);
        when(mob.getUniqueId()).thenReturn(UUID.randomUUID()); when(mob.isValid()).thenReturn(true);
        when(mob.getLocation()).thenReturn(new Location(world, 1, 64, 1)); when(mob.getEntityId()).thenReturn(20);
        when(mob.getTrackedBy()).thenReturn(Set.of(viewer)); when(mob.getPassengers()).thenReturn(List.of());
        when(viewer.canSee(mob)).thenReturn(true);
        var tag=displays.nametag(mob, profile("mob"));
        scheduler.advanceTicks(4); assertEquals(20, frames(viewer).getFirst().vehicle());
        when(mob.isDead()).thenReturn(true); scheduler.advanceTicks(2);
        assertTrue(tag.isClosed()); assertEquals(0, displays.stats().handles());
    }

    @Test
    void profileRegistryEventsFailuresAndVanillaLeasesRemainOptional() {
        var registry=displays.profiles(); var selected=profile("profile");
        registry.register("custom", selected); assertEquals(Set.of("custom"), registry.names());
        assertEquals(selected, registry.find("custom").orElseThrow()); registry.remove("custom"); assertTrue(registry.find("custom").isEmpty());
        var events=new java.util.ArrayList<net.foliaboard.api.display.NametagEvent>();
        var leases=new java.util.concurrent.atomic.AtomicInteger();
        displays.vanillaLeases(player -> { leases.incrementAndGet(); return () -> leases.decrementAndGet(); });
        var tag=displays.nametag(owner, selected); tag.listener(events::add);
        assertEquals(0, leases.get()); tag.replaceVanillaName(true); assertEquals(1, leases.get());
        scheduler.advanceTicks(4); assertTrue(events.stream().anyMatch(event -> event.type()==net.foliaboard.api.display.NametagEvent.Type.PRESENTED));
        tag.renderer((player, data, current) -> { throw new IllegalStateException("renderer unavailable"); });
        scheduler.advanceTicks(2); assertEquals(net.foliaboard.api.display.NametagStatus.Reason.FAILURE, tag.diagnose(viewer.getUniqueId()).reason());
        tag.close(); assertEquals(0, leases.get());
        assertTrue(events.stream().anyMatch(event -> event.type()==net.foliaboard.api.display.NametagEvent.Type.CLOSED));
    }

    private static ItemStack item(int amount) {
        ItemStack item = mock(ItemStack.class);
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(amount);
        when(item.getAmount()).thenAnswer(invocation -> count.get());
        when(item.clone()).thenAnswer(invocation -> item(count.get()));
        org.mockito.Mockito.doAnswer(invocation -> {
            count.set(invocation.getArgument(0));
            return null;
        }).when(item).setAmount(org.mockito.ArgumentMatchers.anyInt());
        return item;
    }

    private static final class FakeTransport implements DisplayTransport {
        private final Map<UUID, FakeConnection> connections = new HashMap<>();
        private float correction;

        @Override
        public boolean supported() {
            return true;
        }

        @Override
        public Connection connect(Player viewer) {
            FakeConnection value = new FakeConnection();
            connections.put(viewer.getUniqueId(), value);
            return value;
        }

        @Override
        public float mountCorrection(org.bukkit.entity.Entity player) {
            return correction;
        }
    }

    private static final class FakeConnection implements DisplayTransport.Connection {
        private List<DisplayFrame> frames = List.of();
        private boolean closed;

        @Override
        public void present(List<DisplayFrame> frames) {
            this.frames = List.copyOf(frames);
        }

        @Override
        public void close() {
            closed = true;
            frames = List.of();
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public int entities() {
            return (int) frames.stream().filter(frame -> frame.accepted().getAsBoolean()).count();
        }
    }
}
