package net.foliaboard.internal.display;

import net.foliaboard.internal.metrics.PacketMetrics;
import net.foliaboard.internal.packet.reflect.DisplayPackets;
import net.foliaboard.internal.packet.reflect.Reflect;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class NmsDisplayTransport implements DisplayTransport {
    private record Rendered(int entity, DisplayFrame frame) {
    }

    private final DisplayPackets packets;
    private final PacketMetrics metrics;
    private final Consumer<Throwable> failures;
    private final String handlerName = "foliaboard_displays_" + UUID.randomUUID();
    private final Method playerHandle;
    private final Class<?> listener;
    private final Class<?> network;
    private final Class<?> channelClass;
    private final Class<?> handlerClass;
    private final Class<?> packetClass;
    private final Class<?> passengersClass;
    private final Class<?> addClass;
    private final Class<?> removeClass;
    private final Class<?> respawnClass;
    private final Class<?> bundleClass;
    private final Constructor<?> bundle;
    private final Method bundlePackets;
    private final Method passengerVehicle;
    private final Method passengerIds;
    private final Method removedIds;
    private final Method addedId;

    public NmsDisplayTransport(PacketMetrics metrics, Consumer<Throwable> failures) {
        this.metrics = metrics;
        this.failures = failures;
        packets = new DisplayPackets();
        playerHandle = Reflect.method(Reflect.clazz("org.bukkit.craftbukkit.entity.CraftPlayer"), "getHandle");
        listener = Reflect.clazz("net.minecraft.server.network.ServerGamePacketListenerImpl");
        network = Reflect.clazz("net.minecraft.network.Connection");
        channelClass = Reflect.clazz("io.netty.channel.Channel");
        handlerClass = Reflect.clazz("io.netty.channel.ChannelOutboundHandler");
        packetClass = Reflect.clazz("net.minecraft.network.protocol.Packet");
        passengersClass = DisplayPackets.packet("ClientboundSetPassengersPacket");
        addClass = DisplayPackets.packet("ClientboundAddEntityPacket");
        removeClass = DisplayPackets.packet("ClientboundRemoveEntitiesPacket");
        respawnClass = DisplayPackets.packet("ClientboundRespawnPacket");
        bundleClass = DisplayPackets.packet("ClientboundBundlePacket");
        bundle = Reflect.constructor(bundleClass, Iterable.class);
        bundlePackets = Reflect.methodByNameDeep(bundleClass, "subPackets", 0);
        passengerVehicle = Reflect.method(passengersClass, "getVehicle");
        passengerIds = Reflect.method(passengersClass, "getPassengers");
        removedIds = Reflect.method(removeClass, "getEntityIds");
        addedId = Reflect.method(addClass, "getId");
    }

    @Override
    public boolean supported() {
        return true;
    }

    @Override
    public float mountCorrection(Player player) {
        return packets.mountCorrection(player);
    }

    @Override
    public Connection connect(Player viewer) {
        Object handle = Reflect.invoke(playerHandle, viewer);
        Object connection = Reflect.get(Reflect.fieldByTypeDeep(handle.getClass(), listener), handle);
        Object net = Reflect.get(Reflect.fieldByTypeDeep(connection.getClass(), network), connection);
        Object channel = Reflect.get(Reflect.fieldByTypeDeep(net.getClass(), channelClass), net);
        return new Client(connection, channel);
    }

    private final class Client implements Connection {
        private final Object connection;
        private final Object channel;
        private final Object loop;
        private final Object pipeline;
        private final Method send;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean drainScheduled = new AtomicBoolean();
        private final AtomicReference<List<DisplayFrame>> pending = new AtomicReference<>();
        private final Map<UUID, Rendered> rendered = new HashMap<>();
        private final Map<Integer, int[]> nativePassengers = new HashMap<>();
        private final Set<Integer> missingVehicles = new HashSet<>();
        private final Set<Object> ownMounts = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        private volatile int count;
        private boolean installed;

        private Client(Object connection, Object channel) {
            this.connection = connection;
            this.channel = channel;
            this.loop = call(channel, "eventLoop");
            this.pipeline = call(channel, "pipeline");
            this.send = Reflect.methodByNameDeep(connection.getClass(), "send", 1);
            execute(this::install);
        }

        private void install() {
            if (closed.get()) return;
            Object handler = Proxy.newProxyInstance(handlerClass.getClassLoader(), new Class<?>[]{handlerClass},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("hashCode")) return System.identityHashCode(proxy);
                        if (name.equals("equals")) return proxy == args[0];
                        if (name.equals("toString")) return handlerName;
                        if (name.equals("handlerAdded") || name.equals("handlerRemoved")) return null;
                        Object context = args[0];
                        Object[] forwarded = Arrays.copyOfRange(args, 1, args.length);
                        if (name.equals("write")) {
                            try {
                                forwarded[0] = rewrite(forwarded[0]);
                            } catch (RuntimeException failure) {
                                failures.accept(failure);
                                close();
                            }
                        }
                        String destination = name.equals("exceptionCaught") ? "fireExceptionCaught" : name;
                        call(context, destination, forwarded);
                        return null;
                    });
            call(pipeline, "addBefore", "packet_handler", handlerName, handler);
            installed = true;
        }

        @Override
        public void present(List<DisplayFrame> frames) {
            if (closed.get()) return;
            pending.set(List.copyOf(frames));
            drain();
        }

        private void drain() {
            if (!drainScheduled.compareAndSet(false, true)) return;
            execute(() -> {
                try {
                    List<DisplayFrame> snapshot = pending.getAndSet(null);
                    if (!closed.get() && installed && snapshot != null) apply(snapshot);
                } finally {
                    drainScheduled.set(false);
                    if (!closed.get() && pending.get() != null) drain();
                }
            });
        }

        private void apply(List<DisplayFrame> snapshot) {
            Set<Integer> relevantVehicles = new HashSet<>();
            for (DisplayFrame frame : snapshot) {
                if (frame.vehicle() >= 0) relevantVehicles.add(frame.vehicle());
            }
            missingVehicles.retainAll(relevantVehicles);
            Set<UUID> accepted = new HashSet<>();
            for (DisplayFrame frame : snapshot) {
                if (!frame.accepted().getAsBoolean() || missingVehicles.contains(frame.vehicle())) continue;
                accepted.add(frame.id());
                Rendered previous = rendered.get(frame.id());
                if (previous != null && (previous.frame().generation() != frame.generation()
                        || !previous.frame().world().equals(frame.world()) || previous.frame().vehicle() != frame.vehicle())) {
                    erase(frame.id());
                    previous = null;
                }
                if (previous == null) {
                    if (frame.vehicle() >= 0) nativePassengers.putIfAbsent(frame.vehicle(),
                            frame.nativePassengers().stream().mapToInt(Integer::intValue).toArray());
                    int id = packets.nextId();
                    rendered.put(frame.id(), new Rendered(id, frame));
                    write(packets.spawn(id, UUID.randomUUID(), frame));
                    write(packets.metadata(id, frame));
                    if (frame.vehicle() >= 0) mount(frame.vehicle());
                } else {
                    if (!sameMetadata(previous.frame(), frame)) write(packets.metadata(previous.entity(), frame));
                    if (frame.vehicle() < 0 && !samePosition(previous.frame(), frame)) write(packets.move(previous.entity(), frame));
                    rendered.put(frame.id(), new Rendered(previous.entity(), frame));
                }
            }
            for (UUID id : List.copyOf(rendered.keySet())) {
                if (!accepted.contains(id)) erase(id);
            }
            count = rendered.size();
        }

        private Object rewrite(Object packet) {
            if (ownMounts.remove(packet)) return packet;
            if (bundleClass.isInstance(packet)) {
                Iterable<?> contents = Reflect.invoke(bundlePackets, packet);
                List<Object> changed = new ArrayList<>();
                for (Object entry : contents) {
                    Object replacement = rewrite(entry);
                    if (bundleClass.isInstance(replacement)) {
                        Iterable<?> nested = Reflect.invoke(bundlePackets, replacement);
                        nested.forEach(changed::add);
                    } else {
                        changed.add(replacement);
                    }
                }
                return Reflect.instantiate(bundle, changed);
            }
            if (passengersClass.isInstance(packet)) {
                int vehicle = Reflect.invoke(passengerVehicle, packet);
                int[] actual = Reflect.invoke(passengerIds, packet);
                if (hasVehicle(vehicle)) {
                    Set<Integer> owned = new HashSet<>();
                    rendered.values().forEach(value -> owned.add(value.entity()));
                    int[] external = Arrays.stream(actual).filter(id -> !owned.contains(id)).toArray();
                    nativePassengers.put(vehicle, external);
                    return packets.passengers(vehicle, PassengerLists.merge(external, mounted(vehicle)));
                }
            } else if (removeClass.isInstance(packet)) {
                Object raw = Reflect.invoke(removedIds, packet);
                Set<Integer> removed = new HashSet<>();
                if (raw instanceof int[] ids) {
                    for (int id : ids) removed.add(id);
                } else if (raw instanceof Iterable<?> ids) {
                    for (Object id : ids) removed.add(((Number) id).intValue());
                }
                List<Integer> virtual = new ArrayList<>();
                rendered.entrySet().removeIf(entry -> {
                    if (!removed.contains(entry.getValue().frame().vehicle())) return false;
                    int vehicle = entry.getValue().frame().vehicle();
                    missingVehicles.add(vehicle);
                    nativePassengers.remove(vehicle);
                    virtual.add(entry.getValue().entity());
                    return true;
                });
                count = rendered.size();
                if (!virtual.isEmpty()) {
                    return Reflect.instantiate(bundle, List.of(packet, packets.remove(virtual.stream().mapToInt(Integer::intValue).toArray())));
                }
            } else if (addClass.isInstance(packet)) {
                int vehicle = Reflect.invoke(addedId, packet);
                missingVehicles.remove(vehicle);
                if (hasVehicle(vehicle)) {
                    return Reflect.instantiate(bundle, List.of(packet, packets.passengers(vehicle,
                            PassengerLists.merge(nativePassengers.getOrDefault(vehicle, new int[0]), mounted(vehicle)))));
                }
            } else if (respawnClass.isInstance(packet)) {
                rendered.clear();
                nativePassengers.clear();
                missingVehicles.clear();
                count = 0;
            }
            return packet;
        }

        private boolean hasVehicle(int vehicle) {
            return rendered.values().stream().anyMatch(value -> value.frame().vehicle() == vehicle);
        }

        private int[] mounted(int vehicle) {
            return rendered.values().stream().filter(value -> value.frame().vehicle() == vehicle
                    && value.frame().accepted().getAsBoolean()).mapToInt(Rendered::entity).sorted().toArray();
        }

        private void mount(int vehicle) {
            Object packet = packets.passengers(vehicle,
                    PassengerLists.merge(nativePassengers.getOrDefault(vehicle, new int[0]), mounted(vehicle)));
            ownMounts.add(packet);
            write(packet);
        }

        private void erase(UUID id) {
            Rendered removed = rendered.remove(id);
            if (removed == null) return;
            int vehicle = removed.frame().vehicle();
            if (vehicle >= 0) {
                mount(vehicle);
                if (!hasVehicle(vehicle)) nativePassengers.remove(vehicle);
            }
            write(packets.remove(removed.entity()));
        }

        private void write(Object packet) {
            if (!packetClass.isInstance(packet)) throw new IllegalArgumentException("Not a server packet");
            Reflect.invoke(send, connection, packet);
            metrics.sent();
            metrics.changed(net.foliaboard.api.PresentationStats.Surface.DISPLAY);
        }

        private void execute(Runnable task) {
            try {
                call(loop, "execute", (Runnable) () -> {
                    try {
                        task.run();
                    } catch (RuntimeException failure) {
                        failures.accept(failure);
                        close();
                    }
                });
            } catch (RuntimeException failure) {
                failures.accept(failure);
                closed.set(true);
            }
        }

        @Override
        public int entities() {
            return count;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            pending.set(null);
            execute(() -> {
                try {
                    for (UUID id : List.copyOf(rendered.keySet())) {
                        try {
                            erase(id);
                        } catch (RuntimeException failure) {
                            failures.accept(failure);
                        }
                    }
                } finally {
                    rendered.clear();
                    count = 0;
                    if (installed) {
                        call(pipeline, "remove", handlerName);
                        installed = false;
                    }
                    nativePassengers.clear();
                    missingVehicles.clear();
                    ownMounts.clear();
                }
            });
        }
    }

    private static Object call(Object instance, String name, Object... args) {
        for (Method method : instance.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] types = method.getParameterTypes();
            boolean matches = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null && !types[i].isInstance(args[i])) matches = false;
            }
            if (matches) {
                method.setAccessible(true);
                return Reflect.invoke(method, instance, args);
            }
        }
        throw new IllegalStateException("Missing network method " + name);
    }

    private static boolean samePosition(DisplayFrame first, DisplayFrame second) {
        return first.x() == second.x() && first.y() == second.y() && first.z() == second.z()
                && first.yaw() == second.yaw() && first.pitch() == second.pitch();
    }

    private static boolean sameMetadata(DisplayFrame first, DisplayFrame second) {
        return first.mountCorrection() == second.mountCorrection() && first.style().equals(second.style())
                && first.textStyle().equals(second.textStyle()) && java.util.Objects.equals(first.text(), second.text())
                && java.util.Objects.equals(first.item(), second.item()) && first.itemTransform() == second.itemTransform()
                && first.range() == second.range();
    }
}
