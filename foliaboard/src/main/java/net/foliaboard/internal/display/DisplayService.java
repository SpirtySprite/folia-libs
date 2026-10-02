package net.foliaboard.internal.display;

import net.foliaboard.api.display.DisplayStats;
import net.foliaboard.api.display.DisplayStyle;
import net.foliaboard.api.display.DisplayVisibility;
import net.foliaboard.api.display.ManagedNametag;
import net.foliaboard.api.display.NametagLayout;
import net.foliaboard.api.display.NametagProfile;
import net.foliaboard.api.display.NametagProfiles;
import net.foliaboard.api.display.NametagRenderer;
import net.foliaboard.api.display.NametagStatus;
import net.foliaboard.api.display.NametagEvent;
import net.foliaboard.api.display.NametagLayer;
import net.foliaboard.api.display.Displays;
import net.foliaboard.api.display.ManagedItemDisplay;
import net.foliaboard.api.display.ManagedTextDisplay;
import net.foliaboard.api.display.TextDisplayStyle;
import net.foliaboard.internal.scheduler.Schedulers;
import net.foliaboard.internal.service.BoardRuntime;
import net.foliacommons.scheduler.TaskHandle;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

public final class DisplayService implements Displays {
    private record Anchor(UUID world, double x, double y, double z, float yaw, float pitch,
                          int vehicle, float correction, Set<UUID> tracked, List<Integer> passengers,
                          boolean invisible, boolean sneaking, boolean spectator, long sampled) {
    }

    private final BoardRuntime runtime;
    private final DisplayTransport transport;
    private final Map<UUID, Handle> handles = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Target> targets = new HashMap<>();
    private final LongAdder providerFailures = new LongAdder();
    private final LongAdder transportFailures = new LongAdder();
    private final Map<String, NametagProfile> profiles = new HashMap<>();
    private Function<Player, AutoCloseable> vanillaLease = player -> { throw new UnsupportedOperationException("Vanilla name leasing is unavailable"); };
    private boolean started;
    private boolean closed;

    public DisplayService(BoardRuntime runtime) {
        this.runtime = runtime;
        DisplayTransport backend;
        try {
            backend = new NmsDisplayTransport(runtime.metrics(), this::transportFailure);
        } catch (RuntimeException failure) {
            runtime.plugin().getLogger().warning("FoliaBoard displays unavailable: " + failure.getMessage());
            backend = null;
        }
        this.transport = backend;
    }

    public DisplayService(BoardRuntime runtime, DisplayTransport transport) {
        this.runtime = runtime;
        this.transport = transport;
    }

    @Override
    public synchronized boolean supported() {
        return transport != null && transport.supported();
    }

    @Override
    public synchronized ManagedTextDisplay text(Location location, Component text) {
        ensureOpen();
        TextHandle handle = new TextHandle(Objects.requireNonNull(text, "text"));
        ((Handle) handle).fixed = fixed(location);
        register(handle);
        return handle;
    }

    @Override
    public synchronized ManagedTextDisplay nametag(Player player, Component text) {
        ensureOpen();
        TextHandle handle = new TextHandle(Objects.requireNonNull(text, "text"));
        attach(handle, player, 0.25);
        register(handle);
        return handle;
    }

    @Override
    public synchronized ManagedItemDisplay item(Location location, ItemStack item) {
        ensureOpen();
        ItemHandle handle = new ItemHandle(Objects.requireNonNull(item, "item").clone());
        ((Handle) handle).fixed = fixed(location);
        register(handle);
        return handle;
    }

    @Override
    public synchronized ManagedItemDisplay item(Player player, double gap, ItemStack item) {
        ensureOpen();
        ItemHandle handle = new ItemHandle(Objects.requireNonNull(item, "item").clone());
        attach(handle, player, gap);
        register(handle);
        return handle;
    }

    public synchronized void vanillaLeases(Function<Player, AutoCloseable> factory) {
        vanillaLease = Objects.requireNonNull(factory, "factory");
    }

    @Override
    public ManagedNametag<Boolean> nametag(Entity entity, NametagProfile profile) {
        return nametag(entity, profile, owner -> Boolean.TRUE, (viewer, snapshot, current) -> current.layout());
    }

    @Override
    public synchronized <T> ManagedNametag<T> nametag(Entity entity, NametagProfile profile,
            Function<Entity, T> sampler, NametagRenderer<T> renderer) {
        ensureOpen();
        Composition<T> handle = new Composition<>(Objects.requireNonNull(profile, "profile"),
                Objects.requireNonNull(sampler, "sampler"), Objects.requireNonNull(renderer, "renderer"));
        attach(handle, entity, 0);
        register(handle);
        return handle;
    }

    @Override
    public NametagProfiles profiles() {
        return new NametagProfiles() {
            @Override public void register(String name, NametagProfile profile) {
                synchronized (DisplayService.this) {
                    ensureOpen();
                    if (Objects.requireNonNull(name, "name").isBlank()) throw new IllegalArgumentException("Profile name cannot be blank");
                    profiles.put(name, Objects.requireNonNull(profile, "profile"));
                }
            }
            @Override public Optional<NametagProfile> find(String name) {
                synchronized (DisplayService.this) { return Optional.ofNullable(profiles.get(Objects.requireNonNull(name, "name"))); }
            }
            @Override public boolean remove(String name) {
                synchronized (DisplayService.this) { ensureOpen(); return profiles.remove(Objects.requireNonNull(name, "name")) != null; }
            }
            @Override public Set<String> names() { synchronized (DisplayService.this) { return Set.copyOf(profiles.keySet()); } }
        };
    }

    private void ensureOpen() {
        runtime.ensureOpen();
        if (closed) throw new IllegalStateException("Display service is closed");
        if (!supported()) throw new UnsupportedOperationException("Display packets are unavailable; inspect diagnose()");
    }

    private void register(Handle handle) {
        runtime.metrics().requested(net.foliaboard.api.PresentationStats.Surface.DISPLAY);
        handles.put(handle.id, handle);
        if (handle.owner != null) target(handle.owner);
        if (handle.closed) return;
        if (!started) {
            started = true;
            Schedulers.global(runtime.plugin(), () -> {
                synchronized (DisplayService.this) {
                    if (closed || !started || handles.isEmpty()) return;
                    for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) onJoin(player);
                }
            });
        }
    }

    public synchronized void onJoin(Player player) {
        if (closed || runtime.closed() || !supported() || !started && handles.isEmpty()) return;
        UUID id = player.getUniqueId();
        for (Handle handle : List.copyOf(handles.values())) {
            if (handle instanceof Composition<?> composition && composition.waitingOwner != null && composition.waitingOwner.equals(id)) {
                handle.owner = player; handle.generation++; handle.revision++; composition.waitingOwner = null;
                composition.forceSample = true; composition.views.clear(); composition.updateLease();
            }
        }
        Session current = sessions.get(id);
        if (current != null && current.player == player) return;
        if (current != null) retire(current);
        if (handles.isEmpty()) return;
        Session session = new Session(player);
        sessions.put(id, session);
        session.timer = Schedulers.entityTaskTimer(runtime.plugin(), player, timer -> {
            try {
                tick(session);
            } catch (RuntimeException failure) {
                transportFailure(failure);
            }
        }, () -> {
            synchronized (DisplayService.this) {
                if (!session.ended) retire(session);
            }
        }, 1, 1);
        if (session.timer.isCancelled() && !session.ended) retire(session);
    }

    public synchronized void onTransition(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.player != player) return;
        transport.release(player);
        session.epoch++;
        session.anchor = null;
        session.position = null;
        session.settleTicks = 2;
        for (Handle handle : handles.values()) {
            if (handle.owner == player) handle.generation++;
        }
        if (session.connection != null) session.connection.present(List.of());
    }

    public synchronized void onQuit(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.player == player) retire(session);
        for (Handle handle : List.copyOf(handles.values())) {
            if (handle.owner == player) handle.close();
        }
    }

    private void retire(Session session) {
        session.ended = true;
        session.epoch++;
        session.anchor = null;
        sessions.remove(session.player.getUniqueId(), session);
        session.timer.cancel();
        transport.release(session.player);
        if (session.connection != null) session.connection.close();
        for (Handle handle : List.copyOf(handles.values())) {
            handle.eligible.remove(session.player.getUniqueId());
            if (handle instanceof Composition<?> composition) composition.views.remove(session.player.getUniqueId());
            if (handle.owner == session.player) {
                if (handle instanceof Composition<?> composition && composition.persistent && !closed) {
                    composition.waitingOwner = session.player.getUniqueId();
                    handle.owner = null; handle.generation++; handle.revision++;
                    composition.data = null; composition.views.clear(); composition.closeLease();
                } else handle.close();
            }
        }
    }

    private synchronized void tick(Session session) {
        if (closed || runtime.closed() || session.ended || sessions.get(session.player.getUniqueId()) != session) return;
        Player viewer = session.player;
        if (!viewer.isOnline()) {
            retire(session);
            return;
        }
        if (session.settleTicks > 0) {
            session.settleTicks--;
            session.anchor = null;
            if (session.connection != null) session.connection.present(List.of());
            return;
        }
        Location position = viewer.getLocation();
        UUID world = position.getWorld().getUID();
        session.position = fixed(position);
        capture(session);
        if (session.connection != null && session.connection.isClosed()) {
            session.connection.close();
            session.connection = null;
            session.retryTicks = 20;
        }
        if (session.retryTicks > 0) session.retryTicks--;
        if (session.connection == null && session.retryTicks == 0 && !handles.isEmpty()) {
            try {
                session.connection = transport.connect(viewer);
            } catch (RuntimeException failure) {
                session.retryTicks = 20;
                transportFailure(failure);
            }
        }
        if (session.connection == null) return;
        List<DisplayFrame> frames = new ArrayList<>();
        for (Handle handle : List.copyOf(handles.values())) {
            handle.eligible.remove(viewer.getUniqueId());
            Anchor anchor = handle.fixed;
            Target ownerSession = handle.owner == null ? null : findTarget(handle.owner);
            if (handle.owner != null) {
                anchor = ownerSession == null || ownerSession.entity != handle.owner ? null : ownerSession.anchor;
            }
            if (handle instanceof Composition<?> composition) {
                frames.addAll(composition.render(session, ownerSession, anchor, position));
                continue;
            }
            if (anchor == null || !allowed(handle, session, anchor, position)) continue;
            long generation = handle.generation;
            long revision = handle.revision;
            long viewerEpoch = session.epoch;
            long ownerEpoch = ownerSession == null ? 0 : ownerSession.epoch;
            try {
                if (!handle.predicate.test(viewer)) continue;
                Component text = handle instanceof TextHandle textHandle ? textHandle.resolve(viewer) : null;
                ItemStack item = handle instanceof ItemHandle itemHandle ? itemHandle.resolve(viewer) : null;
                DisplayStyle renderStyle = Objects.requireNonNull(handle.styleProvider == null ? handle.style
                        : handle.styleProvider.apply(viewer), "provider.style");
                TextDisplayStyle renderText = handle instanceof TextHandle textHandle ? textHandle.resolveStyle(viewer)
                        : TextDisplayStyle.defaults();
                if (!accepted(handle, session, ownerSession, anchor, revision, viewerEpoch, ownerEpoch)) continue;
                Anchor captured = anchor;
                Target capturedOwner = ownerSession;
                frames.add(new DisplayFrame(handle.id, generation, anchor.world, anchor.x,
                        anchor.y, anchor.z, anchor.yaw, anchor.pitch, anchor.vehicle,
                        anchor.correction + (float) handle.gap, text, item,
                        handle instanceof ItemHandle itemHandle ? itemHandle.transform : ItemDisplay.ItemDisplayTransform.NONE,
                        renderStyle, renderText,
                        handle.visibility.range(), () -> eligible(handle, session) && accepted(handle, session, capturedOwner, captured,
                                revision, viewerEpoch, ownerEpoch),
                        () -> retained(handle, session, capturedOwner, captured, generation, viewerEpoch, ownerEpoch), anchor.passengers));
                handle.eligible.add(viewer.getUniqueId());
            } catch (RuntimeException failure) {
                providerFailures.increment();
            }
        }
        session.connection.present(frames);
    }

    private boolean allowed(Handle handle, Session viewer, Anchor anchor, Location position) {
        DisplayVisibility policy = handle.visibility;
        UUID viewerId = viewer.player.getUniqueId();
        if (handle.closed || !handle.visible || handle.hidden.contains(viewerId)
                || !anchor.world.equals(position.getWorld().getUID())) return false;
        if (handle.owner != null) {
            boolean self = handle.owner.getUniqueId().equals(viewerId);
            if (self && !policy.selfVisible() || !self && !anchor.tracked.contains(viewerId)
                    || !canSee(viewer.player, handle.owner)) return false;
            if (System.nanoTime() - anchor.sampled > 1_000_000_000L
                    || policy.hideInvisible() && anchor.invisible || policy.hideSneaking() && anchor.sneaking
                    || policy.hideSpectators() && anchor.spectator) return false;
        }
        double dx = anchor.x - position.getX();
        double dy = anchor.y - position.getY();
        double dz = anchor.z - position.getZ();
        return dx * dx + dy * dy + dz * dz <= policy.range() * policy.range();
    }

    private synchronized boolean retained(Handle handle, Session viewer, Target owner, Anchor anchor,
                                          long generation, long viewerEpoch, long ownerEpoch) {
        return handle.generation == generation && eligible(handle, viewer)
                && accepted(handle, viewer, owner, anchor, handle.revision, viewerEpoch, ownerEpoch);
    }

    private synchronized boolean eligible(Handle handle, Session session) {
        return handle.eligible.contains(session.player.getUniqueId());
    }

    private synchronized boolean accepted(Handle handle, Session viewer, Target owner, Anchor anchor,
                                          long generation, long viewerEpoch, long ownerEpoch) {
        if (!current(handle, viewer, owner, generation, viewerEpoch, ownerEpoch)
                || handle.hidden.contains(viewer.player.getUniqueId()) || !handle.visible || viewer.position == null) return false;
        Anchor latest = owner == null ? anchor : owner.anchor;
        if (latest == null || !latest.world.equals(viewer.position.world)) return false;
        if (owner != null) {
            DisplayVisibility policy = handle.visibility;
            boolean self = owner.entity.getUniqueId().equals(viewer.player.getUniqueId());
            if (self && !policy.selfVisible() || !self && !latest.tracked.contains(viewer.player.getUniqueId())
                    || policy.hideInvisible() && latest.invisible || policy.hideSneaking() && latest.sneaking
                    || policy.hideSpectators() && latest.spectator
                    || System.nanoTime() - latest.sampled > 1_000_000_000L) return false;
        }
        double dx = latest.x - viewer.position.x;
        double dy = latest.y - viewer.position.y;
        double dz = latest.z - viewer.position.z;
        return dx * dx + dy * dy + dz * dz <= handle.visibility.range() * handle.visibility.range();
    }

    private boolean current(Handle handle, Session viewer, Target owner, long generation, long viewerEpoch, long ownerEpoch) {
        return !closed && !runtime.closed() && !handle.closed && handles.get(handle.id) == handle
                && handle.revision == generation && !viewer.ended && viewer.epoch == viewerEpoch
                && sessions.get(viewer.player.getUniqueId()) == viewer
                && (owner == null || !owner.ended && owner.epoch == ownerEpoch
                && findTarget(owner.entity) == owner);
    }

    private void attach(Handle handle, Entity player, double gap) {
        Objects.requireNonNull(player, "player");
        if (!Double.isFinite(gap) || Math.abs(gap) > 64) throw new IllegalArgumentException("Gap must be finite and within 64 blocks");
        handle.owner = player;
        handle.gap = gap;
        handle.fixed = null;
        handle.generation++;
        if (handles.containsKey(handle.id)) target(player);
        pruneTargets();
    }

    private static boolean canSee(Player viewer, Entity entity) {
        return entity instanceof Player player ? viewer.canSee(player) : viewer.canSee(entity);
    }

    private Target findTarget(Entity entity) {
        return entity instanceof Player ? sessions.get(entity.getUniqueId()) : targets.get(entity.getUniqueId());
    }

    private void target(Entity entity) {
        if (entity instanceof Player player) { onJoin(player); return; }
        Target existing = targets.get(entity.getUniqueId());
        if (existing != null && existing.entity == entity && !existing.ended) return;
        if (existing != null) retireTarget(existing);
        Target target = new Target(entity);
        targets.put(entity.getUniqueId(), target);
        target.timer = Schedulers.entityTaskTimer(runtime.plugin(), entity, timer -> {
            synchronized (DisplayService.this) {
                if (closed || target.ended) return;
                if (!entity.isValid() || entity.isDead()) { retireTarget(target); return; }
                if (target.settleTicks > 0) { target.settleTicks--; return; }
                try { capture(target); } catch (RuntimeException failure) { transportFailure(failure); target.anchor = null; }
            }
        }, () -> { synchronized (DisplayService.this) { retireTarget(target); } }, 1, 1);
        if (target.timer.isCancelled()) retireTarget(target);
    }

    private void pruneTargets() {
        for (Target target : List.copyOf(targets.values())) {
            if (handles.values().stream().noneMatch(handle -> handle.owner == target.entity)) retireTarget(target);
        }
    }

    private void retireTarget(Target target) {
        if (target.ended) return;
        target.ended = true; target.epoch++; target.anchor = null; target.timer.cancel();
        targets.remove(target.entity.getUniqueId(), target);
        transport.release(target.entity);
        for (Handle handle : List.copyOf(handles.values())) if (handle.owner == target.entity) handle.close();
    }

    public synchronized void onTransition(Entity entity) {
        Target target = findTarget(entity);
        if (target == null || target.entity != entity) return;
        transport.release(entity);
        target.epoch++; target.anchor = null; target.settleTicks = 2;
        for (Handle handle : handles.values()) if (handle.owner == entity) handle.generation++;
    }

    private void capture(Target target) {
        Entity entity = target.entity;
        if (!entity.isValid() || entity.isDead()) { target.anchor = null; return; }
        Location position = entity.getLocation();
        UUID world = position.getWorld().getUID();
        if (target.anchor != null && (!world.equals(target.anchor.world) || entity.getEntityId() != target.anchor.vehicle)) {
            target.epoch++;
            transport.release(entity);
        }
        Set<UUID> tracked = new HashSet<>();
        for (Player watcher : entity.getTrackedBy()) tracked.add(watcher.getUniqueId());
        List<Integer> passengers = entity.getPassengers().stream().map(Entity::getEntityId).toList();
        target.anchor = new Anchor(world, position.getX(), position.getY(), position.getZ(), 0, 0,
                entity.getEntityId(), transport.mountCorrection(entity), Set.copyOf(tracked), passengers,
                entity instanceof LivingEntity living && living.isInvisible(), entity instanceof Player player && player.isSneaking(),
                entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR, System.nanoTime());
        for (Handle handle : List.copyOf(handles.values())) {
            if (handle.owner == entity && handle instanceof Composition<?> composition) composition.sample();
        }
    }

    @Override
    public synchronized ManagedTextDisplay text(Entity entity, double gap, Component text) {
        ensureOpen(); TextHandle handle = new TextHandle(Objects.requireNonNull(text, "text"));
        attach(handle, entity, gap); register(handle); return handle;
    }

    @Override
    public synchronized ManagedItemDisplay item(Entity entity, double gap, ItemStack item) {
        ensureOpen(); ItemHandle handle = new ItemHandle(Objects.requireNonNull(item, "item").clone());
        attach(handle, entity, gap); register(handle); return handle;
    }

    private static Anchor fixed(Location location) {
        Location copy = Objects.requireNonNull(location, "location").clone();
        copy.checkFinite();
        UUID world = Objects.requireNonNull(copy.getWorld(), "location.world").getUID();
        return new Anchor(world, copy.getX(), copy.getY(), copy.getZ(), copy.getYaw(), copy.getPitch(),
                -1, 0, Set.of(), List.of(), false, false, false, 0);
    }

    private void transportFailure(Throwable failure) {
        transportFailures.increment();
        if (transportFailures.sum() == 1) {
            runtime.plugin().getLogger().warning("FoliaBoard display transport failed: " + failure.getMessage());
        }
    }

    @Override
    public synchronized DisplayStats stats() {
        return new DisplayStats(handles.size(), sessions.size(), sessions.values().stream()
                .mapToInt(value -> value.connection == null ? 0 : value.connection.entities()).sum(),
                providerFailures.sum(), transportFailures.sum());
    }

    public synchronized void closeAll() {
        if (closed) return;
        closed = true;
        for (Handle handle : List.copyOf(handles.values())) handle.close();
        for (Session session : List.copyOf(sessions.values())) retire(session);
        for (Target target : List.copyOf(targets.values())) retireTarget(target);
        profiles.clear();
    }

    private class Target {
        final Entity entity;
        TaskHandle timer = TaskHandle.NOOP;
        Anchor anchor;
        long epoch;
        boolean ended;
        int settleTicks;
        Target(Entity entity) { this.entity = entity; }
    }

    private final class Session extends Target {
        private final Player player;
        private DisplayTransport.Connection connection;
        private Anchor position;
        private int retryTicks;

        private Session(Player player) {
            super(player);
            this.player = player;
        }
    }

    private abstract class Handle implements net.foliaboard.api.display.ManagedDisplay {
        private final UUID id = UUID.randomUUID();
        private final Set<UUID> hidden = new HashSet<>();
        private final Set<UUID> eligible = new HashSet<>();
        private DisplayStyle style = DisplayStyle.defaults();
        private Function<Player, DisplayStyle> styleProvider;
        private boolean customStyle;
        private DisplayVisibility visibility = DisplayVisibility.defaults();
        private Predicate<Player> predicate = player -> true;
        private Entity owner;
        private double gap;
        private Anchor fixed;
        private long generation;
        private long revision;
        private boolean visible = true;
        private boolean closed;

        @Override
        public UUID id() {
            return id;
        }

        private void mutate(Runnable change) {
            synchronized (DisplayService.this) {
                ensureOpen();
                if (closed) throw new IllegalStateException("Display is closed");
                change.run();
                revision++;
                runtime.metrics().requested(net.foliaboard.api.PresentationStats.Surface.DISPLAY);
            }
        }

        @Override
        public DisplayStyle style() {
            synchronized (DisplayService.this) {
                return style;
            }
        }

        @Override
        public DisplayVisibility visibility() {
            synchronized (DisplayService.this) {
                return visibility;
            }
        }

        @Override
        public boolean isVisible() {
            synchronized (DisplayService.this) {
                return visible;
            }
        }

        @Override
        public void style(DisplayStyle value) {
            mutate(() -> {
                customStyle = true;
                style = Objects.requireNonNull(value, "style");
                styleProvider = null;
            });
        }

        @Override
        public void styleFor(Function<Player, DisplayStyle> value) {
            mutate(() -> { customStyle = true; styleProvider = Objects.requireNonNull(value, "provider"); });
        }

        @Override
        public void visibility(DisplayVisibility value) {
            mutate(() -> {
                visibility = Objects.requireNonNull(value, "visibility");
                generation++;
            });
        }

        @Override
        public void hide(UUID viewer) {
            mutate(() -> hidden.add(Objects.requireNonNull(viewer, "viewer")));
        }

        @Override
        public void show(UUID viewer) {
            mutate(() -> hidden.remove(Objects.requireNonNull(viewer, "viewer")));
        }

        @Override
        public void visible(boolean value) {
            mutate(() -> visible = value);
        }

        @Override
        public void viewers(Predicate<Player> value) {
            mutate(() -> {
                predicate = Objects.requireNonNull(value, "predicate");
                generation++;
            });
        }

        @Override
        public void location(Location value) {
            Anchor copy = fixed(value);
            mutate(() -> {
                if (owner != null || fixed == null || !fixed.world.equals(copy.world)) generation++;
                fixed = copy;
                owner = null;
                pruneTargets();
            });
        }

        @Override
        public void attach(Player player, double gap) {
            attach((Entity) player, gap);
        }

        @Override
        public void attach(Entity entity, double gap) {
            mutate(() -> DisplayService.this.attach(this, entity, gap));
        }

        @Override
        public void refresh() {
            mutate(() -> generation++);
        }

        @Override
        public Set<UUID> viewers() {
            synchronized (DisplayService.this) {
                return Set.copyOf(eligible);
            }
        }

        @Override
        public boolean isClosed() {
            synchronized (DisplayService.this) {
                return closed;
            }
        }

        @Override
        public void close() {
            synchronized (DisplayService.this) {
                if (closed) return;
                closed = true;
                if (this instanceof Composition<?> composition) composition.dispose();
                generation++;
                handles.remove(id, this);
                hidden.clear();
                eligible.clear();
                owner = null;
                fixed = null;
                pruneTargets();
                if (handles.isEmpty()) {
                    started = false;
                    for (Session session : List.copyOf(sessions.values())) retire(session);
                    for (Target target : List.copyOf(targets.values())) retireTarget(target);
                }
            }
        }
    }

    private final class Composition<T> extends Handle implements ManagedNametag<T> {
        private NametagProfile base;
        private final Function<Entity, T> sampler;
        private NametagRenderer<T> renderer;
        private T data;
        private long ownerTick;
        private boolean forceSample = true;
        private boolean locked;
        private final Set<UUID> lockedOwners = new HashSet<>();
        private boolean persistent;
        private UUID waitingOwner;
        private boolean replace;
        private AutoCloseable lease;
        private Consumer<NametagEvent> listener = event -> {};
        private final Map<UUID, View> views = new HashMap<>();
        private final List<Layer> layers = new ArrayList<>();
        private long layerSequence;

        private Composition(NametagProfile profile, Function<Entity, T> sampler, NametagRenderer<T> renderer) {
            this.base = profile; this.sampler = sampler; this.renderer = renderer;
            ((Handle) this).visibility = profile.visibility();
        }

        private NametagProfile active() {
            Layer best = null;
            for (Layer layer : layers) {
                if (!layer.ended && (best == null || layer.priority > best.priority
                        || layer.priority == best.priority && layer.sequence > best.sequence)) best = layer;
            }
            return best == null ? base : best.profile;
        }

        private void sample() {
            ownerTick++;
            if (!forceSample && data != null && ownerTick % active().refresh().ownerTicks() != 0) return;
            forceSample = false;
            Handle handle = this;
            Entity owner = handle.owner;
            long generation = handle.generation;
            try {
                T sampled = Objects.requireNonNull(sampler.apply(owner), "owner.snapshot");
                if (!handle.closed && handle.owner == owner && handle.generation == generation) {
                    data = sampled;
                    emit(NametagEvent.Type.SAMPLED, null, new NametagStatus(NametagStatus.Reason.ELIGIBLE, 0));
                }
            } catch (RuntimeException failure) {
                data = null; providerFailures.increment();
                emit(NametagEvent.Type.FAILURE, null, new NametagStatus(NametagStatus.Reason.FAILURE, 0));
            }
        }

        private NametagStatus.Reason reason(Session viewer, Anchor anchor, Location position, NametagProfile profile) {
            Handle handle = this;
            UUID id = viewer.player.getUniqueId();
            if (handle.closed) return NametagStatus.Reason.CLOSED;
            if (waitingOwner != null) return NametagStatus.Reason.OFFLINE;
            if (anchor == null || handle.owner != null && System.nanoTime() - anchor.sampled > 1_000_000_000L) return NametagStatus.Reason.OWNER_UNAVAILABLE;
            if (lockedOwners.contains(id) || handle.owner != null && !profile.visibility().selfVisible() && handle.owner.getUniqueId().equals(id)) return NametagStatus.Reason.SELF_HIDDEN;
            if (handle.hidden.contains(id)) return NametagStatus.Reason.EXCLUDED;
            if (!anchor.world.equals(position.getWorld().getUID())) return NametagStatus.Reason.WORLD;
            if (handle.owner != null && !handle.owner.getUniqueId().equals(id) && !anchor.tracked.contains(id)) return NametagStatus.Reason.TRACKING;
            if (handle.owner != null && !canSee(viewer.player, handle.owner)) return NametagStatus.Reason.VANISHED;
            if (profile.visibility().hideInvisible() && anchor.invisible) return NametagStatus.Reason.INVISIBLE;
            if (profile.visibility().hideSneaking() && anchor.sneaking) return NametagStatus.Reason.SNEAKING;
            if (profile.visibility().hideSpectators() && anchor.spectator) return NametagStatus.Reason.SPECTATOR;
            if (distance(anchor, position) > profile.visibility().range()) return NametagStatus.Reason.RANGE;
            if (data == null) return NametagStatus.Reason.NO_OWNER_DATA;
            if (!handle.visible) return NametagStatus.Reason.GLOBAL_HIDDEN;
            if (!handle.predicate.test(viewer.player)) return NametagStatus.Reason.FILTER;
            return NametagStatus.Reason.ELIGIBLE;
        }

        private double distance(Anchor anchor, Location position) {
            double x = anchor.x - position.getX(), y = anchor.y - position.getY(), z = anchor.z - position.getZ();
            return Math.sqrt(x*x+y*y+z*z);
        }

        private List<DisplayFrame> render(Session viewer, Target owner, Anchor anchor, Location position) {
            Handle handle = this;
            UUID viewerId = viewer.player.getUniqueId();
            View view = views.computeIfAbsent(viewerId, id -> new View());
            handle.eligible.remove(viewerId);
            view.tick++;
            NametagProfile profile = active();
            NametagStatus.Reason reason;
            long revision = handle.revision;
            try { reason = reason(viewer, anchor, position, profile); }
            catch (RuntimeException failure) { reason = NametagStatus.Reason.FAILURE; providerFailures.increment(); }
            boolean eligible = reason == NametagStatus.Reason.ELIGIBLE;
            boolean fadeOut = reason == NametagStatus.Reason.GLOBAL_HIDDEN || reason == NametagStatus.Reason.FILTER;
            if (!eligible && !fadeOut) {
                view.alpha = 0; view.layout = NametagLayout.empty();
                status(view, viewerId, reason, 0); return List.of();
            }
            if (eligible && (view.revision != revision || view.tick >= view.nextRender)) {
                try {
                    view.layout = Objects.requireNonNull(renderer.render(viewer.player, data, profile), "renderer.layout");
                    if (handle.closed || handle.revision != revision) return List.of();
                    view.revision = revision;
                    view.nextRender = view.tick + profile.refresh().viewerTicks();
                } catch (RuntimeException failure) {
                    providerFailures.increment(); view.layout = NametagLayout.empty(); view.alpha = 0;
                    status(view, viewerId, NametagStatus.Reason.FAILURE, 0);
                    emit(NametagEvent.Type.FAILURE, viewerId, view.status); return List.of();
                }
            }
            int duration = eligible ? profile.transition().fadeInTicks() : profile.transition().fadeOutTicks();
            view.alpha = duration == 0 ? eligible ? 1 : 0 : Math.max(0, Math.min(1, view.alpha + (eligible ? 1.0 : -1.0) / duration));
            if (view.alpha <= 0 || anchor == null) { status(view, viewerId, reason, 0); return List.of(); }
            double distance = distance(anchor, position);
            double opacity = view.alpha * profile.distance().opacity(distance);
            if (opacity <= 0) { status(view, viewerId, NametagStatus.Reason.EMPTY, 0); return List.of(); }
            float scale = profile.distance().scale(distance);
            long generation = handle.generation, viewerEpoch = viewer.epoch, ownerEpoch = owner == null ? 0 : owner.epoch;
            List<DisplayFrame> result = new ArrayList<>();
            try {
                DisplayStyle override = handle.styleProvider == null ? handle.style : Objects.requireNonNull(handle.styleProvider.apply(viewer.player), "provider.style");
                for (NametagLayout.Element element : view.layout.elements()) {
                    if (distance < element.minDistance() || distance >= element.maxDistance()) continue;
                    DisplayStyle style = handle.customStyle ? override : element.style();
                    var transform = style.transform();
                    float factor = element instanceof NametagLayout.Item ? (float) (scale * opacity) : scale;
                    style = style.transformed(transform.scaled(transform.scale().x()*factor, transform.scale().y()*factor, transform.scale().z()*factor));
                    Component text = null; ItemStack item = null;
                    TextDisplayStyle textStyle = TextDisplayStyle.defaults();
                    ItemDisplay.ItemDisplayTransform itemTransform = ItemDisplay.ItemDisplayTransform.NONE;
                    if (element instanceof NametagLayout.Text line) {
                        text = line.text();
                        textStyle = line.textStyle().toBuilder().opacity((int) Math.round(line.textStyle().opacity()*opacity)).build();
                    } else if (element instanceof NametagLayout.Item badge) { item = badge.item(); itemTransform = badge.transform(); }
                    UUID id = UUID.nameUUIDFromBytes((handle.id + ":" + element.key()).getBytes(StandardCharsets.UTF_8));
                    result.add(new DisplayFrame(id, generation, anchor.world, anchor.x, anchor.vehicle < 0 ? anchor.y + handle.gap + element.gap() : anchor.y, anchor.z, anchor.yaw, anchor.pitch,
                            anchor.vehicle, anchor.correction + (float) (handle.gap + element.gap()), text, item, itemTransform,
                            style, textStyle, profile.visibility().range(),
                            () -> gate(viewer, owner, revision, generation, viewerEpoch, ownerEpoch, true),
                            () -> gate(viewer, owner, revision, generation, viewerEpoch, ownerEpoch, false), anchor.passengers));
                }
            } catch (RuntimeException failure) { providerFailures.increment(); status(view, viewerId, NametagStatus.Reason.FAILURE, 0); return List.of(); }
            if (!result.isEmpty()) handle.eligible.add(viewerId);
            if (view.generation != generation || view.ownerEpoch != ownerEpoch || view.viewerEpoch != viewerEpoch) {
                if (view.generation >= 0) emit(NametagEvent.Type.RECREATED, viewerId, new NametagStatus(reason, result.size()));
                view.generation = generation; view.ownerEpoch = ownerEpoch; view.viewerEpoch = viewerEpoch;
            }
            status(view, viewerId, result.isEmpty() ? NametagStatus.Reason.EMPTY : reason, result.size());
            return result;
        }

        private boolean gate(Session viewer, Target owner, long revision, long generation, long viewerEpoch, long ownerEpoch, boolean queued) {
            synchronized (DisplayService.this) {
                Handle handle = this;
                if (!current(handle, viewer, owner, queued ? revision : handle.revision, viewerEpoch, ownerEpoch)
                        || handle.generation != generation || !handle.eligible.contains(viewer.player.getUniqueId())
                        || handle.hidden.contains(viewer.player.getUniqueId()) || viewer.position == null
                        || lockedOwners.contains(viewer.player.getUniqueId()) || owner != null && owner.anchor == null) return false;
                NametagProfile profile = active(); Anchor anchor = owner == null ? handle.fixed : owner.anchor; UUID viewerId = viewer.player.getUniqueId();
                if (anchor == null || data == null) return false;
                View view=views.get(viewerId);
                if (!handle.visible && (profile.transition().fadeOutTicks() == 0 || view == null || view.alpha <= 0)) return false;
                boolean self = owner != null && owner.entity.getUniqueId().equals(viewerId);
                if (self && !profile.visibility().selfVisible() || owner != null && !self && !anchor.tracked.contains(viewerId)
                        || !anchor.world.equals(viewer.position.world) || profile.visibility().hideInvisible() && anchor.invisible
                        || profile.visibility().hideSneaking() && anchor.sneaking || profile.visibility().hideSpectators() && anchor.spectator
                        || owner != null && System.nanoTime() - anchor.sampled > 1_000_000_000L) return false;
                double x=anchor.x-viewer.position.x, y=anchor.y-viewer.position.y, z=anchor.z-viewer.position.z;
                return x*x+y*y+z*z <= profile.visibility().range()*profile.visibility().range();
            }
        }

        private void status(View view, UUID viewer, NametagStatus.Reason reason, int count) {
            NametagStatus next = new NametagStatus(reason, count);
            if (!next.equals(view.status)) {
                view.status = next;
                emit(reason == NametagStatus.Reason.ELIGIBLE ? NametagEvent.Type.PRESENTED : NametagEvent.Type.SUPPRESSED, viewer, next);
            }
        }

        private void emit(NametagEvent.Type type, UUID viewer, NametagStatus status) {
            try { listener.accept(new NametagEvent(type, id(), Optional.ofNullable(viewer), status)); }
            catch (RuntimeException failure) { providerFailures.increment(); }
        }

        private void updateLease() {
            closeLease();
            if (replace && ((Handle) this).owner instanceof Player player) lease = vanillaLease.apply(player);
        }

        private void closeLease() {
            if (lease == null) return;
            try { lease.close(); } catch (Exception failure) { transportFailure(failure); }
            lease = null;
        }

        private void dispose() {
            closeLease();
            for (Layer layer : List.copyOf(layers)) { layer.ended = true; layer.timer.cancel(); }
            layers.clear(); views.clear(); lockedOwners.clear(); data = null; waitingOwner = null;
            emit(NametagEvent.Type.CLOSED, null, new NametagStatus(NametagStatus.Reason.CLOSED, 0));
        }

        @Override public void attach(Entity entity, double gap) {
            super.mutate(() -> {
                DisplayService.this.attach(this, entity, gap);
                waitingOwner=null; data=null; forceSample=true; views.clear();
                if (locked) lockedOwners.add(entity.getUniqueId());
                updateLease();
            });
        }
        @Override public void location(Location location) {
            synchronized (DisplayService.this) {
                super.location(location); waitingOwner=null; views.clear(); closeLease();
            }
        }

        @Override public DisplayVisibility visibility() { synchronized (DisplayService.this) { return active().visibility(); } }
        @Override public NametagProfile profile() { synchronized (DisplayService.this) { return base; } }
        @Override public void profile(NametagProfile value) {
            super.mutate(() -> { base=Objects.requireNonNull(value, "profile"); ((Handle) this).visibility=active().visibility(); forceSample=true; });
        }
        @Override public void visibility(DisplayVisibility value) { synchronized (DisplayService.this) { profile(base.toBuilder().visibility(value).build()); } }
        @Override public void layout(NametagLayout value) { synchronized (DisplayService.this) { profile(base.toBuilder().layout(value).build()); } }
        @Override public void renderer(NametagRenderer<T> value) { super.mutate(() -> renderer=Objects.requireNonNull(value, "renderer")); }
        @Override public Optional<T> snapshot() { synchronized (DisplayService.this) { return Optional.ofNullable(data); } }
        @Override public void refreshData() { super.mutate(() -> forceSample=true); }
        @Override public NametagLayer layer(NametagProfile profile, int priority, long durationTicks) {
            if (durationTicks < 0) throw new IllegalArgumentException("Duration cannot be negative");
            synchronized (DisplayService.this) {
                Layer layer = new Layer(Objects.requireNonNull(profile, "profile"), priority, ++layerSequence);
                super.mutate(() -> { layers.add(layer); forceSample=true; });
                if (durationTicks > 0) layer.timer = Schedulers.globalLater(runtime.plugin(), layer::close, durationTicks);
                return layer;
            }
        }
        @Override public void lockOwnerHidden() { super.mutate(() -> { locked=true; if (((Handle) this).owner != null) lockedOwners.add(((Handle) this).owner.getUniqueId()); }); }
        @Override public boolean isOwnerHiddenLocked() { synchronized (DisplayService.this) { return locked; } }
        @Override public void persistent(boolean value) { super.mutate(() -> { persistent=value; if (!value && waitingOwner != null) close(); }); }
        @Override public void replaceVanillaName(boolean value) { super.mutate(() -> { replace=value; updateLease(); }); }
        @Override public void listener(Consumer<NametagEvent> value) { super.mutate(() -> listener=Objects.requireNonNull(value, "listener")); }
        @Override public NametagStatus diagnose(UUID viewer) {
            synchronized (DisplayService.this) {
                Objects.requireNonNull(viewer, "viewer");
                if (isClosed()) return new NametagStatus(NametagStatus.Reason.CLOSED, 0);
                View view=views.get(viewer); return view == null ? new NametagStatus(NametagStatus.Reason.OFFLINE, 0) : view.status;
            }
        }

        private final class Layer implements NametagLayer {
            final NametagProfile profile; final int priority; final long sequence;
            TaskHandle timer=TaskHandle.NOOP; boolean ended;
            Layer(NametagProfile profile, int priority, long sequence) { this.profile=profile; this.priority=priority; this.sequence=sequence; }
            @Override public boolean isClosed() { synchronized (DisplayService.this) { return ended; } }
            @Override public void close() {
                synchronized (DisplayService.this) {
                    if (ended) return; ended=true; timer.cancel(); layers.remove(this);
                    if (!Composition.this.isClosed()) ((Handle) Composition.this).revision++;
                    forceSample=true;
                }
            }
        }

        private final class View {
            long tick, nextRender, revision=-1, generation=-1, ownerEpoch, viewerEpoch;
            double alpha;
            NametagLayout layout=NametagLayout.empty();
            NametagStatus status=new NametagStatus(NametagStatus.Reason.OFFLINE, 0);
        }
    }

    private final class TextHandle extends Handle implements ManagedTextDisplay {
        private Component text;
        private Function<Player, Component> provider;
        private TextDisplayStyle textStyle = TextDisplayStyle.defaults();
        private Function<Player, TextDisplayStyle> styleProvider;

        private TextHandle(Component text) {
            this.text = text;
        }

        private Component resolve(Player viewer) {
            return Objects.requireNonNull(provider == null ? text : provider.apply(viewer), "provider.text");
        }

        private TextDisplayStyle resolveStyle(Player viewer) {
            return Objects.requireNonNull(styleProvider == null ? textStyle : styleProvider.apply(viewer), "provider.textStyle");
        }

        @Override
        public void text(Component value) {
            super.mutate(() -> {
                text = Objects.requireNonNull(value, "text");
                provider = null;
            });
        }

        @Override
        public void textFor(Function<Player, Component> value) {
            super.mutate(() -> provider = Objects.requireNonNull(value, "provider"));
        }

        @Override
        public TextDisplayStyle textStyle() {
            synchronized (DisplayService.this) {
                return textStyle;
            }
        }

        @Override
        public void textStyle(TextDisplayStyle value) {
            super.mutate(() -> {
                textStyle = Objects.requireNonNull(value, "style");
                styleProvider = null;
            });
        }

        @Override
        public void textStyleFor(Function<Player, TextDisplayStyle> value) {
            super.mutate(() -> styleProvider = Objects.requireNonNull(value, "provider"));
        }
    }

    private final class ItemHandle extends Handle implements ManagedItemDisplay {
        private ItemStack item;
        private Function<Player, ItemStack> provider;
        private ItemDisplay.ItemDisplayTransform transform = ItemDisplay.ItemDisplayTransform.FIXED;

        private ItemHandle(ItemStack item) {
            this.item = item;
        }

        private ItemStack resolve(Player viewer) {
            return Objects.requireNonNull(provider == null ? item : provider.apply(viewer), "provider.item").clone();
        }

        @Override
        public void item(ItemStack value) {
            ItemStack copy = Objects.requireNonNull(value, "item").clone();
            super.mutate(() -> {
                item = copy;
                provider = null;
            });
        }

        @Override
        public void itemFor(Function<Player, ItemStack> value) {
            super.mutate(() -> provider = Objects.requireNonNull(value, "provider"));
        }

        @Override
        public ItemDisplay.ItemDisplayTransform itemTransform() {
            synchronized (DisplayService.this) {
                return transform;
            }
        }

        @Override
        public void itemTransform(ItemDisplay.ItemDisplayTransform value) {
            super.mutate(() -> transform = Objects.requireNonNull(value, "transform"));
        }
    }
}
