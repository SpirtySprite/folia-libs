package net.folianpc.internal;

import net.folianpc.api.ClickType;
import net.folianpc.api.Npc;
import net.folianpc.api.NpcAction;
import net.folianpc.api.NpcClickContext;
import net.folianpc.api.NpcAppearance;
import net.folianpc.api.NametagStyle;
import net.folianpc.api.NpcData;
import net.folianpc.api.NpcPose;
import net.folianpc.api.event.NpcInteractEvent;
import net.folianpc.api.event.NpcRemoveEvent;
import net.folianpc.api.event.NpcSpawnEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.Event;
import net.folianpc.api.Skin;
import net.folianpc.internal.scheduler.Schedulers;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NpcManagerTest {

    private RecordingProtocolBackend backend;
    private PlayerTracker tracker;
    private NpcManager manager;
    private List<Event> events;

    @BeforeEach
    void setUp() {
        Schedulers.setSynchronousForTesting(true);
        backend = new RecordingProtocolBackend();
        tracker = new PlayerTracker();
        events = new ArrayList<>();
        manager = new NpcManager(mock(Plugin.class), backend, tracker);
        manager.events(events::add);
    }

    @SuppressWarnings("unchecked")
    private <T extends Event> T firstEvent(Class<T> type) {
        return (T) events.stream().filter(type::isInstance).findFirst().orElse(null);
    }

    @AfterEach
    void tearDown() {
        Schedulers.setSynchronousForTesting(false);
    }

    private Player player(UUID id) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(id);
        return p;
    }

    private void track(Player p, String world, double x, double y, double z) {
        tracker.put(new PlayerTracker.Tracked(p, world, x, y, z));
    }

    @Test
    void rejectedConditionalNavigationPreservesNewerMovementAndCoherentPosition() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        npc.walkTo(new Location(world, 8, 64, 0), 4);
        var rejected = npc.navigateTo(new Location(world, 2, 64, 0), 4,
                net.folianpc.api.NavigationOptions.defaults(), () -> false);
        assertEquals(net.folianpc.api.MovementResult.Status.CANCELLED, rejected.result().join().status());
        assertTrue(npc.moving());
        assertEquals(new net.folianpc.api.NpcPosition("world", 0, 64, 0), npc.positionSnapshot());
        World other = mock(World.class);
        when(other.getName()).thenReturn("other");
        npc.teleport(new Location(other, 3, 70, 9));
        assertEquals(new net.folianpc.api.NpcPosition("other", 3, 70, 9), npc.positionSnapshot());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> npc.navigateTo(new Location(world, Double.NaN, 64, 0), 4,
                        net.folianpc.api.NavigationOptions.defaults(), () -> false));
    }

    @Test
    void showsPlayerInRange() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID id = UUID.randomUUID();
        Player p = player(id);
        track(p, "world", 0, 64, 10);

        manager.tick();

        assertEquals(1, backend.shows.size());
        assertSame(p, backend.shows.get(0).viewer());
        assertEquals("Bob", backend.shows.get(0).npc().name());
        assertTrue(npc.viewers().contains(id));
    }

    @Test
    void doesNotShowTwice() {
        manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);

        manager.tick();
        manager.tick();

        assertEquals(1, backend.shows.size());
    }

    @Test
    void doesNotShowAcrossWorlds() {
        manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "nether", 0, 64, 1);

        manager.tick();

        assertTrue(backend.shows.isEmpty());
    }

    @Test
    void doesNotShowBeyondViewDistance() {
        manager.viewDistance(48);
        manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 200);

        manager.tick();

        assertTrue(backend.shows.isEmpty());
    }

    @Test
    void hidesWhenPlayerLeavesRange() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID id = UUID.randomUUID();
        Player p = player(id);
        track(p, "world", 0, 64, 5);
        manager.tick();
        assertEquals(1, backend.shows.size());

        track(p, "world", 0, 64, 500);
        manager.tick();

        assertEquals(1, backend.hides.size());
        assertFalse(npc.viewers().contains(id));
    }

    @Test
    void lookAtEmitsRotationTowardViewer() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.lookAtPlayers(true);
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 10);

        manager.tick();

        assertFalse(backend.looks.isEmpty());
        RecordingProtocolBackend.Look look = backend.looks.get(backend.looks.size() - 1);
        assertEquals(npc.entityId(), look.entityId());
        assertEquals(0f, look.yaw(), 0.01f);
    }

    @Test
    void lookIsOnlySentWhenTheRotationActuallyChanges() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.lookAtPlayers(true);
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 10);

        manager.tick();
        assertEquals(1, backend.looks.size());

        manager.tick();
        manager.tick();

        assertEquals(1, backend.looks.size(), "a still player must not cost packets every pass");

        track(p, "world", 10, 64, 0);
        manager.tick();

        assertEquals(2, backend.looks.size());
    }

    @Test
    void despawningClearsTheCachedRotationSoItIsResentOnReturn() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.lookAtPlayers(true);
        UUID id = UUID.randomUUID();
        Player p = player(id);
        track(p, "world", 0, 64, 10);
        manager.tick();
        assertEquals(1, backend.looks.size());

        track(p, "world", 0, 64, 900);
        manager.tick();
        track(p, "world", 0, 64, 10);
        manager.tick();

        assertEquals(2, backend.looks.size(), "the client reset on despawn, so it must be re-sent");
    }

    @Test
    void forgettingAPlayerMakesTheNextPassRespawnTheNpc() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID id = UUID.randomUUID();
        Player p = player(id);
        track(p, "world", 0, 64, 5);
        manager.tick();
        assertEquals(1, backend.shows.size());

        manager.forgetPlayer(id);
        manager.tick();

        assertEquals(2, backend.shows.size(), "the NPC must be sent again after a respawn");
    }

    @Test
    void noLookWhenDisabled() {
        manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 10);

        manager.tick();

        assertTrue(backend.looks.isEmpty());
    }

    @Test
    void walkingFacesTravelDirectionEvenWithLookAtPlayersOn() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.lookAtPlayers(true);
        Player p = player(UUID.randomUUID());
        track(p, "world", -10, 64, 0);
        manager.tick();
        backend.looks.clear();

        npc.walkToward(10, 64, 0, 4.0);
        manager.tick();

        assertFalse(backend.looks.isEmpty(), "a step must send a look packet");
        RecordingProtocolBackend.Look look = backend.looks.get(backend.looks.size() - 1);
        assertEquals(-90f, look.yaw(), 0.5f, "must face the travel direction (east), not the player behind it");
    }

    @Test
    void lookAtPlayersResumesTheInstantItStopsMoving() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.lookAtPlayers(true);
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 10);
        npc.walkToward(0.2, 64, 0, 4.0);
        manager.tick();

        assertFalse(npc.moving(), "must have arrived");
        RecordingProtocolBackend.Look look = backend.looks.get(backend.looks.size() - 1);
        assertEquals(0f, look.yaw(), 2.0f, "must face the player now that it has stopped");
    }

    @Test
    void clickRoutesToListener() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        AtomicReference<Npc> clicked = new AtomicReference<>();
        AtomicReference<ClickType> clickType = new AtomicReference<>();
        Player p = player(UUID.randomUUID());
        npc.onClick((who, n, type) -> {
            clicked.set(n);
            clickType.set(type);
        });

        track(p, npc.world(), npc.x(), npc.y(), npc.z());
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);

        assertSame(npc, clicked.get());
        assertEquals(ClickType.RIGHT, clickType.get());
    }

    @Test
    void removeHidesFromViewersAndUnregisters() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID npcId = npc.id();
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();

        npc.remove();

        assertEquals(1, backend.hides.size());
        assertTrue(npc.removed());
        assertNull(manager.get(npcId));
    }

    @Test
    void recreatingFromSavedDataKeepsTheSameId() {
        UUID saved = UUID.randomUUID();

        NpcImpl npc = manager.create(saved, "Bob", EntityType.PLAYER,
                new Position("world", 1, 64, 2, 30, 10));

        assertEquals(saved, npc.id());
        assertSame(npc, manager.get(saved));
    }

    @Test
    void nametagLinesStackAboveTheNpcWithFirstLineOnTop() {
        NpcImpl npc = manager.create("Bob", new Position("world", 5, 64, 7, 0, 0));

        npc.nametag(List.of("top", "bottom"));

        var lines = npc.snapshot().hologram();
        assertEquals(2, lines.size());
        assertEquals("top", lines.get(0).text());
        assertEquals("bottom", lines.get(1).text());
        assertTrue(lines.get(0).y() > lines.get(1).y(), "first line sits highest");
        assertTrue(lines.get(1).y() > 64, "lines float above the NPC's feet");
        assertEquals(5, lines.get(0).x());
        assertEquals(7, lines.get(0).z());
    }

    @Test
    void nametagLinesGetDistinctEntityIdsSeparateFromTheNpc() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));

        npc.nametag(List.of("a", "b"));

        var lines = npc.snapshot().hologram();
        assertNotEquals(lines.get(0).entityId(), lines.get(1).entityId());
        assertNotEquals(npc.entityId(), lines.get(0).entityId());
    }

    @Test
    void changingTheNametagResendsTheNpcToViewers() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        int showsBefore = backend.shows.size();

        npc.nametag(List.of("hello"));

        assertEquals(showsBefore + 1, backend.shows.size(), "the profile changed, so it is re-sent");
        var resent = backend.shows.get(backend.shows.size() - 1).npc();
        assertEquals("hello", resent.hologram().get(0).text());
        assertFalse(resent.nametagVisible());
    }

    @Test
    void hidingAlsoRemovesTheNametagEntities() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.nametag(List.of("a", "b"));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();

        track(p, "world", 0, 64, 900);
        manager.tick();

        assertEquals(1, backend.removed.size());
        assertEquals(2, backend.removed.get(0).length);
    }

    @Test
    void settingANametagHidesTheVanillaNamePlate() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        assertTrue(npc.nametagVisible());

        npc.nametag(List.of("line"));

        assertFalse(npc.nametagVisible());
        assertFalse(npc.snapshot().nametagVisible());
    }

    @Test
    void visibleNamePlateUsesTheDisplayName() {
        NpcImpl npc = manager.create("Steve", new Position("world", 0, 64, 0, 0, 0));

        assertTrue(npc.nametagVisible());
        assertEquals("Steve", npc.profileName(), "a visible plate must read as the display name");
    }

    @Test
    void hiddenNamePlateUsesAUniqueNameSoTeamHidingCannotHitRealPlayers() {
        NpcImpl a = manager.create("Steve", new Position("world", 0, 64, 0, 0, 0));
        NpcImpl b = manager.create("Steve", new Position("world", 1, 64, 0, 0, 0));

        a.nametagVisible(false);
        b.nametagVisible(false);

        assertNotEquals("Steve", a.profileName(), "must not team a real player's name");
        assertNotEquals(a.profileName(), b.profileName());
        assertTrue(a.profileName().length() <= 16, "profile names are limited to 16 chars");
    }

    @Test
    void longDisplayNamesAreTruncatedToAValidProfileName() {
        NpcImpl npc = manager.create("AVeryLongNpcNameIndeed", new Position("world", 0, 64, 0, 0, 0));

        assertEquals(16, npc.profileName().length());
    }

    @Test
    void clearingTheNametagRestoresTheVanillaNamePlate() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.nametag(List.of("line"));

        npc.nametag(List.of());

        assertTrue(npc.nametagVisible());
    }

    @Test
    void clearingTheNametagLeavesNoLines() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.nametag(List.of("a"));

        npc.nametag(List.of());

        assertTrue(npc.snapshot().hologram().isEmpty());
        assertTrue(npc.nametag().isEmpty());
    }

    @Test
    void flagChangesPushMetadataToViewersWithoutRespawning() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        int showsBefore = backend.shows.size();

        npc.glowing(true);

        assertEquals(1, backend.metas.size());
        assertTrue(backend.metas.get(0).glowing());
        assertEquals(showsBefore, backend.shows.size(), "flags must not force a respawn");
    }

    @Test
    void flagsDefaultSensiblyAndReachTheSnapshot() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));

        assertFalse(npc.glowing());
        assertFalse(npc.invisible());
        assertTrue(npc.skinLayers(), "skin layers are on by default");
        assertEquals(1.0, npc.scale());

        npc.invisible(true).skinLayers(false);

        assertTrue(npc.snapshot().invisible());
        assertFalse(npc.snapshot().skinLayers());
    }

    @Test
    void scaleIsPushedAndClampedAboveZero() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();

        npc.scale(2.5);

        assertEquals(List.of(2.5), backend.scales);

        npc.scale(-4);

        assertTrue(npc.scale() > 0, "scale must stay positive");
    }

    @Test
    void spawningAndRemovingFireEvents() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));

        assertNotNull(firstEvent(NpcSpawnEvent.class));
        assertSame(npc, firstEvent(NpcSpawnEvent.class).getNpc());

        npc.remove();

        assertNotNull(firstEvent(NpcRemoveEvent.class));
        assertSame(npc, firstEvent(NpcRemoveEvent.class).getNpc());
    }

    @Test
    void interactFiresAnEventCarryingTheClick() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());

        track(p, npc.world(), npc.x(), npc.y(), npc.z());
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);

        NpcInteractEvent event = firstEvent(NpcInteractEvent.class);
        assertNotNull(event);
        assertSame(npc, event.getNpc());
        assertSame(p, event.getPlayer());
        assertEquals(ClickType.RIGHT, event.getClick());
    }

    @Test
    void cancellingTheInteractEventStopsListenerAndActions() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        List<String> ran = new ArrayList<>();
        npc.onClick((who, clicked, type) -> ran.add("listener"));
        npc.addAction(ClickType.RIGHT, ctx -> ran.add("action"));
        manager.events(event -> {
            events.add(event);
            if (event instanceof NpcInteractEvent interact) {
                interact.setCancelled(true);
            }
        });

        track(p, npc.world(), npc.x(), npc.y(), npc.z());
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);

        assertTrue(ran.isEmpty(), "a cancelled interaction must run nothing");
    }

    @Test
    void aThrowingClickListenerDoesNotStopActions() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        List<String> ran = new ArrayList<>();
        npc.onClick((who, clicked, type) -> {
            throw new IllegalStateException("consumer bug");
        });
        npc.addAction(ClickType.RIGHT, ctx -> ran.add("action"));

        track(p, npc.world(), npc.x(), npc.y(), npc.z());
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);

        assertEquals(List.of("action"), ran);
    }

    @Test
    void glowColourAndCollisionForceTheUniqueWireName() {
        NpcImpl npc = manager.create("Steve", new Position("world", 0, 64, 0, 0, 0));
        assertEquals("Steve", npc.profileName());

        npc.glowColor(NamedTextColor.RED);

        assertNotEquals("Steve", npc.profileName(), "a team must never match a real player's name");
        assertTrue(npc.snapshot().needsTeam());

        npc.glowColor(null).collidable(false);

        assertNotEquals("Steve", npc.profileName());
    }

    @Test
    void aPlainNpcNeedsNoTeamAtAll() {
        NpcImpl npc = manager.create("Steve", new Position("world", 0, 64, 0, 0, 0));

        assertFalse(npc.snapshot().needsTeam());
        assertTrue(npc.collidable());
        assertNull(npc.glowColor());
    }

    @Test
    void appearanceRoundTripsThroughData() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.glowing(true).scale(2.0).glowColor(NamedTextColor.AQUA).collidable(false);

        NpcAppearance saved = npc.data().appearance();
        NpcImpl restored = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        restored.appearance(saved);

        assertTrue(restored.glowing());
        assertEquals(2.0, restored.scale());
        assertEquals(NamedTextColor.AQUA, restored.glowColor());
        assertFalse(restored.collidable());
    }

    @Test
    void hiddenPlayersNeverSeeTheNpcEvenPointBlank() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID id = UUID.randomUUID();
        Player p = player(id);
        track(p, "world", 0, 64, 1);

        npc.hideFrom(id);
        manager.tick();

        assertTrue(backend.shows.isEmpty());
    }

    @Test
    void hidingAfterTheFactDespawnsForThatPlayerOnly() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID hidden = UUID.randomUUID();
        Player a = player(hidden);
        Player b = player(UUID.randomUUID());
        track(a, "world", 0, 64, 5);
        track(b, "world", 0, 64, 5);
        manager.tick();
        assertEquals(2, backend.shows.size());

        npc.hideFrom(hidden);
        manager.tick();

        assertEquals(1, backend.hides.size());
        assertSame(a, backend.hides.get(0).viewer());
        assertFalse(npc.viewers().contains(hidden));
    }

    @Test
    void forcedPlayersSeeTheNpcBeyondViewDistance() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID id = UUID.randomUUID();
        Player p = player(id);
        track(p, "world", 0, 64, 5000);

        npc.showTo(id);
        manager.tick();

        assertEquals(1, backend.shows.size());
    }

    @Test
    void aForcedPlayerWhoIsAlsoInRangeIsShownOnce() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID id = UUID.randomUUID();
        track(player(id), "world", 0, 64, 5);

        npc.showTo(id);
        manager.tick();
        manager.tick();

        assertEquals(1, backend.shows.size());
    }

    @Test
    void viewersMatchADirectDistanceCheckForManyNpcsAndPlayers() {
        java.util.Random random = new java.util.Random(7);
        manager.viewDistance(48);
        List<NpcImpl> npcs = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            NpcImpl npc = manager.create("n" + i, new Position("world",
                    (random.nextDouble() - 0.5) * 600, 64, (random.nextDouble() - 0.5) * 600, 0, 0));
            if (i % 5 == 0) {
                npc.viewDistance(10 + random.nextInt(150));
            }
            npcs.add(npc);
        }
        manager.create("elsewhere", new Position("nether", 0, 64, 0, 0, 0));
        List<PlayerTracker.Tracked> players = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            UUID id = UUID.randomUUID();
            String world = i % 10 == 0 ? "nether" : "world";
            PlayerTracker.Tracked t = new PlayerTracker.Tracked(player(id), world,
                    (random.nextDouble() - 0.5) * 600, 50 + random.nextInt(40), (random.nextDouble() - 0.5) * 600);
            tracker.put(t);
            players.add(t);
        }
        // Some players are forced in or out whatever their distance.
        npcs.get(3).showTo(players.get(1).uuid());
        npcs.get(3).hideFrom(players.get(2).uuid());
        npcs.get(4).showTo(players.get(5).uuid());

        manager.tick();

        for (NpcImpl npc : npcs) {
            double range = npc.viewDistance() > 0 ? npc.viewDistance() : 48;
            Set<UUID> expected = new java.util.HashSet<>();
            for (PlayerTracker.Tracked t : players) {
                if (!t.world().equals(npc.position().world())) {
                    continue;
                }
                boolean inRange = npc.position().distanceSquared(t.x(), t.y(), t.z()) <= range * range;
                if (npc.visibleTo(t.player(), inRange)) {
                    expected.add(t.uuid());
                }
            }
            assertEquals(expected, new java.util.HashSet<>(npc.viewers()), "viewers of " + npc.name());
        }
    }

    @Test
    void forcingDoesNotCrossWorlds() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID id = UUID.randomUUID();
        Player p = player(id);
        track(p, "nether", 0, 64, 1);

        npc.showTo(id);
        manager.tick();

        assertTrue(backend.shows.isEmpty());
    }

    @Test
    void resettingVisibilityRestoresTheDistanceCheck() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID id = UUID.randomUUID();
        Player p = player(id);
        track(p, "world", 0, 64, 5);
        npc.hideFrom(id);
        manager.tick();
        assertTrue(backend.shows.isEmpty());

        npc.resetVisibility(id);
        manager.tick();

        assertEquals(1, backend.shows.size());
    }

    @Test
    void teleportInSameWorldResendsToViewersInRange() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        int showsBefore = backend.shows.size();

        npc.teleportTo(new Position("world", 3, 64, 3, 0, 0));

        assertEquals(3, npc.x());
        assertEquals(showsBefore + 1, backend.shows.size(), "the NPC is re-sent at the new spot");
    }

    @Test
    void teleportToAnotherWorldDespawnsForOldViewers() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID id = UUID.randomUUID();
        Player p = player(id);
        track(p, "world", 0, 64, 5);
        manager.tick();

        npc.teleportTo(new Position("nether", 0, 64, 0, 0, 0));

        assertEquals(1, backend.hides.size());
        assertFalse(npc.viewers().contains(id), "a cross-world teleport drops old viewers");
    }

    @Test
    void walkingAdvancesThePositionAndSlidesForViewers() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();

        npc.walkToward(10, 64, 0, 4.0);
        manager.tick();

        assertTrue(npc.moving());
        assertEquals(0.4, npc.x(), 1e-6, "moved one step toward the target");
        assertFalse(backend.moves.isEmpty());
        assertEquals(npc.entityId(), backend.moves.get(0).entityId());
    }

    @Test
    void walkingStopsOnArrival() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();

        npc.walkToward(0.2, 64, 0, 4.0);
        manager.tick();

        assertFalse(npc.moving(), "arrived, so no longer walking");
        assertEquals(0.2, npc.x(), 1e-6);
    }

    private void snapshots(World world, boolean sealed) {
        org.bukkit.Chunk chunk = mock(org.bukkit.Chunk.class);
        org.bukkit.ChunkSnapshot snapshot = mock(org.bukkit.ChunkSnapshot.class);
        org.bukkit.Material solid = mock(org.bukkit.Material.class);
        org.bukkit.Material air = mock(org.bukkit.Material.class);
        when(solid.isSolid()).thenReturn(true);
        when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
                sealed || (int) inv.getArgument(1) <= 0 ? solid : air);
        when(chunk.getChunkSnapshot(false, false, false)).thenReturn(snapshot);
        when(world.getChunkAtAsync(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(CompletableFuture.completedFuture(chunk));
    }

    private World flatWorld(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        Block solid = mock(Block.class);
        when(solid.isSolid()).thenReturn(true);
        Block air = mock(Block.class);
        when(air.isSolid()).thenReturn(false);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
                ((int) inv.getArgument(1)) <= 0 ? solid : air);
        snapshots(world, false);
        return world;
    }

    private World sealedWorld(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        Block solid = mock(Block.class);
        when(solid.isSolid()).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(solid);
        snapshots(world, true);
        return world;
    }

    @Test
    void navigateToOnOpenGroundFindsARouteAndStartsWalking() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 1, 0, 0, 0));
        World world = flatWorld("world");

        CompletableFuture<Boolean> result = npc.navigateTo(new Location(world, 5, 1, 0), 4.0);

        assertTrue(result.isDone());
        assertTrue(result.join(), "a direct route across open ground must be found");
        assertTrue(npc.moving());
    }

    @Test
    void navigateToReturnsFalseWhenNoRouteExists() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 1, 0, 0, 0));
        World world = sealedWorld("world");

        CompletableFuture<Boolean> result = npc.navigateTo(new Location(world, 5, 1, 0), 4.0);

        assertFalse(result.join());
        assertFalse(npc.moving(), "a failed search must not start the NPC walking");
    }

    @Test
    void navigateToADifferentWorldFailsWithoutSearching() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 1, 0, 0, 0));
        World nether = flatWorld("nether");

        CompletableFuture<Boolean> result = npc.navigateTo(new Location(nether, 5, 1, 0), 4.0);

        assertFalse(result.join(), "cross-world routes are not supported");
        assertFalse(npc.moving());
    }

    @Test
    void stopWalkingCancelsAnInProgressRoute() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 1, 0, 0, 0));
        World world = flatWorld("world");
        npc.navigateTo(new Location(world, 5, 1, 0), 4.0).join();
        assertTrue(npc.moving());

        npc.stopWalking();

        assertFalse(npc.moving());
    }

    @Test
    void interactCarriesTheSneakingFlag() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());

        track(p, npc.world(), npc.x(), npc.y(), npc.z());
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT, true);

        assertTrue(firstEvent(NpcInteractEvent.class).isSneaking());
    }

    @Test
    void autoRefreshResendsTheNametagOnItsInterval() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.nametag(List.of("hi"));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        int before = backend.hologramRefreshes.size();

        npc.autoRefreshNametag(4);
        manager.tick();
        assertEquals(before, backend.hologramRefreshes.size(), "not yet due");
        manager.tick();
        assertEquals(before + 1, backend.hologramRefreshes.size(), "due on the second pass");
    }

    @Test
    void autoRefreshOffByDefault() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.nametag(List.of("hi"));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        int before = backend.hologramRefreshes.size();

        manager.tick();
        manager.tick();

        assertEquals(before, backend.hologramRefreshes.size());
    }

    @Test
    void statsCountViewerShows() {
        NpcImpl a = manager.create("A", new Position("world", 0, 64, 0, 0, 0));
        NpcImpl b = manager.create("B", new Position("world", 0, 64, 0, 0, 0));
        track(player(UUID.randomUUID()), "world", 0, 64, 5);
        track(player(UUID.randomUUID()), "world", 0, 64, 5);
        manager.tick();

        assertEquals(2, manager.count());
        assertEquals(4, manager.viewerShows(), "2 NPCs x 2 viewers");
    }

    @Test
    void swingSendsAnAnimationToEveryViewer() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player a = player(UUID.randomUUID());
        Player b = player(UUID.randomUUID());
        track(a, "world", 0, 64, 5);
        track(b, "world", 0, 64, 5);
        manager.tick();

        npc.swing();

        assertEquals(2, backend.animations.size());
        assertEquals(0, backend.animations.get(0), "0 is swing main hand");
        npc.swingOffHand();
        assertEquals(3, backend.animations.get(2), "3 is swing off hand");
    }

    @Test
    void refreshNametagResendsTextWithoutRespawning() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.nametag(List.of("hi %player%"));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        int showsBefore = backend.shows.size();

        npc.refreshNametag();

        assertEquals(1, backend.hologramRefreshes.size());
        assertEquals(showsBefore, backend.shows.size(), "a refresh must not respawn the NPC");
    }

    @Test
    void rawMetadataReachesTheSnapshotAndClears() {
        NpcImpl npc = manager.create(UUID.randomUUID(), "Zed", EntityType.ZOMBIE,
                new Position("world", 0, 64, 0, 0, 0));

        npc.metadata(16, net.folianpc.api.MetadataType.BOOLEAN, true);

        assertEquals(net.folianpc.api.MetadataType.BOOLEAN, npc.snapshot().rawMeta().get(16).type());
        assertEquals(true, npc.snapshot().rawMeta().get(16).value());

        npc.metadata(16, net.folianpc.api.MetadataType.BOOLEAN, null);

        assertTrue(npc.snapshot().rawMeta().isEmpty());
    }

    @Test
    void poseSurvivesIntoSnapshotAndData() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));

        npc.pose(NpcPose.SITTING);

        assertEquals(NpcPose.SITTING, npc.pose());
        assertEquals("SITTING", npc.snapshot().pose());
        assertEquals(NpcPose.SITTING, npc.data().pose());
    }

    @Test
    void quittingDropsVisibilityOverridesButRespawnDoesNot() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID id = UUID.randomUUID();
        Player p = player(id);
        track(p, "world", 0, 64, 5);
        npc.hideFrom(id);

        manager.forgetPlayer(id);
        track(p, "world", 0, 64, 5);
        manager.tick();
        assertTrue(backend.shows.isEmpty(), "respawn keeps a standing hide");

        manager.dropPlayer(id);
        manager.tick();
        assertEquals(1, backend.shows.size(), "quit clears the override");
    }

    @Test
    void renamingResendsAndChangesTheWireName() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        int showsBefore = backend.shows.size();

        npc.name("Alice");

        assertEquals("Alice", npc.name());
        assertEquals("Alice", npc.profileName());
        assertEquals(showsBefore + 1, backend.shows.size());
    }

    @Test
    void perNpcViewDistanceOverridesTheGlobalOne() {
        manager.viewDistance(48);
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.viewDistance(10);
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 20);

        manager.tick();

        assertTrue(backend.shows.isEmpty());

        npc.viewDistance(0);
        manager.tick();

        assertEquals(1, backend.shows.size());
    }

    @Test
    void quittingForgetsPerPlayerState() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID id = UUID.randomUUID();
        Player p = player(id);
        track(p, "world", 0, 64, 5);
        npc.cooldown(10_000);
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);
        assertTrue(npc.viewers().contains(id));

        manager.forgetPlayer(id);

        assertFalse(npc.viewers().contains(id));
        assertTrue(npc.allowInteract(id, 0L), "cooldown state is dropped with the player");
    }

    @Test
    void actionsRunInOrderForTheMatchingClickType() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        List<String> ran = new ArrayList<>();
        npc.addAction(ClickType.RIGHT, ctx -> ran.add("first"));
        npc.addAction(ClickType.RIGHT, ctx -> ran.add("second"));
        npc.addAction(ClickType.LEFT, ctx -> ran.add("left"));

        track(p, npc.world(), npc.x(), npc.y(), npc.z());
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);

        assertEquals(List.of("first", "second"), ran);
    }

    @Test
    void actionsAndTheClickListenerBothRun() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        List<String> ran = new ArrayList<>();
        npc.onClick((who, clicked, type) -> ran.add("listener"));
        npc.addAction(ClickType.LEFT, ctx -> ran.add("action"));

        track(p, npc.world(), npc.x(), npc.y(), npc.z());
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.LEFT);

        assertEquals(List.of("listener", "action"), ran);
    }

    @Test
    void cooldownBlocksRepeatClicksFromTheSamePlayer() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        AtomicLong now = new AtomicLong(1_000L);
        manager.clock(now::get);
        npc.cooldown(500L);
        Player p = player(UUID.randomUUID());
        AtomicInteger runs = new AtomicInteger();
        npc.addAction(ClickType.RIGHT, ctx -> runs.incrementAndGet());

        track(p, npc.world(), npc.x(), npc.y(), npc.z());
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);
        now.set(1_400L);
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);

        assertEquals(1, runs.get(), "clicks inside the cooldown window are ignored");

        now.set(1_600L);
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);

        assertEquals(2, runs.get(), "the click after the window runs");
    }

    @Test
    void cooldownIsPerPlayer() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        manager.clock(() -> 1_000L);
        npc.cooldown(500L);
        AtomicInteger runs = new AtomicInteger();
        npc.addAction(ClickType.RIGHT, ctx -> runs.incrementAndGet());

        Player a = player(UUID.randomUUID());
        Player b = player(UUID.randomUUID());
        track(a, "world", 0, 64, 5);
        track(b, "world", 0, 64, 5);
        manager.tick();
        backend.fireInteract(a, npc.entityId(), ClickType.RIGHT);
        backend.fireInteract(b, npc.entityId(), ClickType.RIGHT);

        assertEquals(2, runs.get(), "one player's cooldown must not block another");
    }

    @Test
    void cancelRemainingStopsLaterActions() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        List<String> ran = new ArrayList<>();
        npc.addAction(ClickType.RIGHT, ctx -> ran.add("first"));
        npc.addAction(ClickType.RIGHT, NpcClickContext::cancelRemaining);
        npc.addAction(ClickType.RIGHT, ctx -> ran.add("never"));

        track(p, npc.world(), npc.x(), npc.y(), npc.z());
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);

        assertEquals(List.of("first"), ran);
    }

    @Test
    void actionsSeeTheClickTypeAndShareContextData() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        AtomicReference<ClickType> seen = new AtomicReference<>();
        npc.addAction(ClickType.LEFT, ctx -> ctx.data().put("hits", 7));
        npc.addAction(ClickType.LEFT, ctx -> {
            seen.set(ctx.click());
            assertEquals(7, ctx.data().get("hits"));
        });

        track(p, npc.world(), npc.x(), npc.y(), npc.z());
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.LEFT);

        assertEquals(ClickType.LEFT, seen.get());
    }

    @Test
    void whenGatesAnActionAndThenChainsTwo() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        List<String> ran = new ArrayList<>();
        NpcAction blocked = ((NpcAction) ctx -> ran.add("blocked")).when(ctx -> false);
        NpcAction chained = ((NpcAction) ctx -> ran.add("a")).then(ctx -> ran.add("b"));
        npc.addAction(ClickType.RIGHT, blocked);
        npc.addAction(ClickType.RIGHT, chained);

        track(p, npc.world(), npc.x(), npc.y(), npc.z());
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);

        assertEquals(List.of("a", "b"), ran);
    }

    @Test
    void oneThrowingActionDoesNotStopTheRest() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        List<String> ran = new ArrayList<>();
        npc.addAction(ClickType.RIGHT, ctx -> {
            throw new IllegalStateException("developer bug");
        });
        npc.addAction(ClickType.RIGHT, ctx -> ran.add("still runs"));

        track(p, npc.world(), npc.x(), npc.y(), npc.z());
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);

        assertEquals(List.of("still runs"), ran);
    }

    @Test
    void clearingActionsStopsThemRunning() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        AtomicInteger runs = new AtomicInteger();
        npc.addAction(ClickType.RIGHT, ctx -> runs.incrementAndGet());

        npc.clearActions(ClickType.RIGHT);
        track(p, npc.world(), npc.x(), npc.y(), npc.z());
        manager.tick();
        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);

        assertEquals(0, runs.get());
    }

    @Test
    void equipmentChangePushesToViewersWithoutRespawning() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        int showsAfterSpawn = backend.shows.size();

        npc.equipment(EquipmentSlot.HAND, mutableItem(2));

        assertEquals(1, backend.equips.size());
        assertEquals(npc.entityId(), backend.equips.get(0).entityId());
        assertEquals(showsAfterSpawn, backend.shows.size(), "equipment must not force a respawn");
    }

    @Test
    void equipmentStartsEmptyAndClearingIsSafe() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));

        npc.equipment(EquipmentSlot.HEAD, null);

        assertTrue(npc.equipment().isEmpty());
        assertTrue(npc.snapshot().equipment().isEmpty());
        assertTrue(npc.data().equipment().isEmpty());
    }

    @Test
    void mobNpcsKeepTheirTypeThroughSnapshotAndData() {
        NpcImpl npc = manager.create(UUID.randomUUID(), "Zed", EntityType.ZOMBIE,
                new Position("world", 0, 64, 0, 0, 0));

        assertEquals(EntityType.ZOMBIE, npc.type());
        assertEquals(EntityType.ZOMBIE, npc.snapshot().type());
        assertFalse(npc.snapshot().isPlayer());
        assertEquals(EntityType.ZOMBIE, npc.data().type());
    }

    @Test
    void playerNpcsAreStillFlaggedAsPlayers() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));

        assertEquals(EntityType.PLAYER, npc.type());
        assertTrue(npc.snapshot().isPlayer());
    }

    @Test
    void ageableMobsAreFlaggedAgeable() {
        NpcImpl zombie = manager.create(UUID.randomUUID(), "Zed", EntityType.ZOMBIE,
                new Position("world", 0, 64, 0, 0, 0));
        NpcImpl villager = manager.create(UUID.randomUUID(), "Vil", EntityType.VILLAGER,
                new Position("world", 0, 64, 0, 0, 0));

        assertTrue(zombie.snapshot().isAgeable());
        assertTrue(villager.snapshot().isAgeable());
    }

    @Test
    void nonAgeableEntitiesAreNotFlaggedAgeable() {
        NpcImpl player = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        NpcImpl skeleton = manager.create(UUID.randomUUID(), "Bones", EntityType.SKELETON,
                new Position("world", 0, 64, 0, 0, 0));

        assertFalse(player.snapshot().isAgeable());
        assertFalse(skeleton.snapshot().isAgeable());
    }

    @Test
    void babyDefaultsFalseAndPushesMetadataWithoutRespawning() {
        NpcImpl npc = manager.create(UUID.randomUUID(), "Zed", EntityType.ZOMBIE,
                new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        int showsBefore = backend.shows.size();

        assertFalse(npc.baby());

        npc.baby(true);

        assertTrue(npc.baby());
        assertTrue(npc.snapshot().baby());
        assertEquals(1, backend.metas.size());
        assertTrue(backend.metas.get(0).baby());
        assertEquals(showsBefore, backend.shows.size(), "baby state must not force a respawn");
    }

    @Test
    void babyRoundTripsThroughData() {
        NpcImpl npc = manager.create(UUID.randomUUID(), "Zed", EntityType.ZOMBIE,
                new Position("world", 0, 64, 0, 0, 0));

        npc.baby(true);

        assertTrue(npc.data().baby());
    }

    @Test
    void showInTabListDefaultsFalseAndResendsWhenToggled() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        int showsBefore = backend.shows.size();

        assertFalse(npc.showInTabList());

        npc.showInTabList(true);

        assertTrue(npc.showInTabList());
        assertTrue(npc.snapshot().showInTabList());
        assertEquals(showsBefore + 1, backend.shows.size(), "the tab-list entry is part of the wire profile");
    }

    @Test
    void showInTabListRoundTripsThroughData() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));

        npc.showInTabList(true);

        assertTrue(npc.data().showInTabList());
    }

    @Test
    void mobVariantDefaultsToZeroWithNoNameSet() {
        NpcImpl npc = manager.create(UUID.randomUUID(), "Fluffy", EntityType.CAT,
                new Position("world", 0, 64, 0, 0, 0));

        assertEquals(0, npc.variant());
        assertNull(npc.variantName());
        assertNull(npc.villagerProfession());
        assertNull(npc.villagerType());
        assertEquals(1, npc.villagerLevel());
    }

    @Test
    void intAndNamedVariantsAreIndependent() {
        NpcImpl npc = manager.create(UUID.randomUUID(), "Rex", EntityType.RABBIT,
                new Position("world", 0, 64, 0, 0, 0));

        npc.variant(3);
        npc.variant("black");

        assertEquals(3, npc.variant(), "setting the named variant must not clear the int one");
        assertEquals("black", npc.variantName());
    }

    @Test
    void mobVariantPushesMetadataWithoutRespawning() {
        NpcImpl npc = manager.create(UUID.randomUUID(), "Rex", EntityType.RABBIT,
                new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        int showsBefore = backend.shows.size();

        npc.variant(2);

        assertEquals(1, backend.metas.size());
        assertEquals(2, backend.metas.get(0).mobVariant().variant());
        assertEquals(showsBefore, backend.shows.size(), "mob variant must not force a respawn");
    }

    @Test
    void villagerFieldsRoundTripThroughData() {
        NpcImpl npc = manager.create(UUID.randomUUID(), "Merchant", EntityType.VILLAGER,
                new Position("world", 0, 64, 0, 0, 0));

        npc.villagerProfession("farmer").villagerType("plains").villagerLevel(4);

        assertEquals("farmer", npc.data().mobVariant().villagerProfession());
        assertEquals("plains", npc.data().mobVariant().villagerType());
        assertEquals(4, npc.data().mobVariant().villagerLevel());
    }

    @Test
    void mobVariantBulkSetterReplacesEverythingAtOnce() {
        NpcImpl npc = manager.create(UUID.randomUUID(), "Merchant", EntityType.VILLAGER,
                new Position("world", 0, 64, 0, 0, 0));
        npc.villagerProfession("librarian");

        npc.mobVariant(new net.folianpc.api.MobVariant(0, null, "farmer", "desert", 2));

        assertEquals("farmer", npc.villagerProfession());
        assertEquals("desert", npc.villagerType());
        assertEquals(2, npc.villagerLevel());
    }

    @Test
    void copyPropagatesMobVariant() {
        NpcImpl original = manager.create(UUID.randomUUID(), "Merchant", EntityType.VILLAGER,
                new Position("world", 0, 64, 0, 0, 0));
        original.villagerProfession("librarian").villagerType("taiga").villagerLevel(5);

        Npc copy = original.copy(new org.bukkit.Location(null, 1, 64, 1));

        assertEquals("librarian", copy.villagerProfession());
        assertEquals("taiga", copy.villagerType());
        assertEquals(5, copy.villagerLevel());
    }

    @Test
    void changingTypeResendsTheNpcAsTheNewEntity() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        int showsBefore = backend.shows.size();

        npc.type(EntityType.ZOMBIE);

        assertEquals(EntityType.ZOMBIE, npc.type());
        assertEquals(EntityType.ZOMBIE, npc.snapshot().type());
        assertEquals(showsBefore + 1, backend.shows.size(), "the spawn packet carries the type, so it must be re-sent");
    }

    @Test
    void changingTypeToNullFallsBackToPlayer() {
        NpcImpl npc = manager.create(UUID.randomUUID(), "Zed", EntityType.ZOMBIE,
                new Position("world", 0, 64, 0, 0, 0));

        npc.type(null);

        assertEquals(EntityType.PLAYER, npc.type());
    }

    @Test
    void copyDuplicatesConfigurationAtANewLocationWithItsOwnId() {
        NpcImpl original = manager.create("Shopkeeper", new Position("world", 0, 64, 0, 0, 0));
        original.glowing(true).scale(1.5).cooldown(2000);
        original.nametag(List.of("hi"));
        original.baby(true).showInTabList(true);
        List<String> ran = new ArrayList<>();
        original.addAction(ClickType.RIGHT, ctx -> ran.add("original-action"));

        Npc copy = original.copy(new org.bukkit.Location(null, 10, 65, 10));

        assertNotEquals(original.id(), copy.id());
        assertEquals(original.name(), copy.name());
        assertEquals(10.0, copy.x());
        assertEquals(65.0, copy.y());
        assertEquals(10.0, copy.z());
        assertTrue(copy.glowing());
        assertEquals(1.5, copy.scale());
        assertEquals(2000L, copy.cooldown());
        assertEquals(List.of("hi"), copy.nametag());
        assertTrue(copy.baby());
        assertTrue(copy.showInTabList());

        Player p = player(UUID.randomUUID());
        track(p, copy.world(), copy.x(), copy.y(), copy.z());
        manager.tick();
        backend.fireInteract(p, ((NpcImpl) copy).entityId(), ClickType.RIGHT);
        assertEquals(List.of("original-action"), ran, "the copy keeps the original's actions");
    }

    @Test
    void ownerRoundTripsThroughData() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        UUID creator = UUID.randomUUID();

        npc.owner(creator);

        assertEquals(creator, npc.owner());
        assertEquals(creator, npc.data().owner());
    }

    @Test
    void distantTrackedClicksAreIgnoredButActionsStillRunUpClose() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player near = player(UUID.randomUUID());
        Player far = player(UUID.randomUUID());
        track(near, "world", 0, 64, 5);
        track(far, "world", 0, 64, 500);
        AtomicInteger runs = new AtomicInteger();
        npc.addAction(ClickType.RIGHT, ctx -> runs.incrementAndGet());

        manager.tick();
        backend.fireInteract(far, npc.entityId(), ClickType.RIGHT);
        assertEquals(0, runs.get(), "a forged click from a known-distant tracked position must not run");

        backend.fireInteract(near, npc.entityId(), ClickType.RIGHT);
        assertEquals(1, runs.get(), "a click from a tracked nearby position runs normally");
    }

    @Test
    void untrackedClicksAreRejected() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        AtomicInteger runs = new AtomicInteger();
        npc.addAction(ClickType.RIGHT, ctx -> runs.incrementAndGet());

        backend.fireInteract(p, npc.entityId(), ClickType.RIGHT);

        assertEquals(0, runs.get());
    }

    @Test
    void dataSnapshotCarriesEverythingNeededToRespawn() {
        NpcImpl npc = manager.create("Bob", new Position("world", 1.5, 64, 2.5, 30, 10));
        npc.lookAtPlayers(true);
        npc.skin(new Skin("value", "signature"));

        NpcData data = npc.data();

        assertEquals(npc.id(), data.id());
        assertEquals("Bob", data.name());
        assertEquals("world", data.world());
        assertEquals(1.5, data.x());
        assertEquals(2.5, data.z());
        assertEquals(30f, data.yaw());
        assertTrue(data.lookAtPlayers());
        assertEquals("value", data.skin().value());
    }

    @Test
    void changingTheNametagStyleOnlyResendsTheLines() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.nametag(List.of("hello", "world"));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        int showsBefore = backend.shows.size();
        int hidesBefore = backend.hides.size();
        int refreshesBefore = backend.hologramRefreshes.size();

        NametagStyle style = NametagStyle.transparent().withTextOpacity(128).withShadow(true);
        npc.nametagStyle(style);

        assertEquals(showsBefore, backend.shows.size(), "no respawn for a style change");
        assertEquals(hidesBefore, backend.hides.size());
        assertEquals(refreshesBefore + 1, backend.hologramRefreshes.size());
        var lines = backend.hologramRefreshes.get(backend.hologramRefreshes.size() - 1).hologram();
        assertEquals(style, lines.get(0).style());
        assertEquals(style, lines.get(1).style());
    }

    @Test
    void settingTheSameNametagStyleSendsNothing() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.nametag(List.of("hello"));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();
        npc.nametagStyle(NametagStyle.transparent());
        int refreshes = backend.hologramRefreshes.size();

        npc.nametagStyle(NametagStyle.transparent());

        assertEquals(refreshes, backend.hologramRefreshes.size());
    }

    @Test
    void nametagStyleWithoutLinesIsStoredButNotSent() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player p = player(UUID.randomUUID());
        track(p, "world", 0, 64, 5);
        manager.tick();

        npc.nametagStyle(NametagStyle.transparent());

        assertTrue(backend.hologramRefreshes.isEmpty());
        assertEquals(NametagStyle.transparent(), npc.nametagStyle());
        npc.nametag(List.of("later"));
        assertEquals(NametagStyle.transparent(), npc.snapshot().hologram().get(0).style());
    }

    @Test
    void nullNametagStyleFallsBackToDefaults() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.nametagStyle(NametagStyle.transparent());

        npc.nametagStyle(null);

        assertEquals(NametagStyle.defaults(), npc.nametagStyle());
    }

    @Test
    void nametagStyleSurvivesCopyAndData() {
        NpcImpl original = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        NametagStyle style = NametagStyle.defaults().withBackground(0x220044, 90).withSeeThrough(true);
        original.nametagStyle(style);
        original.nametag(List.of("hi"));

        Npc copy = original.copy(new org.bukkit.Location(null, 3, 64, 3));
        NpcData saved = original.data();

        assertEquals(style, copy.nametagStyle());
        assertEquals(style, saved.nametagStyle());
    }

    @Test
    void legacyDataConstructorUsesTheDefaultNametagStyle() {
        NpcData legacy = new NpcData(UUID.randomUUID(), "Bob", EntityType.PLAYER, "world", 0, 64, 0, 0, 0,
                false, null, false, java.util.Map.of(), List.of("hi"), NpcAppearance.defaults(), NpcPose.STANDING,
                false, false, net.folianpc.api.MobVariant.defaults(), null);

        assertEquals(NametagStyle.defaults(), legacy.nametagStyle());
    }
    @Test
    void duplicateIdsAndCreationAfterCloseAreRejected() {
        UUID id = UUID.randomUUID();
        Position position = new Position("world", 0, 1, 0, 0, 0);
        NpcImpl original = manager.create(id, "original", org.bukkit.entity.EntityType.PLAYER, position);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> manager.create(id, "replacement", org.bukkit.entity.EntityType.PLAYER, position));
        assertSame(original, manager.get(id));
        manager.close();
        assertTrue(original.removed());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> original.glowing(true));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> manager.create("late", position));
    }

    @Test
    void snapshotNavigationReportsSetupThenArrival() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0.5, 1, 0.5, 0, 0));
        var task = npc.navigateTo(new Location(flatWorld("world"), 3.5, 1, 0.5), 10,
                net.folianpc.api.NavigationOptions.builder().radius(8).build());
        assertEquals(net.folianpc.api.MovementResult.Status.ROUTE_FOUND, task.route().join().status());
        assertFalse(task.result().isDone());
        for (int pass = 0; pass < 20; pass++) {
            manager.tick();
        }
        assertEquals(net.folianpc.api.MovementResult.Status.ARRIVED, task.result().join().status());
        assertEquals(3.5, npc.x());
    }

    @Test
    void cancellationSupersessionRemovalAndShutdownCompletePendingCapture() {
        for (int ending = 0; ending < 4; ending++) {
            NpcManager local = new NpcManager(mock(Plugin.class), new RecordingProtocolBackend(), new PlayerTracker());
            local.events(event -> { });
            NpcImpl npc = local.create("Bob", new Position("world", 0.5, 1, 0.5, 0, 0));
            World world = flatWorld("world");
            CompletableFuture<org.bukkit.Chunk> pending = new CompletableFuture<>();
            when(world.getChunkAtAsync(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(pending);
            var task = npc.navigateTo(new Location(world, 3.5, 1, 0.5), 4,
                    net.folianpc.api.NavigationOptions.builder().radius(8).build());
            switch (ending) {
                case 0 -> task.cancel();
                case 1 -> npc.teleport(new Location(world, 20, 1, 0));
                case 2 -> npc.remove();
                default -> local.close();
            }
            var expected = List.of(net.folianpc.api.MovementResult.Status.CANCELLED,
                    net.folianpc.api.MovementResult.Status.SUPERSEDED,
                    net.folianpc.api.MovementResult.Status.REMOVED,
                    net.folianpc.api.MovementResult.Status.SHUTDOWN).get(ending);
            assertEquals(expected, task.route().join().status());
            assertEquals(expected, task.result().join().status());
            pending.complete(mock(org.bukkit.Chunk.class));
            assertFalse(npc.moving());
            local.close();
        }
    }

    @Test
    void failedTerrainCaptureAndRejectedExecutorFinishNavigation() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0.5, 1, 0.5, 0, 0));
        World world = flatWorld("world");
        when(world.getChunkAtAsync(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("load failed")));
        var failed = npc.navigateTo(new Location(world, 3.5, 1, 0.5), 4,
                net.folianpc.api.NavigationOptions.builder().radius(8).build());
        assertEquals(net.folianpc.api.MovementResult.Status.FAILED, failed.result().join().status());
        manager.async(task -> { throw new java.util.concurrent.RejectedExecutionException(); });
        var rejected = npc.navigateTo(new Location(flatWorld("world"), 3.5, 1, 0.5), 4,
                net.folianpc.api.NavigationOptions.builder().radius(8).build());
        assertEquals(net.folianpc.api.MovementResult.Status.FAILED, rejected.result().join().status());
        assertFalse(npc.moving());
    }

    private org.bukkit.inventory.ItemStack mutableItem(int amount) {
        var value = new AtomicInteger(amount);
        var item = mock(org.bukkit.inventory.ItemStack.class);
        var material = mock(org.bukkit.Material.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenAnswer(inv -> value.get());
        org.mockito.Mockito.doAnswer(inv -> { value.set(inv.getArgument(0)); return null; }).when(item).setAmount(anyInt());
        when(item.clone()).thenAnswer(inv -> mutableItem(value.get()));
        return item;
    }

    @Test
    void finiteInputsAndIndependentEquipmentSnapshotsAreEnforced() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> npc.scale(Double.NaN));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> npc.viewDistance(Double.POSITIVE_INFINITY));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> npc.walkTo(new Location(null, Double.NaN, 64, 0), 4));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> npc.walkTo(new Location(null, 0, 64, 0), Double.NaN));
        org.bukkit.inventory.ItemStack item = mutableItem(2);
        npc.equipment(org.bukkit.inventory.EquipmentSlot.HAND, item);
        item.setAmount(9);
        var data = npc.data();
        var snapshot = npc.snapshot();
        npc.equipment().get(org.bukkit.inventory.EquipmentSlot.HAND).setAmount(8);
        data.equipment().get(org.bukkit.inventory.EquipmentSlot.HAND).setAmount(7);
        snapshot.equipment().get(org.bukkit.inventory.EquipmentSlot.HAND).setAmount(6);
        assertEquals(2, npc.equipment().get(org.bukkit.inventory.EquipmentSlot.HAND).getAmount());
        assertEquals(2, data.equipment().get(org.bukkit.inventory.EquipmentSlot.HAND).getAmount());
        assertEquals(2, snapshot.equipment().get(org.bukkit.inventory.EquipmentSlot.HAND).getAmount());
    }

    @Test
    void hiddenAndStaleEntityClicksAreRejected() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player viewer = player(UUID.randomUUID());
        track(viewer, "world", 0, 64, 1);
        AtomicInteger clicks = new AtomicInteger();
        npc.onClick((player, target, type) -> clicks.incrementAndGet());
        npc.visibleWhen(player -> false);
        manager.tick();
        assertTrue(backend.fireInteract(viewer, npc.entityId(), ClickType.RIGHT));
        assertEquals(0, clicks.get());
        npc.visibleWhen(player -> true);
        manager.tick();
        backend.fireInteract(viewer, npc.entityId(), ClickType.RIGHT);
        assertEquals(1, clicks.get());
        npc.remove();
        assertFalse(backend.fireInteract(viewer, npc.entityId(), ClickType.RIGHT));
        assertEquals(1, clicks.get());
    }

    @Test
    void hysteresisKeepsExistingViewerWithinHideMargin() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.viewDistance(10).visibilityHysteresis(2);
        Player viewer = player(UUID.randomUUID());
        track(viewer, "world", 9, 64, 0);
        manager.tick();
        assertTrue(npc.viewers().contains(viewer.getUniqueId()));
        track(viewer, "world", 11, 64, 0);
        manager.tick();
        assertTrue(npc.viewers().contains(viewer.getUniqueId()));
        track(viewer, "world", 13, 64, 0);
        manager.tick();
        assertFalse(npc.viewers().contains(viewer.getUniqueId()));
    }

    @Test
    void appearanceBatchCombinesRespawnsAndPartialUpdates() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player viewer = player(UUID.randomUUID());
        track(viewer, "world", 0, 64, 1);
        manager.tick();
        backend.shows.clear();
        npc.batch(target -> target.name("Alice").glowing(true).scale(2).collidable(false));
        assertEquals(1, backend.shows.size());
        assertEquals("Alice", backend.shows.getFirst().npc().name());
        assertEquals(2, backend.shows.getFirst().npc().scale());
        assertTrue(backend.shows.getFirst().npc().glowing());
        assertTrue(backend.metas.isEmpty());
        backend.shows.clear();
        npc.batch(target -> target.glowing(false).invisible(true).skinLayers(false));
        assertEquals(1, backend.metas.size());
        assertTrue(backend.shows.isEmpty());
        npc.batch(target -> target.batch(inner -> inner.glowing(true)));
        assertEquals(2, backend.metas.size());
    }

    @Test
    void unchangedAppearanceAvoidsPacketsAndThrowingBatchFlushesCompletedMutations() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player viewer = player(UUID.randomUUID());
        track(viewer, "world", 0, 64, 1);
        manager.tick();
        backend.shows.clear();
        npc.name("Bob").skin(null).mirrorSkin(false).nametagVisible(true).glowing(false)
                .invisible(false).skinLayers(true).scale(1).baby(false).collidable(true)
                .showInTabList(false).pose(net.folianpc.api.NpcPose.STANDING).appearance(npc.appearance());
        assertTrue(backend.shows.isEmpty());
        assertTrue(backend.metas.isEmpty());
        assertTrue(backend.scales.isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> npc.batch(target -> { target.glowing(true); throw new IllegalStateException("partial"); }));
        assertTrue(npc.glowing());
        assertEquals(1, backend.metas.size());
        npc.invisible(true);
        assertEquals(2, backend.metas.size());
    }

    @Test
    void viewerOverridesStayIndependentAndDisconnectClearsThem() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player a = player(UUID.randomUUID());
        Player b = player(UUID.randomUUID());
        track(a, "world", 0, 64, 1);
        track(b, "world", 0, 64, 2);
        var alternate = new net.folianpc.api.NpcAppearance(true, false, true, 2,
                net.kyori.adventure.text.format.NamedTextColor.RED, true, false);
        npc.appearanceFor(a.getUniqueId(), net.folianpc.api.ViewerAppearance.builder().appearance(alternate).build());
        manager.tick();
        var shownA = backend.shows.stream().filter(show -> show.viewer() == a).findFirst().orElseThrow().npc();
        var shownB = backend.shows.stream().filter(show -> show.viewer() == b).findFirst().orElseThrow().npc();
        assertTrue(shownA.glowing());
        assertTrue(shownA.needsTeam());
        assertEquals(2, shownA.scale());
        assertFalse(shownB.glowing());
        assertFalse(npc.glowing());
        npc.clearAppearanceFor(a.getUniqueId());
        assertFalse(npc.snapshot(a.getUniqueId()).glowing());
        npc.appearanceFor(a.getUniqueId(), net.folianpc.api.ViewerAppearance.builder().appearance(alternate).build());
        manager.dropPlayer(a.getUniqueId());
        assertFalse(npc.snapshot(a.getUniqueId()).glowing());
    }

    @Test
    void nametagLayoutFollowsEntityDimensionsAndSupportsFixedOffsets() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        npc.nametag(List.of("upper", "lower"));
        assertEquals(66.05, npc.snapshot().hologram().getLast().y(), 1e-6);
        npc.baby(true).scale(2);
        assertEquals(67.85, npc.snapshot().hologram().getLast().y(), 1e-6);
        npc.type(org.bukkit.entity.EntityType.VILLAGER).baby(false).scale(1);
        double adultHeight = npc.snapshot().hologram().getLast().y();
        npc.baby(true).scale(2);
        assertEquals(adultHeight, npc.snapshot().hologram().getLast().y(), 1e-6);
        npc.type(org.bukkit.entity.EntityType.ENDERMAN).baby(false);
        assertEquals(70.05, npc.snapshot().hologram().getLast().y(), 1e-6);
        npc.nametagLayout(new net.folianpc.api.NametagLayout(0.5, 3, false));
        assertEquals(67, npc.snapshot().hologram().getLast().y());
        assertEquals(67.5, npc.snapshot().hologram().getFirst().y());
    }

    @Test
    void latestSkinRequestWinsAndDirectChoicesDiscardPendingFetches() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        CompletableFuture<Skin> first = new CompletableFuture<>();
        CompletableFuture<Skin> second = new CompletableFuture<>();
        var old = npc.skinAsync(first);
        var fresh = npc.skinAsync(second);
        second.complete(Skin.of("new", null));
        first.complete(Skin.of("old", null));
        assertEquals(net.folianpc.api.SkinApplyResult.Status.SUPERSEDED, old.join().status());
        assertEquals(net.folianpc.api.SkinApplyResult.Status.APPLIED, fresh.join().status());
        assertEquals("new", npc.skin().value());
        CompletableFuture<Skin> third = new CompletableFuture<>();
        var pending = npc.skinAsync(third);
        npc.skin(Skin.of("explicit", null));
        third.complete(Skin.of("late", null));
        assertEquals(net.folianpc.api.SkinApplyResult.Status.SUPERSEDED, pending.join().status());
        assertEquals("explicit", npc.skin().value());
        var failed = npc.skinAsync(CompletableFuture.failedFuture(new IllegalStateException("fetch")));
        assertEquals(net.folianpc.api.SkinApplyResult.Status.FAILED, failed.join().status());
        var removed = npc.skinAsync(new CompletableFuture<>());
        npc.remove();
        assertEquals(net.folianpc.api.SkinApplyResult.Status.REMOVED, removed.join().status());
    }

    @Test
    void patrolCopiesWaypointsWaitsAndFinishesOrStopsRepeating() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0.5, 1, 0.5, 0, 0));
        World world = flatWorld("world");
        Location first = new Location(world, 1.5, 1, 0.5);
        var navigation = net.folianpc.api.NavigationOptions.builder().radius(8).build();
        var task = npc.patrol(List.of(first, new Location(world, 2.5, 1, 0.5)),
                new net.folianpc.api.PatrolOptions(10, 4, false, navigation));
        first.setX(100);
        for (int pass = 0; pass < 40; pass++) manager.tick();
        assertEquals(net.folianpc.api.MovementResult.Status.ARRIVED, task.result().join().status());
        assertEquals(2.5, npc.x());
        var repeating = npc.patrol(List.of(new Location(world, 1.5, 1, 0.5), new Location(world, 2.5, 1, 0.5)),
                new net.folianpc.api.PatrolOptions(10, 2, true, navigation));
        for (int pass = 0; pass < 30; pass++) manager.tick();
        assertFalse(repeating.result().isDone());
        npc.stopWalking();
        assertEquals(net.folianpc.api.MovementResult.Status.CANCELLED, repeating.result().join().status());
        assertFalse(npc.moving());
    }

    @Test
    void followUsesOwnedLocationHoldsDistanceAndEndsOnDisconnectOrManualMovement() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0.5, 1, 0.5, 0, 0));
        World world = flatWorld("world");
        Player target = player(UUID.randomUUID());
        track(target, "world", 1.5, 1, 0.5);
        AtomicReference<Location> location = new AtomicReference<>(new Location(world, 1.5, 1, 0.5));
        when(target.getLocation()).thenAnswer(inv -> location.get().clone());
        var options = new net.folianpc.api.FollowOptions(4, 2, 20, 2,
                net.folianpc.api.NavigationOptions.builder().radius(16).build());
        var task = npc.follow(target.getUniqueId(), options);
        manager.tick();
        assertFalse(npc.moving());
        location.set(new Location(world, 8.5, 1, 0.5));
        track(target, "world", 8.5, 1, 0.5);
        manager.tick();
        manager.tick();
        assertTrue(npc.moving());
        npc.walkTo(new Location(world, 2.5, 1, 0.5), 4);
        assertEquals(net.folianpc.api.MovementResult.Status.SUPERSEDED, task.result().join().status());
        var disconnected = npc.follow(target.getUniqueId(), options);
        tracker.remove(target.getUniqueId());
        manager.tick();
        assertEquals(net.folianpc.api.MovementResult.Status.CANCELLED, disconnected.result().join().status());
        assertFalse(npc.moving());
    }

    @Test
    void removedAndClosedServicesCompletePendingBehaviors() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0.5, 1, 0.5, 0, 0));
        var task = npc.patrol(List.of(new Location(flatWorld("world"), 3.5, 1, 0.5)), net.folianpc.api.PatrolOptions.defaults());
        manager.close();
        assertEquals(net.folianpc.api.MovementResult.Status.SHUTDOWN, task.result().join().status());
        assertFalse(npc.moving());
    }

    @Test
    void appearanceRecipeAndSameSizeTextChangesSendOnlyRequiredPackets() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player viewer = player(UUID.randomUUID());
        track(viewer, "world", 0, 64, 1);
        npc.nametag(List.of("before"));
        manager.tick();
        backend.shows.clear();
        npc.batch(target -> target.appearance(new net.folianpc.api.NpcAppearance(true, false, true, 1, null, true, false))
                .nametag(List.of("after")));
        assertTrue(backend.shows.isEmpty());
        assertEquals(1, backend.metas.size());
        assertEquals(1, backend.hologramRefreshes.size());
        assertEquals("after", backend.hologramRefreshes.getFirst().hologram().getFirst().text());
    }

    @Test
    void clearanceChangesSupersedeCapturedNavigationAndObserverCallbacksCanStartNewRequests() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0.5, 1, 0.5, 0, 0));
        World world = flatWorld("world");
        CompletableFuture<org.bukkit.Chunk> pending = new CompletableFuture<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(pending);
        var options = net.folianpc.api.NavigationOptions.builder().radius(8).build();
        var task = npc.navigateTo(new Location(world, 3.5, 1, 0.5), 4, options);
        npc.scale(2);
        assertEquals(net.folianpc.api.MovementResult.Status.SUPERSEDED, task.result().join().status());
        npc.scale(1);
        var first = npc.navigateTo(new Location(world, 3.5, 1, 0.5), 4, options);
        AtomicReference<net.folianpc.api.MovementTask> callback = new AtomicReference<>();
        first.result().thenAccept(outcome -> callback.set(npc.navigateTo(new Location(world, 2.5, 1, 0.5), 4, options)));
        var second = npc.navigateTo(new Location(world, 4.5, 1, 0.5), 4, options);
        assertEquals(net.folianpc.api.MovementResult.Status.SUPERSEDED, second.result().join().status());
        assertNotNull(callback.get());
        callback.get().cancel();
        assertEquals(net.folianpc.api.MovementResult.Status.CANCELLED, callback.get().result().join().status());
    }

    @Test
    void legacyNavigationRetainsSolidGroundPolicyWhileNewDefaultsAvoidWater() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0.5, 1, 0.5, 0, 0));
        World world = flatWorld("world");
        org.bukkit.Material solid = mock(org.bukkit.Material.class);
        when(solid.isSolid()).thenReturn(true);
        org.bukkit.Material air = mock(org.bukkit.Material.class);
        org.bukkit.Material water = mock(org.bukkit.Material.class);
        when(water.ordinal()).thenReturn(org.bukkit.Material.WATER.ordinal());
        org.bukkit.Chunk chunk = mock(org.bukkit.Chunk.class);
        org.bukkit.ChunkSnapshot snapshot = mock(org.bukkit.ChunkSnapshot.class);
        when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            int x = inv.getArgument(0);
            int y = inv.getArgument(1);
            int z = inv.getArgument(2);
            return y <= 0 ? solid : x == 1 && z == 0 && y == 1 ? water : air;
        });
        when(chunk.getChunkSnapshot(false, false, false)).thenReturn(snapshot);
        when(world.getChunkAtAsync(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(CompletableFuture.completedFuture(chunk));
        Location destination = new Location(world, 1.5, 1, 0.5);
        assertTrue(npc.navigateTo(destination, 4).join());
        var safe = npc.navigateTo(destination, 4, net.folianpc.api.NavigationOptions.builder().radius(8).build());
        assertEquals(net.folianpc.api.MovementResult.Status.UNREACHABLE, safe.result().join().status());
    }

    @Test
    void equipmentBatchesRetainChangedSlotsIncludingExplicitClears() {
        NpcImpl npc = manager.create("Bob", new Position("world", 0, 64, 0, 0, 0));
        Player viewer = player(UUID.randomUUID());
        track(viewer, "world", 0, 64, 1);
        npc.equipment(EquipmentSlot.HAND, mutableItem(2));
        manager.tick();
        npc.batch(target -> target.equipment(EquipmentSlot.HAND, null)
                .equipment(EquipmentSlot.OFF_HAND, mutableItem(3)));
        assertEquals(1, backend.equips.size());
        assertEquals(Set.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND), backend.equipmentChanges.getFirst());
        assertFalse(backend.equips.getFirst().equipment().containsKey(EquipmentSlot.HAND));
        assertEquals(3, backend.equips.getFirst().equipment().get(EquipmentSlot.OFF_HAND).getAmount());
        npc.equipment(EquipmentSlot.OFF_HAND, null);
        assertEquals(Set.of(EquipmentSlot.OFF_HAND), backend.equipmentChanges.getLast());
        assertTrue(backend.equips.getLast().equipment().isEmpty());
    }

    @Test
    void registeredDimensionsDriveClearanceAndNametagsWithoutShrinkingUnsupportedPoses() {
        backend.sizes.put(org.bukkit.entity.EntityType.POLAR_BEAR, new net.folianpc.internal.protocol.BodySize(1.4, 1.4));
        NpcImpl npc = manager.create(UUID.randomUUID(), "Bear", org.bukkit.entity.EntityType.POLAR_BEAR,
                new Position("world", 0.5, 1, 0.5, 0, 0));
        npc.nametag(List.of("bear"));
        var options = net.folianpc.api.NavigationOptions.defaults();
        assertEquals(1.4, npc.dimensions(options)[0]);
        assertEquals(1.4, npc.dimensions(options)[1]);
        npc.pose(net.folianpc.api.NpcPose.SWIMMING);
        assertEquals(1.4, npc.dimensions(options)[1]);
        npc.baby(true).scale(2);
        assertEquals(1.4, npc.dimensions(options)[0]);
        assertEquals(2.65, npc.snapshot().hologram().getFirst().y(), 1e-6);
        assertEquals(0.5, npc.dimensions(net.folianpc.api.NavigationOptions.builder().dimensions(0.5, 0.75).build())[0]);
    }

    @Test
    void arrivalCallbacksKeepCommittedMovementWhenStoppingOrStartingAnotherRoute() {
        for (int mode = 0; mode < 3; mode++) {
            NpcImpl npc = manager.create("Arrival", new Position("world", 0, 64, 0, 0, 0));
            Player viewer = player(UUID.randomUUID());
            track(viewer, "world", 0, 64, 1);
            manager.tick();
            var request = npc.beginMovement();
            npc.installRoute(request, List.of(new double[]{0.2, 64, 0}), 4);
            int action = mode;
            request.result().thenRun(() -> {
                if (action == 0) npc.stopWalking();
                else if (action == 1) npc.walkToward(1, 64, 0, 4);
                else {
                    var replacement = npc.beginMovement();
                    npc.installRoute(replacement, List.of(new double[]{1, 64, 0}), 4);
                }
            });
            manager.tick();
            assertEquals(net.folianpc.api.MovementResult.Status.ARRIVED, request.result().join().status());
            assertEquals(0.2, backend.moves.stream().filter(move -> move.viewer() == viewer
                    && move.entityId() == npc.entityId()).mapToDouble(RecordingProtocolBackend.Move::dx).sum(), 1e-6);
            if (mode > 0) {
                manager.tick();
                assertEquals(npc.x(), backend.moves.stream().filter(move -> move.viewer() == viewer
                        && move.entityId() == npc.entityId()).mapToDouble(RecordingProtocolBackend.Move::dx).sum(), 1e-6);
            }
            npc.remove();
            tracker.remove(viewer.getUniqueId());
        }
    }

    @Test
    void queuedMovementCoalescesAgainstThePositionAlreadySentToEachViewer() {
        NpcImpl npc = manager.create("Queued", new Position("world", 0, 64, 0, 0, 0));
        Player viewer = player(UUID.randomUUID());
        track(viewer, "world", 0, 64, 1);
        manager.tick();
        List<Runnable> queued = new ArrayList<>();
        try (var schedulers = org.mockito.Mockito.mockStatic(Schedulers.class)) {
            schedulers.when(() -> Schedulers.onEntity(org.mockito.ArgumentMatchers.any(Plugin.class),
                    org.mockito.ArgumentMatchers.any(org.bukkit.entity.Entity.class),
                    org.mockito.ArgumentMatchers.any(Runnable.class))).thenAnswer(call -> {
                queued.add(call.getArgument(2));
                return null;
            });
            var request = npc.beginMovement();
            npc.installRoute(request, List.of(new double[]{0.6, 64, 0}), 4);
            request.result().thenRun(npc::stopWalking);
            manager.tick();
            manager.tick();
            assertTrue(backend.moves.isEmpty());
            List.copyOf(queued).forEach(Runnable::run);
        }
        assertEquals(0.6, npc.x(), 1e-6);
        assertEquals(0.6, backend.moves.stream().filter(move -> move.entityId() == npc.entityId())
                .mapToDouble(RecordingProtocolBackend.Move::dx).sum(), 1e-6);
    }

    @Test
    void arrivalTeleportResetsTheViewerBaselineBeforeFurtherMovement() {
        NpcImpl npc = manager.create("Teleport", new Position("world", 0, 64, 0, 0, 0));
        Player viewer = player(UUID.randomUUID());
        track(viewer, "world", 0, 64, 1);
        manager.tick();
        var request = npc.beginMovement();
        npc.installRoute(request, List.of(new double[]{0.2, 64, 0}), 4);
        request.result().thenRun(() -> npc.teleportTo(new Position("world", 3, 64, 0, 0, 0)));
        manager.tick();
        assertTrue(backend.moves.isEmpty());
        assertEquals(3, backend.shows.getLast().npc().x());
        npc.walkToward(4, 64, 0, 4);
        manager.tick();
        assertEquals(0.4, backend.moves.getLast().dx(), 1e-6);
    }

    @Test
    void existingNametagDisplaysMoveForLayoutAndBodyHeightChanges() {
        NpcImpl npc = manager.create("Layout", new Position("world", 0, 64, 0, 0, 0));
        Player viewer = player(UUID.randomUUID());
        track(viewer, "world", 0, 64, 1);
        npc.nametag(List.of("upper", "lower"));
        manager.tick();
        int[] ids = npc.nametagIds();
        npc.nametagLayout(new net.folianpc.api.NametagLayout(0.5, 0.75, true));
        assertEquals(0.72, backend.moves.stream().filter(move -> move.entityId() == ids[0])
                .mapToDouble(RecordingProtocolBackend.Move::dy).sum(), 1e-6);
        assertEquals(0.5, backend.moves.stream().filter(move -> move.entityId() == ids[1])
                .mapToDouble(RecordingProtocolBackend.Move::dy).sum(), 1e-6);
        backend.moves.clear();
        npc.pose(NpcPose.SWIMMING);
        assertEquals(-1.2, backend.moves.getLast().dy(), 1e-6);
        backend.moves.clear();
        npc.scale(2);
        assertEquals(0.6, backend.moves.getLast().dy(), 1e-6);
        npc.type(EntityType.VILLAGER).pose(NpcPose.STANDING);
        backend.moves.clear();
        double before = npc.snapshot().hologram().getLast().y();
        npc.baby(true);
        assertEquals(npc.snapshot().hologram().getLast().y() - before, backend.moves.getLast().dy(), 1e-6);
        backend.moves.clear();
        npc.appearance(new NpcAppearance(false, false, true, 3, null, true, false));
        assertFalse(backend.moves.isEmpty());
    }

    @Test
    void largeNametagOffsetsRespawnWithoutClampedRelativeMovement() {
        NpcImpl npc = manager.create("Offset", new Position("world", 0, 64, 0, 0, 0));
        Player viewer = player(UUID.randomUUID());
        track(viewer, "world", 0, 64, 1);
        npc.nametag(List.of("label"));
        manager.tick();
        backend.shows.clear();
        npc.nametagLayout(new net.folianpc.api.NametagLayout(0.3, 20, true));
        assertTrue(backend.moves.isEmpty());
        assertEquals(npc.snapshot().hologram().getFirst().y(), backend.shows.getLast().npc().hologram().getFirst().y());
        npc.walkToward(1, 64, 0, 4);
        manager.tick();
        assertEquals(0.4, backend.moves.getLast().dx(), 1e-6);
    }

}
