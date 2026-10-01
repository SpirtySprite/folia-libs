package net.folianpc.internal;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

public final class ItemCopies {
    private ItemCopies() {
    }

    public static Map<EquipmentSlot, ItemStack> copy(Map<EquipmentSlot, ItemStack> items) {
        Map<EquipmentSlot, ItemStack> result = new EnumMap<>(EquipmentSlot.class);
        items.forEach((slot, item) -> result.put(slot, item.clone()));
        return Map.copyOf(result);
    }
}
