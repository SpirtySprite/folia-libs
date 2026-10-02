package net.foliaboard.internal.display;

import net.foliaboard.api.display.DisplayStats;
import net.foliaboard.api.display.DisplayStyle;
import net.foliaboard.api.display.DisplayVisibility;
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
    private final LongAdder providerFailures = new LongAdder();
    private final LongAdder transportFailures = new LongAdder();
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

    private void ensureOpen() {
        runtime.ensureOpen();
        if (closed) throw new IllegalStateException("Display service is closed");
        if (!supported()) throw new UnsupportedOperationException("Display packets are unavailable; inspect diagnose()");
    }

    private void register(Handle handle) {
        runtime.metrics().requested(net.foliaboard.api.PresentationStats.Surface.DISPLAY);
        handles.put(handle.id, handle);
        if (!started) {
            started = true;
            Schedulers.global(runtime.plugin(), () -> {
                for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) onJoin(player);
            });
        }
    }

    public synchronized void onJoin(Player player) {
        if (closed || runtime.closed() || !supported()) return;
        UUID id = player.getUniqueId();
        Session current = sessions.get(id);
        if (current != null && current.player == player) return;
        if (current != null) retire(current);
        Session session = new Session(player);
        sessions.put(id, session);
        session.timer = Schedulers.entityTaskTimer(runtime.plugin(), player, timer -> {
            try {
                tick(session);
            } catch (RuntimeException failure) {
                transportFailure(failure);
            }
        }, 1, 1);
    }

    public synchronized void onTransition(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.player != player) return;
        session.epoch++;
        session.anchor = null;
        session.settleTicks = 2;
        for (Handle handle : handles.values()) {
            if (handle.owner == player) handle.generation++;
        }
        if (session.connection != null) session.connection.present(List.of());
    }

    public synchronized void onQuit(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.player != player) return;
        retire(session);
    }

    private void retire(Session session) {
        session.ended = true;
        session.epoch++;
        session.anchor = null;
        sessions.remove(session.player.getUniqueId(), session);
        session.timer.cancel();
        if (session.connection != null) session.connection.close();
        for (Handle handle : List.copyOf(handles.values())) {
            handle.eligible.remove(session.player.getUniqueId());
            if (handle.owner == session.player) handle.close();
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
        boolean alive = viewer.isValid() && !viewer.isDead();
        if (alive) {
            Set<UUID> tracked = new HashSet<>();
            for (Player watcher : viewer.getTrackedBy()) tracked.add(watcher.getUniqueId());
            List<Integer> actualPassengers = viewer.getPassengers().stream().map(org.bukkit.entity.Entity::getEntityId).toList();
            session.anchor = new Anchor(world, position.getX(), position.getY(), position.getZ(), 0, 0,
                    viewer.getEntityId(), transport.mountCorrection(viewer), Set.copyOf(tracked), actualPassengers,
                    viewer.isInvisible(), viewer.isSneaking(), viewer.getGameMode() == GameMode.SPECTATOR, System.nanoTime());
        } else {
            session.anchor = null;
        }
        if (session.connection == null && !handles.isEmpty()) session.connection = transport.connect(viewer);
        if (session.connection == null) return;
        List<DisplayFrame> frames = new ArrayList<>();
        for (Handle handle : List.copyOf(handles.values())) {
            handle.eligible.remove(viewer.getUniqueId());
            Anchor anchor = handle.fixed;
            Session ownerSession = handle.owner == null ? null : sessions.get(handle.owner.getUniqueId());
            if (handle.owner != null) {
                anchor = ownerSession == null || ownerSession.player != handle.owner ? null : ownerSession.anchor;
            }
            if (anchor == null || !allowed(handle, session, anchor, position)) continue;
            long generation = handle.generation;
            long viewerEpoch = session.epoch;
            long ownerEpoch = ownerSession == null ? 0 : ownerSession.epoch;
            try {
                if (!handle.predicate.test(viewer)) continue;
                Component text = handle instanceof TextHandle textHandle ? textHandle.resolve(viewer) : null;
                ItemStack item = handle instanceof ItemHandle itemHandle ? itemHandle.resolve(viewer) : null;
                if (!current(handle, session, ownerSession, generation, viewerEpoch, ownerEpoch)) continue;
                Anchor captured = anchor;
                Session capturedOwner = ownerSession;
                frames.add(new DisplayFrame(handle.id, generation, anchor.world, anchor.x,
                        anchor.y, anchor.z, anchor.yaw, anchor.pitch, anchor.vehicle,
                        anchor.correction + (float) handle.gap, text, item,
                        handle instanceof ItemHandle itemHandle ? itemHandle.transform : ItemDisplay.ItemDisplayTransform.NONE,
                        handle.style, handle instanceof TextHandle textHandle ? textHandle.textStyle : TextDisplayStyle.defaults(),
                        handle.visibility.range(), () -> accepted(handle, session, capturedOwner, captured,
                                generation, viewerEpoch, ownerEpoch), anchor.passengers));
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
                    || !viewer.player.canSee(handle.owner)) return false;
            if (System.nanoTime() - anchor.sampled > 1_000_000_000L
                    || policy.hideInvisible() && anchor.invisible || policy.hideSneaking() && anchor.sneaking
                    || policy.hideSpectators() && anchor.spectator) return false;
        }
        double dx = anchor.x - position.getX();
        double dy = anchor.y - position.getY();
        double dz = anchor.z - position.getZ();
        return dx * dx + dy * dy + dz * dz <= policy.range() * policy.range();
    }

    private synchronized boolean accepted(Handle handle, Session viewer, Session owner, Anchor anchor,
                                          long generation, long viewerEpoch, long ownerEpoch) {
        return current(handle, viewer, owner, generation, viewerEpoch, ownerEpoch)
                && !handle.hidden.contains(viewer.player.getUniqueId()) && handle.visible
                && (handle.owner == null || handle.visibility.selfVisible()
                || !handle.owner.getUniqueId().equals(viewer.player.getUniqueId()))
                && (handle.owner == null || System.nanoTime() - anchor.sampled <= 1_000_000_000L);
    }

    private boolean current(Handle handle, Session viewer, Session owner, long generation, long viewerEpoch, long ownerEpoch) {
        return !closed && !runtime.closed() && !handle.closed && handles.get(handle.id) == handle
                && handle.generation == generation && !viewer.ended && viewer.epoch == viewerEpoch
                && sessions.get(viewer.player.getUniqueId()) == viewer
                && (owner == null || !owner.ended && owner.epoch == ownerEpoch
                && sessions.get(owner.player.getUniqueId()) == owner);
    }

    private void attach(Handle handle, Player player, double gap) {
        Objects.requireNonNull(player, "player");
        if (!Double.isFinite(gap) || Math.abs(gap) > 64) throw new IllegalArgumentException("Gap must be finite and within 64 blocks");
        onJoin(player);
        handle.owner = player;
        handle.gap = gap;
        handle.fixed = null;
        handle.generation++;
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
    }

    private final class Session {
        private final Player player;
        private TaskHandle timer = TaskHandle.NOOP;
        private DisplayTransport.Connection connection;
        private Anchor anchor;
        private long epoch;
        private int settleTicks;
        private boolean ended;

        private Session(Player player) {
            this.player = player;
        }
    }

    private abstract class Handle implements net.foliaboard.api.display.ManagedDisplay {
        private final UUID id = UUID.randomUUID();
        private final Set<UUID> hidden = new HashSet<>();
        private final Set<UUID> eligible = new HashSet<>();
        private DisplayStyle style = DisplayStyle.defaults();
        private DisplayVisibility visibility = DisplayVisibility.defaults();
        private Predicate<Player> predicate = player -> true;
        private Player owner;
        private double gap;
        private Anchor fixed;
        private long generation;
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
                runtime.metrics().requested(net.foliaboard.api.PresentationStats.Surface.DISPLAY);
            }
        }

        @Override
        public void style(DisplayStyle value) {
            mutate(() -> style = Objects.requireNonNull(value, "style"));
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
                fixed = copy;
                owner = null;
                generation++;
            });
        }

        @Override
        public void attach(Player player, double gap) {
            mutate(() -> DisplayService.this.attach(this, player, gap));
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
                generation++;
                handles.remove(id, this);
                hidden.clear();
                eligible.clear();
                owner = null;
                fixed = null;
            }
        }
    }

    private final class TextHandle extends Handle implements ManagedTextDisplay {
        private Component text;
        private Function<Player, Component> provider;
        private TextDisplayStyle textStyle = TextDisplayStyle.defaults();

        private TextHandle(Component text) {
            this.text = text;
        }

        private Component resolve(Player viewer) {
            return Objects.requireNonNull(provider == null ? text : provider.apply(viewer), "provider.text");
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
        public void textStyle(TextDisplayStyle value) {
            super.mutate(() -> textStyle = Objects.requireNonNull(value, "style"));
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
        public void itemTransform(ItemDisplay.ItemDisplayTransform value) {
            super.mutate(() -> transform = Objects.requireNonNull(value, "transform"));
        }
    }
}
