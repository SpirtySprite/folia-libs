package net.folianpc.api.agent;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** A thread-safe, bounded NPC inventory. Inputs and snapshots are copied; exchanges either commit entirely or change nothing. */
@ApiStatus.Experimental
public final class AgentInventory {
    private final int capacity;
    private List<ItemStack> contents = List.of();
    private boolean committing;

    /** Creates an inventory with one to 256 stack slots. */
    public AgentInventory(int slots) {
        if (slots < 1 || slots > 256) throw new IllegalArgumentException("Invalid inventory capacity");
        capacity = slots;
    }

    /** Returns independent nonempty stacks in slot order. */
    public synchronized List<ItemStack> contents() { return copy(contents); }

    /** Returns total material-name counts and plain:MATERIAL counts for stacks without customized metadata. */
    public synchronized Map<String, Long> resources() {
        Map<String, Long> result = new java.util.HashMap<>();
        for (ItemStack item : contents) {
            result.merge(item.getType().name(), (long) item.getAmount(), Math::addExact);
            if (!item.hasItemMeta()) result.merge(plainKey(item.getType()), (long) item.getAmount(), Math::addExact);
        }
        return Map.copyOf(result);
    }

    /** Returns the reserved planning key for uncustomized ingredients of this material. */
    public static String plainKey(Material material) { return "plain:" + Objects.requireNonNull(material, "material").name(); }

    /** Adds every supplied stack if all fit. Rejects air, empty stacks and null inputs. */
    public synchronized boolean add(Collection<ItemStack> items) { return exchange(Map.of(), items); }

    /** Consumes plain material ingredients and adds copied outputs atomically. Named or otherwise customized stacks are not consumed. */
    public synchronized boolean exchange(Map<Material, Integer> ingredients, Collection<ItemStack> outputs) {
        return exchange(ingredients, outputs, () -> true);
    }

    synchronized boolean exchange(Map<Material, Integer> ingredients, Collection<ItemStack> outputs, BooleanSupplier effect) {
        if (committing) throw new IllegalStateException("Recursive inventory mutation during world commit");
        Objects.requireNonNull(ingredients, "ingredients");
        List<ItemStack> additions = copy(Objects.requireNonNull(outputs, "outputs"));
        Map<Material, Integer> needed = new EnumMap<>(Material.class);
        ingredients.forEach((material, amount) -> {
            Objects.requireNonNull(material, "material");
            if (AgentInventory.isAir(material) || amount == null || amount < 1) throw new IllegalArgumentException("Invalid ingredient");
            needed.put(material, amount);
        });
        List<ItemStack> next = new ArrayList<>(copy(contents));
        for (ItemStack item : next) {
            Material material = item.getType();
            int amount = needed.getOrDefault(material, 0);
            if (amount == 0 || item.hasItemMeta()) continue;
            int used = Math.min(item.getAmount(), amount);
            item.setAmount(item.getAmount() - used);
            needed.put(material, amount - used);
        }
        if (needed.values().stream().anyMatch(amount -> amount > 0)) return false;
        next.removeIf(item -> item.getAmount() == 0);
        return install(next, additions, effect);
    }

    /** Exchanges exact stacks, matching all metadata. Inputs are copied and partial consumption never commits. */
    public synchronized boolean exchangeExact(Collection<ItemStack> ingredients, Collection<ItemStack> outputs) {
        return exchangeExact(ingredients, outputs, () -> true);
    }

    synchronized boolean exchangeExact(Collection<ItemStack> ingredients, Collection<ItemStack> outputs, BooleanSupplier effect) {
        if (committing) throw new IllegalStateException("Recursive inventory mutation during world commit");
        List<ItemStack> next = new ArrayList<>(copy(contents));
        List<ItemStack> additions = copy(outputs);
        for (ItemStack needed : copy(ingredients)) {
            if (AgentInventory.isAir(needed.getType()) || needed.getAmount() < 1) throw new IllegalArgumentException("Invalid ingredient");
            int remaining = needed.getAmount();
            for (ItemStack item : next) {
                if (!item.isSimilar(needed)) continue;
                int used = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - used);
                remaining -= used;
            }
            if (remaining > 0) return false;
        }
        next.removeIf(item -> item.getAmount() == 0);
        return install(next, additions, effect);
    }

    private boolean install(List<ItemStack> next, List<ItemStack> additions, BooleanSupplier effect) {
        for (ItemStack addition : additions) {
            if (AgentInventory.isAir(addition.getType()) || addition.getAmount() < 1 || addition.getMaxStackSize() < 1) throw new IllegalArgumentException("Invalid output stack");
            int remaining = addition.getAmount();
            for (ItemStack item : next) {
                if (!item.isSimilar(addition)) continue;
                int moved = Math.min(remaining, Math.max(0, item.getMaxStackSize() - item.getAmount()));
                item.setAmount(item.getAmount() + moved);
                remaining -= moved;
            }
            while (remaining > 0) {
                if (next.size() >= capacity) return false;
                ItemStack stack = addition.clone();
                int moved = Math.min(remaining, stack.getMaxStackSize());
                if (moved < 1) throw new IllegalArgumentException("Invalid maximum stack size");
                stack.setAmount(moved);
                next.add(stack);
                remaining -= moved;
            }
        }
        committing = true;
        try {
            if (!effect.getAsBoolean()) return false;
            contents = List.copyOf(next);
            return true;
        } finally { committing = false; }
    }

    /** Removes plain ingredients only when the entire request is available. */
    public synchronized boolean remove(Map<Material, Integer> ingredients) { return exchange(ingredients, List.of()); }

    private static List<ItemStack> copy(Collection<ItemStack> items) {
        return items.stream().map(item -> Objects.requireNonNull(item, "item").clone()).toList();
    }

    static boolean isAir(Material material) { return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR; }
}
