package net.foliaboard.internal.packet.reflect;

import net.foliaboard.api.display.DisplayTransform;
import net.foliaboard.internal.display.DisplayFrame;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class DisplayPackets {
    private record Data(int id, Object serializer) {
    }

    private final ComponentConverter components = new ComponentConverter();
    private final Map<String, Data> fields = new HashMap<>();
    private final AtomicInteger counter;
    private final Constructor<?> spawn;
    private final boolean byteAngles;
    private final Constructor<?> remove;
    private final Constructor<?> value;
    private final Constructor<?> metadata;
    private final Constructor<?> vector;
    private final Constructor<?> position;
    private final Constructor<?> teleport;
    private final Constructor<?> passengers;
    private final Constructor<?> buffer;
    private final Method wrappedBuffer;
    private final Method release;
    private final Method itemCopy;
    private final Method ridingPosition;
    private final Method getPlayerHandle;
    private final Object zero;
    private final Object textType;
    private final Object itemType;
    private final Map<UUID, Object> riders = new java.util.concurrent.ConcurrentHashMap<>();
    private final Constructor<?> rider;
    private final Method level;

    public DisplayPackets() {
        Class<?> entity = Reflect.clazz("net.minecraft.world.entity.Entity");
        Class<?> types = Reflect.clazz("net.minecraft.world.entity.EntityType");
        Class<?> vec = Reflect.clazz("net.minecraft.world.phys.Vec3");
        Class<?> add = packet("ClientboundAddEntityPacket");
        counter = Reflect.get(Reflect.field(entity, "ENTITY_COUNTER"), null);
        vector = Reflect.constructor(vec, double.class, double.class, double.class);
        zero = Reflect.get(Reflect.field(vec, "ZERO"), null);
        textType = Reflect.get(Reflect.field(types, "TEXT_DISPLAY"), null);
        itemType = Reflect.get(Reflect.field(types, "ITEM_DISPLAY"), null);
        Constructor<?> addConstructor;
        boolean bytes;
        try {
            addConstructor = Reflect.constructor(add, int.class, UUID.class, double.class, double.class,
                    double.class, byte.class, byte.class, types, int.class, vec, byte.class);
            bytes = true;
        } catch (IllegalStateException oldSignature) {
            addConstructor = Reflect.constructor(add, int.class, UUID.class, double.class, double.class,
                    double.class, float.class, float.class, types, int.class, vec, double.class);
            bytes = false;
        }
        spawn = addConstructor;
        byteAngles = bytes;
        remove = Reflect.constructor(packet("ClientboundRemoveEntitiesPacket"), int[].class);
        value = Reflect.constructor(Reflect.clazz("net.minecraft.network.syncher.SynchedEntityData$DataValue"),
                int.class, Reflect.clazz("net.minecraft.network.syncher.EntityDataSerializer"), Object.class);
        metadata = Reflect.constructor(packet("ClientboundSetEntityDataPacket"), int.class, List.class);
        Class<?> byteBuf = Reflect.clazz("io.netty.buffer.ByteBuf");
        Class<?> friendly = Reflect.clazz("net.minecraft.network.FriendlyByteBuf");
        buffer = Reflect.constructor(friendly, byteBuf);
        wrappedBuffer = Reflect.method(Reflect.clazz("io.netty.buffer.Unpooled"), "wrappedBuffer", byte[].class);
        release = Reflect.method(Reflect.clazz("io.netty.util.ReferenceCounted"), "release");
        passengers = Reflect.constructor(packet("ClientboundSetPassengersPacket"), friendly);
        Class<?> change;
        try {
            change = Reflect.clazz("net.minecraft.world.entity.PositionMoveRotation");
        } catch (IllegalStateException olderServer) {
            change = null;
        }
        position = change == null ? null : Reflect.constructor(change, vec, vec, float.class, float.class);
        teleport = change == null ? Reflect.constructor(packet("ClientboundTeleportEntityPacket"), friendly)
                : Reflect.constructor(packet("ClientboundTeleportEntityPacket"), int.class, change, Set.class, boolean.class);
        itemCopy = Reflect.method(Reflect.clazz("org.bukkit.craftbukkit.inventory.CraftItemStack"),
                "asNMSCopy", ItemStack.class);
        getPlayerHandle = Reflect.method(Reflect.clazz("org.bukkit.craftbukkit.entity.CraftEntity"), "getHandle");
        rider = Reflect.constructor(Reflect.clazz("net.minecraft.world.entity.Display$TextDisplay"), types, Reflect.clazz("net.minecraft.world.level.Level"));
        level = Reflect.methodByNameDeep(entity, "level", 0);
        ridingPosition = Reflect.method(entity, "getPassengerRidingPosition", entity);
        String[] shared = {"TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS", "TRANSFORMATION_INTERPOLATION_DURATION",
                "POS_ROT_INTERPOLATION_DURATION", "TRANSLATION", "SCALE", "LEFT_ROTATION", "RIGHT_ROTATION",
                "BILLBOARD_RENDER_CONSTRAINTS", "BRIGHTNESS_OVERRIDE", "VIEW_RANGE", "SHADOW_RADIUS", "SHADOW_STRENGTH",
                "WIDTH", "HEIGHT", "GLOW_COLOR_OVERRIDE"};
        for (String name : shared) resolve("net.minecraft.world.entity.Display", name);
        for (String name : List.of("TEXT", "LINE_WIDTH", "BACKGROUND_COLOR", "TEXT_OPACITY", "STYLE_FLAGS")) {
            resolve("net.minecraft.world.entity.Display$TextDisplay", name);
        }
        resolve("net.minecraft.world.entity.Display$ItemDisplay", "ITEM_STACK");
        resolve("net.minecraft.world.entity.Display$ItemDisplay", "ITEM_DISPLAY");
        resolve("net.minecraft.world.entity.Entity", "SHARED_FLAGS");
    }

    private void resolve(String owner, String name) {
        Object accessor = Reflect.get(Reflect.field(Reflect.clazz(owner), "DATA_" + name + "_ID"), null);
        fields.put(name, new Data(Reflect.invoke(Reflect.method(accessor.getClass(), "id"), accessor),
                Reflect.invoke(Reflect.method(accessor.getClass(), "serializer"), accessor)));
    }

    public static Class<?> packet(String name) {
        return Reflect.clazz("net.minecraft.network.protocol.game." + name);
    }

    public int nextId() {
        return counter.incrementAndGet();
    }

    public float mountCorrection(Entity player) {
        Object handle = Reflect.invoke(getPlayerHandle, player);
        Object display = riders.computeIfAbsent(player.getUniqueId(), id -> Reflect.instantiate(rider, textType, Reflect.invoke(level, handle)));
        Object anchor = Reflect.invoke(ridingPosition, handle, display);
        double mountY = Reflect.get(Reflect.field(anchor.getClass(), "y"), anchor);
        return (float) (player.getLocation().getY() + player.getHeight() - mountY);
    }

    public void release(Entity entity) { riders.remove(entity.getUniqueId()); }

    public Object spawn(int id, UUID uuid, DisplayFrame frame) {
        Object type = frame.text() == null ? itemType : textType;
        if (byteAngles) {
            return Reflect.instantiate(spawn, id, uuid, frame.x(), frame.y(), frame.z(),
                    angle(frame.pitch()), angle(frame.yaw()), type, 0, zero, angle(frame.yaw()));
        }
        return Reflect.instantiate(spawn, id, uuid, frame.x(), frame.y(), frame.z(),
                frame.pitch(), frame.yaw(), type, 0, zero, (double) frame.yaw());
    }

    public Object metadata(int id, DisplayFrame frame) {
        List<Object> values = new ArrayList<>();
        var style = frame.style();
        var transform = style.transform();
        DisplayTransform.Vector translation = transform.translation();
        float mount = frame.vehicle() < 0 ? 0 : frame.mountCorrection();
        add(values, "TRANSLATION", new org.joml.Vector3f(translation.x(), translation.y() + mount, translation.z()));
        add(values, "SCALE", vector(transform.scale()));
        add(values, "LEFT_ROTATION", rotation(transform.leftRotation()));
        add(values, "RIGHT_ROTATION", rotation(transform.rightRotation()));
        add(values, "TRANSFORMATION_INTERPOLATION_START_DELTA_TICKS", 0);
        add(values, "TRANSFORMATION_INTERPOLATION_DURATION", style.interpolationTicks());
        add(values, "POS_ROT_INTERPOLATION_DURATION", style.teleportTicks());
        add(values, "BILLBOARD_RENDER_CONSTRAINTS", (byte) style.billboard().ordinal());
        add(values, "BRIGHTNESS_OVERRIDE", style.blockLight() < 0 ? -1 : style.blockLight() << 4 | style.skyLight() << 20);
        add(values, "VIEW_RANGE", (float) (frame.range() / 64));
        add(values, "SHADOW_RADIUS", style.shadowRadius());
        add(values, "SHADOW_STRENGTH", style.shadowStrength());
        add(values, "WIDTH", style.width());
        add(values, "HEIGHT", style.height());
        add(values, "GLOW_COLOR_OVERRIDE", style.glowColor());
        add(values, "SHARED_FLAGS", (byte) (style.glowing() ? 0x40 : 0));
        if (frame.text() != null) {
            var text = frame.textStyle();
            add(values, "TEXT", components.toVanilla(frame.text()));
            add(values, "LINE_WIDTH", text.lineWidth());
            add(values, "BACKGROUND_COLOR", text.background());
            add(values, "TEXT_OPACITY", (byte) text.opacity());
            int alignment = switch (text.alignment()) { case CENTER -> 0; case LEFT -> 8; case RIGHT -> 16; };
            add(values, "STYLE_FLAGS", (byte) ((text.shadow() ? 1 : 0) | (text.seeThrough() ? 2 : 0)
                    | (text.defaultBackground() ? 4 : 0) | alignment));
        } else {
            add(values, "ITEM_STACK", Reflect.invoke(itemCopy, null, frame.item()));
            add(values, "ITEM_DISPLAY", (byte) frame.itemTransform().ordinal());
        }
        return Reflect.instantiate(metadata, id, values);
    }

    private void add(List<Object> values, String name, Object data) {
        Data field = fields.get(name);
        values.add(Reflect.instantiate(value, field.id(), field.serializer(), data));
    }

    private static org.joml.Vector3f vector(DisplayTransform.Vector value) {
        return new org.joml.Vector3f(value.x(), value.y(), value.z());
    }

    private static org.joml.Quaternionf rotation(DisplayTransform.Rotation value) {
        return new org.joml.Quaternionf(value.x(), value.y(), value.z(), value.w());
    }

    public Object remove(int... ids) {
        return Reflect.instantiate(remove, (Object) ids);
    }

    public Object passengers(int vehicle, int[] ids) {
        return decode(passengers, output -> {
            varInt(output, vehicle);
            varInt(output, ids.length);
            for (int id : ids) varInt(output, id);
        });
    }

    public Object move(int id, DisplayFrame frame) {
        if (position != null) {
            Object coordinates = Reflect.instantiate(vector, frame.x(), frame.y(), frame.z());
            return Reflect.instantiate(teleport, id,
                    Reflect.instantiate(position, coordinates, zero, frame.yaw(), frame.pitch()), Set.of(), false);
        }
        return decode(teleport, output -> {
            varInt(output, id);
            output.writeDouble(frame.x());
            output.writeDouble(frame.y());
            output.writeDouble(frame.z());
            output.writeByte(angle(frame.yaw()));
            output.writeByte(angle(frame.pitch()));
            output.writeBoolean(false);
        });
    }

    private Object decode(Constructor<?> constructor, Encoder encoder) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            encoder.write(output);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not encode display packet", failure);
        }
        Object netty = Reflect.invoke(wrappedBuffer, null, bytes.toByteArray());
        try {
            Object friendly = Reflect.instantiate(buffer, netty);
            return Reflect.instantiate(constructor, friendly);
        } finally {
            Reflect.invoke(release, netty);
        }
    }

    private static void varInt(DataOutputStream output, int value) throws IOException {
        while ((value & ~0x7f) != 0) {
            output.writeByte(value & 0x7f | 0x80);
            value >>>= 7;
        }
        output.writeByte(value);
    }

    private static byte angle(float degrees) {
        return (byte) Math.floor(degrees * 256 / 360);
    }

    private interface Encoder {
        void write(DataOutputStream output) throws IOException;
    }
}
