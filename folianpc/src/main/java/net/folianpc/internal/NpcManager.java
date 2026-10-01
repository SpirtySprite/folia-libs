package net.folianpc.internal;

import net.folianpc.api.Emote;
import net.folianpc.api.MovementResult;
import net.folianpc.api.NavigationOptions;
import net.folianpc.internal.pathfinding.TerrainCapture;
import net.folianpc.api.NpcClickListener;
import net.folianpc.api.event.NpcInteractEvent;
import net.folianpc.api.event.NpcRemoveEvent;
import net.folianpc.api.event.NpcSpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import net.folianpc.internal.geometry.LookAt;
import net.folianpc.internal.pathfinding.RoutePlanner;
import net.folianpc.internal.protocol.NpcSnapshot;
import net.folianpc.internal.protocol.HologramLine;
import net.folianpc.internal.protocol.ProtocolBackend;
import net.folianpc.internal.scheduler.Schedulers;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class NpcManager {

    private final Plugin plugin;
    private final ProtocolBackend backend;
    private final PlayerTracker tracker;

    private final ConcurrentHashMap<UUID, NpcImpl> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, NpcImpl> byEntityId = new ConcurrentHashMap<>();

    private record SentPosition(String world, double x, double y, double z) { }
    private final Map<UUID, Map<Integer, SentPosition>> sentPositions = new ConcurrentHashMap<>();

    private final Object lifecycle = new Object();
    private volatile boolean closed;

    private volatile double viewDistance = 48.0;
    private volatile RoutePlanner planner = new RoutePlanner();

    private static final double SECONDS_PER_PASS = 0.1;

    private final Set<UUID> shouldSee = new HashSet<>();
    private final Set<UUID> nearNow = new HashSet<>();
    private volatile boolean debug;

    private volatile java.util.function.LongSupplier clock = System::currentTimeMillis;
    private volatile Consumer<Event> events = event -> Bukkit.getPluginManager().callEvent(event);
    private volatile java.util.concurrent.Executor async = Runnable::run;

    public void async(java.util.concurrent.Executor executor) {
        this.async = executor;
    }

    public void clock(java.util.function.LongSupplier clock) {
        this.clock = clock;
    }

    public void events(Consumer<Event> publisher) {
        this.events = publisher;
    }

    public NpcManager(Plugin plugin, ProtocolBackend backend, PlayerTracker tracker) {
        this.plugin = plugin;
        this.backend = backend;
        this.tracker = tracker;
        backend.onInteract(this::handleInteract);
    }

    public void viewDistance(double blocks) {
        NpcImpl.finite(blocks, "view distance");
        this.viewDistance = Math.max(1.0, blocks);
    }

    public void debug(boolean value) {
        this.debug = value;
    }

    public int count() {
        return byId.size();
    }

    private void log(String message) {
        if (debug && plugin != null && plugin.getLogger() != null) {
            plugin.getLogger().info("FoliaNPC: " + message);
        }
    }

    public NpcImpl create(String name, Position position) {
        return create(UUID.randomUUID(), name, org.bukkit.entity.EntityType.PLAYER, position);
    }

    public NpcImpl create(UUID id, String name, org.bukkit.entity.EntityType type, Position position) {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(position, "position");
        NpcImpl npc;
        synchronized (lifecycle) {
            if (closed) {
                throw new IllegalStateException("NPC manager is closed");
            }
            if (byId.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate NPC UUID: " + id);
            }
            npc = new NpcImpl(id, backend.nextEntityId(), name, type, position, this);
            byId.put(npc.id(), npc);
            byEntityId.put(npc.entityId(), npc);
        }
        events.accept(new NpcSpawnEvent(npc));
        return npc;
    }

    public NpcImpl get(UUID id) {
        return byId.get(id);
    }

    public java.util.Collection<NpcImpl> all() {
        return byId.values();
    }

    void unregister(NpcImpl npc) {
        byId.remove(npc.id(), npc);
        byEntityId.remove(npc.entityId(), npc);
        for (UUID viewerId : npc.viewers()) {
            PlayerTracker.Tracked t = tracker.get(viewerId);
            if (t != null) {
                hideFrom(t.player(), npc);
            }
        }
        npc.viewers().clear();
        events.accept(new NpcRemoveEvent(npc));
    }

    int newEntityId() {
        return backend.nextEntityId();
    }

    public void forgetPlayer(UUID playerId) {
        sentPositions.remove(playerId);
        for (NpcImpl npc : byId.values()) {
            npc.forget(playerId);
        }
    }

    private void hideFrom(Player viewer, NpcImpl npc) {
        NpcSnapshot snapshot = npc.snapshot();
        int[] lineIds = npc.nametagIds();
        Schedulers.onEntity(plugin, viewer, () -> {
            if (!npc.removed() && npc.viewers().contains(viewer.getUniqueId())) return;
            hide(viewer, snapshot);
            if (lineIds.length > 0) {
                removeEntities(viewer, lineIds);
            }
        });
    }

    public void refresh(NpcImpl npc, int[] staleLineIds) {
        forEachViewer(npc, (viewer, snapshot) -> {
            removeEntities(viewer, staleLineIds);
            hide(viewer, snapshot);
            show(viewer, snapshot);
        });
    }

    public void refreshViewer(NpcImpl npc, UUID viewerId) {
        PlayerTracker.Tracked tracked = tracker.get(viewerId);
        if (tracked != null) {
            Schedulers.onEntity(plugin, tracked.player(), () -> {
                if (!closed && !npc.removed() && npc.viewers().contains(viewerId)) {
                    NpcSnapshot snapshot = npc.snapshot(viewerId);
                    removeEntities(tracked.player(), npc.nametagIds());
                    hide(tracked.player(), snapshot);
                    show(tracked.player(), snapshot);
                }
            });
        }
    }

    public void updateMeta(NpcImpl npc) {
        forEachViewer(npc, backend::updateMeta);
    }

    java.util.Optional<net.folianpc.internal.protocol.BodySize> bodySize(org.bukkit.entity.EntityType type) {
        return backend.bodySize(type);
    }

    public void updateScale(NpcImpl npc) {
        forEachViewer(npc, (viewer, npcState) -> backend.scale(viewer, npcState.entityId(), npcState.scale()));
    }

    public void updateEquipment(NpcImpl npc, Set<org.bukkit.inventory.EquipmentSlot> changed) {
        Set<org.bukkit.inventory.EquipmentSlot> slots = Set.copyOf(changed);
        forEachViewer(npc, (viewer, npcState) ->
                backend.equipChanges(viewer, npcState.entityId(), npcState.equipment(), slots));
    }

    public void updateNametag(NpcImpl npc) {
        forEachViewer(npc, (viewer, snapshot) -> {
            Map<Integer, SentPosition> positions = sentPositions.get(viewer.getUniqueId());
            if (positions == null) return;
            if (snapshot.hologram().stream().anyMatch(line -> needsRespawn(positions.get(line.entityId()),
                    snapshot.world(), line.x(), line.y(), line.z()))) {
                hide(viewer, snapshot);
                removeEntities(viewer, npc.nametagIds());
                show(viewer, snapshot);
            } else {
                for (HologramLine line : snapshot.hologram()) {
                    moveTo(viewer, positions, line.entityId(), snapshot.world(), line.x(), line.y(), line.z());
                }
                backend.refreshHologram(viewer, snapshot);
            }
        });
    }

    public void animate(NpcImpl npc, int action) {
        forEachViewer(npc, (viewer, npcState) -> backend.animate(viewer, npcState.entityId(), action));
    }

    private record EmoteStep(float yawOffset, float pitchOffset, long delayTicks, boolean swing) {
    }

    private static final Map<Emote, List<EmoteStep>> EMOTE_STEPS = Map.of(
            Emote.NOD, List.of(
                    new EmoteStep(0, 15, 0, false),
                    new EmoteStep(0, -15, 3, false),
                    new EmoteStep(0, 10, 3, false),
                    new EmoteStep(0, -10, 3, false),
                    new EmoteStep(0, 0, 3, false)),
            Emote.SHAKE_HEAD, List.of(
                    new EmoteStep(20, 0, 0, false),
                    new EmoteStep(-20, 0, 3, false),
                    new EmoteStep(15, 0, 3, false),
                    new EmoteStep(-15, 0, 3, false),
                    new EmoteStep(0, 0, 3, false)),
            Emote.WAVE, List.of(
                    new EmoteStep(10, 0, 0, true),
                    new EmoteStep(-10, 0, 6, true),
                    new EmoteStep(10, 0, 6, true),
                    new EmoteStep(0, 0, 6, false)),
            Emote.DANCE, List.of(
                    new EmoteStep(45, 0, 0, false),
                    new EmoteStep(90, 0, 2, false),
                    new EmoteStep(135, 0, 2, false),
                    new EmoteStep(180, 0, 2, false),
                    new EmoteStep(225, 0, 2, false),
                    new EmoteStep(270, 0, 2, false),
                    new EmoteStep(315, 0, 2, false),
                    new EmoteStep(360, 0, 2, false)));

    public void playEmote(NpcImpl npc, Emote emote) {
        List<EmoteStep> steps = EMOTE_STEPS.get(emote);
        if (steps == null) {
            return;
        }
        Position base = npc.position();
        float baseYaw = base.yaw();
        float basePitch = base.pitch();
        int entityId = npc.entityId();
        long cumulative = 0;
        for (int i = 0; i < steps.size(); i++) {
            EmoteStep step = steps.get(i);
            cumulative += step.delayTicks();
            long atTick = cumulative;
            float yaw = LookAt.normalizeYaw(baseYaw + step.yawOffset());
            float pitch = clampPitch(basePitch + step.pitchOffset());
            boolean lastStep = i == steps.size() - 1;
            boolean swingNow = step.swing();
            for (UUID viewerId : npc.viewers()) {
                PlayerTracker.Tracked t = tracker.get(viewerId);
                if (t == null) {
                    continue;
                }
                Player viewer = t.player();
                Schedulers.onEntityLater(plugin, viewer, () -> {
                    if (closed || npc.removed() || !npc.viewers().contains(viewerId)) {
                        return;
                    }
                    backend.look(viewer, entityId, yaw, pitch);
                    if (swingNow) {
                        backend.animate(viewer, entityId, 0);
                    }
                    if (lastStep) {
                        npc.forgetLook(viewerId);
                    }
                }, atTick);
            }
        }
    }

    private static float clampPitch(float pitch) {
        if (pitch > 90f) return 90f;
        if (pitch < -90f) return -90f;
        return pitch;
    }

    public void reposition(NpcImpl npc) {
        for (UUID viewerId : List.copyOf(npc.viewers())) {
            PlayerTracker.Tracked tracked = tracker.get(viewerId);
            if (tracked == null) {
                npc.viewers().remove(viewerId);
            } else {
                Schedulers.onEntity(plugin, tracked.player(), () -> evaluateVisibility(npc, tracked.player(), true));
            }
        }
    }

    private void evaluateVisibility(NpcImpl npc, Player viewer, boolean reposition) {
        UUID viewerId = viewer.getUniqueId();
        if (closed || npc.removed()) {
            return;
        }
        PlayerTracker.Tracked tracked = tracker.get(viewerId);
        Position pos = npc.position();
        double range = npc.viewDistance() > 0 ? npc.viewDistance() : viewDistance;
        if (npc.viewers().contains(viewerId)) {
            range += npc.visibilityHysteresis();
        }
        boolean visible = tracked != null && tracked.player() == viewer && tracked.world().equals(pos.world())
                && npc.visibleTo(viewer, pos.distanceSquared(tracked.x(), tracked.y(), tracked.z()) <= range * range);
        if (!visible) {
            if (npc.viewers().remove(viewerId)) {
                npc.forgetLook(viewerId);
                hide(viewer, npc.snapshot());
                removeEntities(viewer, npc.nametagIds());
            }
            return;
        }
        boolean show = npc.viewers().add(viewerId);
        if (show || reposition) {
            NpcSnapshot snapshot = npc.snapshot(viewerId);
            if (reposition) {
                removeEntities(viewer, npc.nametagIds());
                hide(viewer, snapshot);
                npc.forgetLook(viewerId);
            }
            show(viewer, snapshot);
            if (show) log("shown '" + npc.name() + "' (id=" + npc.entityId() + ") to " + viewer.getName());
        }
        if (npc.lookAtPlayers() && !npc.moving()) {
            LookAt.Rotation look = LookAt.face(pos.x(), pos.y() + Position.EYE_HEIGHT, pos.z(),
                    tracked.x(), tracked.y() + Position.EYE_HEIGHT, tracked.z());
            if (npc.lookChanged(viewerId, look.yaw(), look.pitch())) {
                backend.look(viewer, npc.entityId(), look.yaw(), look.pitch());
            }
        }
    }

    public CompletableFuture<Boolean> navigate(NpcImpl npc, Location target, double speed) {
        return navigate(npc, target, speed, NavigationOptions.builder()
                .terrain(NavigationOptions.TerrainPolicy.SOLID_GROUND).build()).route().thenCompose(outcome ->
                outcome.status() == MovementResult.Status.FAILED
                        ? CompletableFuture.failedFuture(outcome.failure().orElseThrow())
                        : CompletableFuture.completedFuture(outcome.status() == MovementResult.Status.ROUTE_FOUND));
    }

    CompletableFuture<Location> followDestination(UUID target) {
        PlayerTracker.Tracked tracked = tracker.get(target);
        if (tracked == null || closed) return CompletableFuture.completedFuture(null);
        var scheduler = Schedulers.scheduler(plugin);
        if (scheduler == null) return CompletableFuture.completedFuture(null);
        return scheduler.callForEntity(tracked.player(), () -> {
            PlayerTracker.Tracked current = tracker.get(target);
            return current == null || current.player() != tracked.player() ? null : tracked.player().getLocation().clone();
        });
    }

    public MovementRequest navigate(NpcImpl npc, Location target, double speed, NavigationOptions options) {
        java.util.Objects.requireNonNull(target, "target");
        java.util.Objects.requireNonNull(options, "options");
        NpcImpl.finite(speed, "movement speed");
        Location destination = target.clone();
        World world = destination.getWorld();
        new Position(world == null ? "world" : world.getName(), destination.getX(), destination.getY(),
                destination.getZ(), destination.getYaw(), destination.getPitch());
        MovementRequest request = npc.beginMovement();
        if (request.isCancelled()) {
            return request;
        }
        Position from = npc.position();
        double[] dimensions = npc.dimensions(options);
        if (world == null || !world.getName().equals(from.world()) || dimensions[0] > 16 || dimensions[1] > 32
                || Math.abs(destination.getX() - from.x()) > options.radius()
                || Math.abs(destination.getZ() - from.z()) > options.radius()) {
            npc.cancelMovement(request, MovementResult.Status.UNREACHABLE);
            return request;
        }
        try {
            var capture = new TerrainCapture(Schedulers.scheduler(plugin)).capture(world, from.x(), from.z(),
                    options.radius(), options.terrain());
            request.work.add(capture);
            var search = capture.thenApplyAsync(sampler -> planner.route(sampler, from.x(), from.y(), from.z(),
                    destination.getX(), destination.getY(), destination.getZ(), options, dimensions[0], dimensions[1]), async);
            request.work.add(search);
            search.whenComplete((route, failure) -> {
                if (failure != null) {
                    npc.completeMovement(request, MovementResult.failed(failure));
                } else if (route.isEmpty()) {
                    npc.cancelMovement(request, MovementResult.Status.UNREACHABLE);
                } else {
                    npc.installRoute(request, route, speed);
                }
            });
        } catch (RuntimeException failure) {
            npc.completeMovement(request, MovementResult.failed(failure));
        }
        return request;
    }

    private void show(Player viewer, NpcSnapshot snapshot) {
        backend.show(viewer, snapshot);
        Map<Integer, SentPosition> positions = sentPositions.computeIfAbsent(viewer.getUniqueId(),
                ignored -> new ConcurrentHashMap<>());
        positions.put(snapshot.entityId(), new SentPosition(snapshot.world(), snapshot.x(), snapshot.y(), snapshot.z()));
        for (HologramLine line : snapshot.hologram()) {
            positions.put(line.entityId(), new SentPosition(snapshot.world(), line.x(), line.y(), line.z()));
        }
    }

    private void hide(Player viewer, NpcSnapshot snapshot) {
        backend.hide(viewer, snapshot);
        Map<Integer, SentPosition> positions = sentPositions.get(viewer.getUniqueId());
        if (positions != null) positions.remove(snapshot.entityId());
    }

    private void removeEntities(Player viewer, int[] ids) {
        backend.removeEntities(viewer, ids);
        Map<Integer, SentPosition> positions = sentPositions.get(viewer.getUniqueId());
        if (positions != null) for (int id : ids) positions.remove(id);
    }

    private static boolean needsRespawn(SentPosition previous, String world, double x, double y, double z) {
        return previous == null || !previous.world().equals(world) || Math.abs(x - previous.x()) >= 8
                || Math.abs(y - previous.y()) >= 8 || Math.abs(z - previous.z()) >= 8;
    }

    private void moveTo(Player viewer, Map<Integer, SentPosition> positions, int entityId,
                        String world, double x, double y, double z) {
        SentPosition previous = positions.get(entityId);
        if (previous == null) return;
        double dx = x - previous.x();
        double dy = y - previous.y();
        double dz = z - previous.z();
        if (dx != 0 || dy != 0 || dz != 0) {
            backend.move(viewer, entityId, dx, dy, dz);
            positions.put(entityId, new SentPosition(world, x, y, z));
        }
    }

    private void walkStep(NpcImpl npc) {
        forEachViewer(npc, (viewer, snapshot) -> {
            Map<Integer, SentPosition> positions = sentPositions.get(viewer.getUniqueId());
            if (positions == null) return;
            if (needsRespawn(positions.get(snapshot.entityId()), snapshot.world(), snapshot.x(), snapshot.y(), snapshot.z())
                    || snapshot.hologram().stream().anyMatch(line -> needsRespawn(positions.get(line.entityId()),
                    snapshot.world(), line.x(), line.y(), line.z()))) {
                hide(viewer, snapshot);
                removeEntities(viewer, npc.nametagIds());
                show(viewer, snapshot);
            } else {
                moveTo(viewer, positions, snapshot.entityId(), snapshot.world(), snapshot.x(), snapshot.y(), snapshot.z());
                for (HologramLine line : snapshot.hologram()) {
                    moveTo(viewer, positions, line.entityId(), snapshot.world(), line.x(), line.y(), line.z());
                }
                if (npc.lookChanged(viewer.getUniqueId(), snapshot.yaw(), snapshot.pitch())) {
                    backend.look(viewer, snapshot.entityId(), snapshot.yaw(), snapshot.pitch());
                }
            }
        });
    }

    public void dropPlayer(UUID playerId) {
        sentPositions.remove(playerId);
        for (NpcImpl npc : byId.values()) {
            npc.forget(playerId);
            npc.dropVisibility(playerId);
        }
    }

    private void forEachViewer(NpcImpl npc, BiConsumer<Player, NpcSnapshot> action) {
        for (UUID viewerId : npc.viewers()) {
            PlayerTracker.Tracked tracked = tracker.get(viewerId);
            if (tracked != null) {
                Player viewer = tracked.player();
                Schedulers.onEntity(plugin, viewer, () -> {
                    if (!closed && !npc.removed() && npc.viewers().contains(viewerId)) {
                        action.accept(viewer, npc.snapshot(viewerId));
                    }
                });
            }
        }
    }

    private final Map<String, PlayerGrid> playersByWorld = new java.util.HashMap<>();
    private final List<PlayerTracker.Tracked> scratch = new java.util.ArrayList<>();
    private final Set<UUID> forcedHandled = new HashSet<>();

    public void tick() {
        if (closed) {
            return;
        }
        long start = System.nanoTime();
        playersByWorld.clear();
        for (PlayerTracker.Tracked t : tracker.all()) {
            playersByWorld.computeIfAbsent(t.world(), key -> new PlayerGrid()).add(t);
        }
        for (NpcImpl npc : byId.values()) {
            if (!npc.removed()) {
                tick(npc);
            }
        }
        playersByWorld.clear();
        lastTickMillis = (System.nanoTime() - start) / 1_000_000.0;
    }

    private volatile double lastTickMillis;

    public double lastTickMillis() {
        return lastTickMillis;
    }

    public int viewerShows() {
        int total = 0;
        for (NpcImpl npc : byId.values()) {
            total += npc.viewers().size();
        }
        return total;
    }

    private void tick(NpcImpl npc) {
        npc.tickBehavior();
        if (npc.moving()) {
            double[] delta = npc.stepWalk(SECONDS_PER_PASS);
            if (delta != null) {
                walkStep(npc);
            }
        }
        if (npc.dueForNametagRefresh()) {
            updateNametag(npc);
        }
        Position pos = npc.position();
        double range = npc.viewDistance() > 0 ? npc.viewDistance() : viewDistance;
        range += npc.visibilityHysteresis();
        shouldSee.clear();
        boolean trackProximity = npc.hasProximityListeners();
        if (trackProximity) {
            nearNow.clear();
        }

        double proxRadius = trackProximity ? npc.proximityRadius() : 0.0;
        double proxSq = proxRadius * proxRadius;
        scratch.clear();
        List<PlayerTracker.Tracked> candidates = List.of();
        PlayerGrid sameWorld = playersByWorld.get(pos.world());
        if (sameWorld != null) {
            candidates = sameWorld.near(pos.x(), pos.z(), Math.max(range, proxRadius), scratch);
            if (npc.hasForcedVisibility()) {
                if (candidates != scratch) {
                    scratch.addAll(candidates);
                    candidates = scratch;
                }
                List<PlayerTracker.Tracked> withForced = scratch;
                forcedHandled.clear();
                for (PlayerTracker.Tracked near : withForced) {
                    forcedHandled.add(near.uuid());
                }
                npc.forEachForcedVisible(id -> {
                    PlayerTracker.Tracked forced = tracker.get(id);
                    if (forced != null && forced.world().equals(pos.world()) && forcedHandled.add(id)) {
                        withForced.add(forced);
                    }
                });
            }
        }
        for (PlayerTracker.Tracked t : candidates) {
            double distSq = pos.distanceSquared(t.x(), t.y(), t.z());
            if (trackProximity && distSq <= proxSq) {
                nearNow.add(t.uuid());
            }
            shouldSee.add(t.uuid());
            Schedulers.onEntity(plugin, t.player(), () -> evaluateVisibility(npc, t.player(), false));
        }

        for (Iterator<UUID> it = npc.viewers().iterator(); it.hasNext(); ) {
            UUID viewerId = it.next();
            if (!shouldSee.contains(viewerId)) {
                it.remove();
                npc.forgetLook(viewerId);
                PlayerTracker.Tracked t = tracker.get(viewerId);
                if (t != null) {
                    hideFrom(t.player(), npc);
                }
            }
        }

        if (trackProximity) {
            npc.syncProximity(nearNow,
                    playerId -> firePresence(npc, playerId, npc.nearCallback()),
                    playerId -> firePresence(npc, playerId, npc.leaveCallback()));
        }
    }

    private void firePresence(NpcImpl npc, UUID playerId, java.util.function.BiConsumer<net.folianpc.api.Npc, Player> callback) {
        if (callback == null) {
            return;
        }
        PlayerTracker.Tracked t = tracker.get(playerId);
        if (t == null) {
            return;
        }
        Player viewer = t.player();
        Schedulers.onEntity(plugin, viewer, () -> {
            if (!closed && !npc.removed()) {
                guard(npc, () -> callback.accept(npc, viewer));
            }
        });
    }

    private static final double MAX_INTERACT_DISTANCE_SQUARED = 10.0 * 10.0;

    private boolean handleInteract(org.bukkit.entity.Player viewer, int entityId,
                                   net.folianpc.api.ClickType type, boolean sneaking) {
        NpcImpl npc = byEntityId.get(entityId);
        if (npc == null) {
            return false;
        }
        PlayerTracker.Tracked t = tracker.get(viewer.getUniqueId());
        if (closed || npc.removed() || t == null || !npc.viewers().contains(viewer.getUniqueId())
                || !t.world().equals(npc.position().world())
                || npc.position().distanceSquared(t.x(), t.y(), t.z()) > MAX_INTERACT_DISTANCE_SQUARED) {
            return true;
        }
        Schedulers.onEntity(plugin, viewer, () -> interact(viewer, npc, type, sneaking));
        return true;
    }

    private void interact(Player viewer, NpcImpl npc, net.folianpc.api.ClickType type, boolean sneaking) {
        UUID viewerId = viewer.getUniqueId();
        PlayerTracker.Tracked tracked = tracker.get(viewerId);
        Position pos = npc.position();
        if (closed || npc.removed() || byEntityId.get(npc.entityId()) != npc || tracked == null
                || !npc.viewers().contains(viewerId) || !tracked.world().equals(pos.world())
                || pos.distanceSquared(tracked.x(), tracked.y(), tracked.z()) > MAX_INTERACT_DISTANCE_SQUARED
                || !npc.visibleTo(viewer, true) || !npc.allowInteract(viewerId, clock.getAsLong())) {
            return;
        }
        NpcInteractEvent event = new NpcInteractEvent(viewer, npc, type, sneaking);
        events.accept(event);
        if (event.isCancelled() || closed || npc.removed()) {
            return;
        }
        NpcClickListener listener = npc.clickListener();
        if (listener != null) {
            guard(npc, () -> listener.onClick(viewer, npc, type));
        }
        runActions(viewer, npc, type, sneaking);
    }

    private void guard(NpcImpl npc, Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            if (plugin != null && plugin.getLogger() != null) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "FoliaNPC: click handler on '" + npc.name() + "' threw", t);
            }
        }
    }

    private void runActions(Player viewer, NpcImpl npc, net.folianpc.api.ClickType type, boolean sneaking) {
        var entries = npc.actions(type);
        if (entries.isEmpty()) {
            return;
        }
        ClickContext ctx = new ClickContext(plugin, viewer, npc, type, sneaking, async);
        for (NpcImpl.ActionEntry entry : entries) {
            if (ctx.remainingCancelled()) {
                return;
            }
            Schedulers.onEntityLater(plugin, viewer, () -> {
                if (!closed && !npc.removed() && !ctx.remainingCancelled()) {
                    guard(npc, () -> entry.action().run(ctx));
                }
            }, entry.delayTicks());
        }
    }

    public boolean closed() {
        return closed;
    }

    public void close() {
        List<NpcImpl> ending;
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            closed = true;
            ending = List.copyOf(byId.values());
            byId.clear();
            byEntityId.clear();
        }
        for (NpcImpl npc : ending) {
            npc.markRemoved();
            NpcSnapshot snapshot = npc.snapshot();
            int[] lineIds = npc.nametagIds();
            for (UUID viewerId : npc.viewers()) {
                PlayerTracker.Tracked t = tracker.get(viewerId);
                if (t != null) {
                    hideNow(t.player(), snapshot, lineIds);
                }
            }
            npc.viewers().clear();
        }
        sentPositions.clear();
    }

    private void hideNow(Player viewer, NpcSnapshot snapshot, int[] lineIds) {
        try {
            hide(viewer, snapshot);
            if (lineIds.length > 0) {
                removeEntities(viewer, lineIds);
            }
        } catch (RuntimeException failure) {
            log("could not hide '" + snapshot.name() + "' from " + viewer.getName() + " on close: " + failure);
        }
    }
}
