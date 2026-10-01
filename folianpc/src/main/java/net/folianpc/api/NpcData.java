package net.folianpc.api;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A snapshot of one NPC that can be saved and used to spawn it again.
 *
 * <p>Build instances with {@link #builder()} (or {@link #toBuilder()} to change a few fields of an
 * existing one). The long constructors are kept for source compatibility but are deprecated: every new
 * option adds another parameter, so code written against the builder keeps compiling when options are
 * added. To store a snapshot, turn it into plain maps with {@link #serialize()} and read it back with
 * {@link #deserialize(Map)}.
 */
public record NpcData(UUID id, String name, EntityType type, String world,
                      double x, double y, double z, float yaw, float pitch,
                      boolean lookAtPlayers, Skin skin, boolean mirrorSkin,
                      Map<EquipmentSlot, ItemStack> equipment,
                      List<String> nametag,
                      NpcAppearance appearance, NpcPose pose, boolean baby, boolean showInTabList,
                      MobVariant mobVariant, UUID owner, NametagStyle nametagStyle) {

    @Deprecated(since = "1.2.0")
    public NpcData {
        new net.folianpc.internal.Position(world == null ? "" : world, x, y, z, yaw, pitch);
        equipment = equipment == null ? null : net.folianpc.internal.ItemCopies.copy(equipment);
        nametag = nametag == null ? null : List.copyOf(nametag);
    }

    /** Returns independent item stacks, so editing equipment cannot mutate this saved snapshot. */
    @Override
    public Map<EquipmentSlot, ItemStack> equipment() {
        return equipment == null ? null : net.folianpc.internal.ItemCopies.copy(equipment);
    }

    @Deprecated(since = "1.2.0")
    public NpcData(UUID id, String name, EntityType type, String world,
                   double x, double y, double z, float yaw, float pitch,
                   boolean lookAtPlayers, Skin skin, boolean mirrorSkin,
                   Map<EquipmentSlot, ItemStack> equipment,
                   List<String> nametag,
                   NpcAppearance appearance, NpcPose pose, boolean baby, boolean showInTabList,
                   MobVariant mobVariant, UUID owner) {
        this(id, name, type, world, x, y, z, yaw, pitch, lookAtPlayers, skin, mirrorSkin, equipment, nametag,
                appearance, pose, baby, showInTabList, mobVariant, owner, NametagStyle.defaults());
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull Builder toBuilder() {
        Builder builder = new Builder()
                .id(id).name(name).type(type)
                .position(world, x, y, z, yaw, pitch)
                .lookAtPlayers(lookAtPlayers).skin(skin).mirrorSkin(mirrorSkin)
                .appearance(appearance).pose(pose).baby(baby).showInTabList(showInTabList)
                .mobVariant(mobVariant).owner(owner).nametagStyle(nametagStyle);
        if (equipment != null) {
            builder.equipment(equipment);
        }
        if (nametag != null) {
            builder.nametag(nametag);
        }
        return builder;
    }

    /** Converts this snapshot to plain maps, lists and primitives, suitable for YAML, JSON or a database. */
    public @NotNull Map<String, Object> serialize() {
        return NpcDataCodec.toMap(this);
    }

    /** Reads a snapshot written by {@link #serialize()}, including ones written by older versions. */
    public static @NotNull NpcData deserialize(@NotNull Map<String, ?> map) {
        return NpcDataCodec.fromMap(map, message -> {
        });
    }

    public static final class Builder {
        private UUID id;
        private String name = "NPC";
        private EntityType type = EntityType.PLAYER;
        private String world = "";
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
        private boolean lookAtPlayers;
        private Skin skin;
        private boolean mirrorSkin;
        private final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        private List<String> nametag = List.of();
        private NpcAppearance appearance = NpcAppearance.defaults();
        private NpcPose pose = NpcPose.STANDING;
        private boolean baby;
        private boolean showInTabList;
        private MobVariant mobVariant = MobVariant.defaults();
        private UUID owner;
        private NametagStyle nametagStyle = NametagStyle.defaults();

        private Builder() {
        }

        public @NotNull Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public @NotNull Builder name(String name) {
            this.name = name;
            return this;
        }

        public @NotNull Builder type(EntityType type) {
            this.type = type == null ? EntityType.PLAYER : type;
            return this;
        }

        public @NotNull Builder position(String world, double x, double y, double z, float yaw, float pitch) {
            this.world = world == null ? "" : world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            return this;
        }

        public @NotNull Builder lookAtPlayers(boolean lookAtPlayers) {
            this.lookAtPlayers = lookAtPlayers;
            return this;
        }

        public @NotNull Builder skin(Skin skin) {
            this.skin = skin;
            return this;
        }

        public @NotNull Builder mirrorSkin(boolean mirrorSkin) {
            this.mirrorSkin = mirrorSkin;
            return this;
        }

        public @NotNull Builder equipment(@NotNull EquipmentSlot slot, ItemStack item) {
            if (item == null) {
                equipment.remove(slot);
            } else {
                equipment.put(slot, item.clone());
            }
            return this;
        }

        public @NotNull Builder equipment(@NotNull Map<EquipmentSlot, ItemStack> items) {
            equipment.clear();
            items.forEach(this::equipment);
            return this;
        }

        public @NotNull Builder nametag(List<String> lines) {
            this.nametag = lines == null ? List.of() : List.copyOf(lines);
            return this;
        }

        public @NotNull Builder nametag(String... lines) {
            return nametag(List.of(lines));
        }

        public @NotNull Builder appearance(NpcAppearance appearance) {
            this.appearance = appearance == null ? NpcAppearance.defaults() : appearance;
            return this;
        }

        public @NotNull Builder pose(NpcPose pose) {
            this.pose = pose == null ? NpcPose.STANDING : pose;
            return this;
        }

        public @NotNull Builder baby(boolean baby) {
            this.baby = baby;
            return this;
        }

        public @NotNull Builder showInTabList(boolean showInTabList) {
            this.showInTabList = showInTabList;
            return this;
        }

        public @NotNull Builder mobVariant(MobVariant mobVariant) {
            this.mobVariant = mobVariant == null ? MobVariant.defaults() : mobVariant;
            return this;
        }

        public @NotNull Builder owner(UUID owner) {
            this.owner = owner;
            return this;
        }

        public @NotNull Builder nametagStyle(NametagStyle nametagStyle) {
            this.nametagStyle = nametagStyle == null ? NametagStyle.defaults() : nametagStyle;
            return this;
        }

        @SuppressWarnings("deprecation")
        public @NotNull NpcData build() {
            return new NpcData(id, name, type, world, x, y, z, yaw, pitch, lookAtPlayers, skin, mirrorSkin,
                    Map.copyOf(equipment), nametag, appearance, pose, baby, showInTabList, mobVariant, owner,
                    nametagStyle);
        }
    }
}
