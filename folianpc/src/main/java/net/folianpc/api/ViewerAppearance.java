package net.folianpc.api;

import net.folianpc.internal.ItemCopies;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable per-viewer appearance and equipment overrides. Unspecified slots inherit the NPC's equipment. */
@ApiStatus.Experimental
public record ViewerAppearance(Optional<NpcAppearance> appearance, Map<EquipmentSlot, ItemStack> equipment) {
    /** Copies equipment independently and requires an explicit optional appearance. */
    public ViewerAppearance {
        Objects.requireNonNull(appearance, "appearance");
        equipment = ItemCopies.copy(Objects.requireNonNull(equipment, "equipment"));
    }

    /** Returns independently cloned overrides. An air item clears an inherited slot. */
    @Override public Map<EquipmentSlot, ItemStack> equipment() { return ItemCopies.copy(equipment); }

    /** Starts an independent override builder. */
    public static Builder builder() { return new Builder(); }

    /** Builds optional presentation overrides without changing shared NPC state. */
    public static final class Builder {
        private NpcAppearance appearance;
        private final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        private Builder() { }
        /** Overrides the complete appearance; null restores inherited appearance. */
        public Builder appearance(NpcAppearance value) { appearance = value; return this; }
        /** Overrides one slot with a copied item; null removes that override. */
        public Builder equipment(EquipmentSlot slot, ItemStack item) {
            Objects.requireNonNull(slot, "slot");
            if (item == null) equipment.remove(slot); else equipment.put(slot, item.clone());
            return this;
        }
        /** Freezes independently copied overrides. */
        public ViewerAppearance build() { return new ViewerAppearance(Optional.ofNullable(appearance), equipment); }
    }
}
