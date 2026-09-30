package net.folianpc.api;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Converts {@link NpcData} to and from plain maps.
 *
 * <p>The format is versioned through the {@code schema} key. Reading is tolerant on purpose: missing
 * keys fall back to defaults, unknown keys are ignored, and unknown enum names fall back to the default
 * value, so data written by an older or newer version still loads. Anything that had to be guessed is
 * reported to the warning callback.
 */
public final class NpcDataCodec {

    /** The schema version this version of the library writes. */
    public static final int SCHEMA = 1;

    private NpcDataCodec() {
    }

    public static @NotNull Map<String, Object> toMap(@NotNull NpcData data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schema", SCHEMA);
        putIfNotNull(map, "id", data.id() == null ? null : data.id().toString());
        putIfNotNull(map, "name", data.name());
        putIfNotNull(map, "type", data.type() == null ? null : data.type().name());
        map.put("world", data.world() == null ? "" : data.world());
        map.put("x", data.x());
        map.put("y", data.y());
        map.put("z", data.z());
        map.put("yaw", (double) data.yaw());
        map.put("pitch", (double) data.pitch());
        map.put("lookAtPlayers", data.lookAtPlayers());
        if (data.skin() != null) {
            Map<String, Object> skin = new LinkedHashMap<>();
            skin.put("value", data.skin().value());
            putIfNotNull(skin, "signature", data.skin().signature());
            map.put("skin", skin);
        }
        map.put("mirrorSkin", data.mirrorSkin());
        if (data.equipment() != null && !data.equipment().isEmpty()) {
            Map<String, Object> equipment = new LinkedHashMap<>();
            for (Map.Entry<EquipmentSlot, ItemStack> entry : data.equipment().entrySet()) {
                if (entry.getValue() != null) {
                    equipment.put(entry.getKey().name(), entry.getValue().serialize());
                }
            }
            map.put("equipment", equipment);
        }
        map.put("nametag", data.nametag() == null ? List.of() : new ArrayList<>(data.nametag()));
        NpcAppearance appearance = data.appearance() == null ? NpcAppearance.defaults() : data.appearance();
        Map<String, Object> look = new LinkedHashMap<>();
        look.put("glowing", appearance.glowing());
        look.put("invisible", appearance.invisible());
        look.put("skinLayers", appearance.skinLayers());
        look.put("scale", appearance.scale());
        if (appearance.glowColor() != null) {
            look.put("glowColor", NamedTextColor.NAMES.key(appearance.glowColor()));
        }
        look.put("collidable", appearance.collidable());
        look.put("nametagVisible", appearance.nametagVisible());
        map.put("appearance", look);
        map.put("pose", (data.pose() == null ? NpcPose.STANDING : data.pose()).name());
        map.put("baby", data.baby());
        map.put("showInTabList", data.showInTabList());
        MobVariant variant = data.mobVariant() == null ? MobVariant.defaults() : data.mobVariant();
        Map<String, Object> mob = new LinkedHashMap<>();
        mob.put("variant", variant.variant());
        putIfNotNull(mob, "variantName", variant.variantName());
        putIfNotNull(mob, "villagerProfession", variant.villagerProfession());
        putIfNotNull(mob, "villagerType", variant.villagerType());
        mob.put("villagerLevel", variant.villagerLevel());
        map.put("mobVariant", mob);
        putIfNotNull(map, "owner", data.owner() == null ? null : data.owner().toString());
        NametagStyle style = data.nametagStyle() == null ? NametagStyle.defaults() : data.nametagStyle();
        Map<String, Object> styleMap = new LinkedHashMap<>();
        styleMap.put("background", style.background());
        styleMap.put("textOpacity", style.textOpacity());
        styleMap.put("shadow", style.shadow());
        styleMap.put("seeThrough", style.seeThrough());
        map.put("nametagStyle", styleMap);
        return map;
    }

    @SuppressWarnings("deprecation")
    public static @NotNull NpcData fromMap(@NotNull Map<String, ?> map, @NotNull Consumer<String> warn) {
        int schema = number(map.get("schema"), 1).intValue();
        if (schema > SCHEMA) {
            warn.accept("NPC data has schema " + schema + " but this version understands " + SCHEMA
                    + "; unknown fields are ignored");
        }
        NpcData.Builder builder = NpcData.builder();
        builder.id(uuid(map.get("id"), "id", warn));
        builder.name(string(map.get("name"), "NPC"));
        builder.type(enumValue(EntityType.class, map.get("type"), EntityType.PLAYER, "type", warn));
        builder.position(string(map.get("world"), ""),
                number(map.get("x"), 0).doubleValue(), number(map.get("y"), 0).doubleValue(),
                number(map.get("z"), 0).doubleValue(), number(map.get("yaw"), 0).floatValue(),
                number(map.get("pitch"), 0).floatValue());
        builder.lookAtPlayers(bool(map.get("lookAtPlayers"), false));
        Map<String, ?> skin = section(map.get("skin"));
        if (skin != null && skin.get("value") != null) {
            builder.skin(Skin.of(string(skin.get("value"), ""), nullableString(skin.get("signature"))));
        }
        builder.mirrorSkin(bool(map.get("mirrorSkin"), false));
        Map<String, ?> equipment = section(map.get("equipment"));
        if (equipment != null) {
            Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
            for (Map.Entry<String, ?> entry : equipment.entrySet()) {
                EquipmentSlot slot = enumValue(EquipmentSlot.class, entry.getKey(), null, "equipment slot", warn);
                Map<String, ?> serialized = section(entry.getValue());
                if (slot == null || serialized == null) {
                    continue;
                }
                try {
                    items.put(slot, ItemStack.deserialize(new LinkedHashMap<String, Object>(serialized)));
                } catch (RuntimeException failure) {
                    warn.accept("Skipped unreadable item in equipment slot " + slot + ": " + failure.getMessage());
                }
            }
            builder.equipment(items);
        }
        if (map.get("nametag") instanceof List<?> lines) {
            List<String> text = new ArrayList<>(lines.size());
            for (Object line : lines) {
                text.add(String.valueOf(line));
            }
            builder.nametag(text);
        }
        Map<String, ?> look = section(map.get("appearance"));
        if (look != null) {
            NpcAppearance defaults = NpcAppearance.defaults();
            NamedTextColor glow = null;
            if (look.get("glowColor") != null) {
                glow = NamedTextColor.NAMES.value(String.valueOf(look.get("glowColor")).toLowerCase(Locale.ROOT));
                if (glow == null) {
                    warn.accept("Unknown glow colour '" + look.get("glowColor") + "'");
                }
            }
            builder.appearance(new NpcAppearance(
                    bool(look.get("glowing"), defaults.glowing()),
                    bool(look.get("invisible"), defaults.invisible()),
                    bool(look.get("skinLayers"), defaults.skinLayers()),
                    number(look.get("scale"), defaults.scale()).doubleValue(),
                    glow,
                    bool(look.get("collidable"), defaults.collidable()),
                    bool(look.get("nametagVisible"), defaults.nametagVisible())));
        }
        builder.pose(enumValue(NpcPose.class, map.get("pose"), NpcPose.STANDING, "pose", warn));
        builder.baby(bool(map.get("baby"), false));
        builder.showInTabList(bool(map.get("showInTabList"), false));
        Map<String, ?> mob = section(map.get("mobVariant"));
        if (mob != null) {
            builder.mobVariant(new MobVariant(
                    number(mob.get("variant"), 0).intValue(),
                    nullableString(mob.get("variantName")),
                    nullableString(mob.get("villagerProfession")),
                    nullableString(mob.get("villagerType")),
                    number(mob.get("villagerLevel"), 1).intValue()));
        }
        builder.owner(uuid(map.get("owner"), "owner", warn));
        Map<String, ?> style = section(map.get("nametagStyle"));
        if (style != null) {
            NametagStyle defaults = NametagStyle.defaults();
            builder.nametagStyle(new NametagStyle(
                    number(style.get("background"), defaults.background()).intValue(),
                    number(style.get("textOpacity"), defaults.textOpacity()).intValue(),
                    bool(style.get("shadow"), defaults.shadow()),
                    bool(style.get("seeThrough"), defaults.seeThrough())));
        }
        return builder.build();
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static @Nullable Map<String, ?> section(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> copy = new LinkedHashMap<>();
            raw.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return copy;
        }
        return null;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static @Nullable String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static Number number(Object value, Number fallback) {
        if (value instanceof Number n) {
            return n;
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static @Nullable UUID uuid(Object value, String what, Consumer<String> warn) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException invalid) {
            warn.accept("Ignored invalid " + what + " '" + value + "'");
            return null;
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Object value, E fallback, String what,
                                                   Consumer<String> warn) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, String.valueOf(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            warn.accept("Unknown " + what + " '" + value + "'"
                    + (fallback == null ? "" : ", using " + fallback));
            return fallback;
        }
    }
}
