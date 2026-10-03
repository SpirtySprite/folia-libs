package net.folianpc.api.agent;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Region-owned explicit world actions for packet NPCs. Permission is rechecked at commit, and actions never impersonate a Bukkit player. */
@ApiStatus.Experimental
public final class AgentActions {
    private AgentActions() { }

    /** Navigates to a copied position using the goal's clearance and hazard options. */
    public static AgentAction move(Location target) {
        Location destination = location(target);
        return context -> context.navigate(destination);
    }

    /** Navigates to a developer-selected approach point, then executes an action only after arrival. */
    public static AgentAction approach(Location target, AgentAction action) {
        Location destination = location(target);
        Objects.requireNonNull(action, "action");
        return context -> context.navigate(destination).thenCompose(arrived -> arrived
                ? action.execute(context).toCompletableFuture() : CompletableFuture.completedFuture(false));
    }

    /** Finds a standing approach to an occupied block, then executes the supplied action after actual arrival. */
    public static AgentAction approachBlock(Location target, AgentAction action) {
        Location destination = location(target);
        destination.setX(destination.getBlockX() + 0.5);
        destination.setY(destination.getBlockY() + 0.5);
        destination.setZ(destination.getBlockZ() + 0.5);
        Objects.requireNonNull(action, "action");
        return context -> context.navigateNear(destination, context.options().reach()).thenCompose(arrived -> arrived
                ? action.execute(context).toCompletableFuture() : CompletableFuture.completedFuture(false));
    }

    /** Mines an expected block after a bounded tick delay. Drops go directly into inventory, tools must be owned and take one durability damage; XP is not simulated. */
    public static AgentAction mine(Location target, Material expected, ItemStack tool, long ticks) {
        Location position = location(target);
        position.setX(position.getBlockX());
        position.setY(position.getBlockY());
        position.setZ(position.getBlockZ());
        Objects.requireNonNull(expected, "expected");
        ItemStack held = Objects.requireNonNull(tool, "tool").clone();
        if (ticks < 1 || ticks > 72000 || AgentInventory.isAir(expected)) throw new IllegalArgumentException("Invalid mining request");
        return context -> context.waitTicks(ticks).thenCompose(ready -> ready
                ? context.scheduler().callForLocation(position, () -> context.commit(() -> mine(context, position, expected, held)))
                : CompletableFuture.completedFuture(false));
    }

    /** Captures a dropped item's current position on its owner, navigates within reach, then rechecks the item before collecting it. */
    public static AgentAction approachItem(Item item) {
        Objects.requireNonNull(item, "item");
        return context -> context.scheduler().callForEntity(item, () -> context.active() && item.isValid() && !item.isDead() ? item.getLocation().clone() : null)
                .thenCompose(target -> {
                    if (target == null) return CompletableFuture.completedFuture(false);
                    if (context.inReach(target)) return pickup(item).execute(context).toCompletableFuture();
                    return context.navigateNear(target, context.options().reach()).thenCompose(arrived -> arrived
                            ? pickup(item).execute(context).toCompletableFuture() : CompletableFuture.completedFuture(false));
                });
    }

    /** Collects an entire real dropped item on its current entity owner if it remains valid, permitted, in reach and fits the inventory. */
    public static AgentAction pickup(Item item) {
        Objects.requireNonNull(item, "item");
        return context -> context.scheduler().callForEntity(item, () -> context.commit(() -> {
            if (!item.isValid() || item.isDead() || !context.inReach(item.getLocation())) return false;
            ItemStack stack = item.getItemStack().clone();
            if (!context.permitted(new AgentInteraction(AgentInteraction.Kind.PICKUP, item.getLocation(), stack.getType()))) return false;
            return context.inventory().exchangeExact(List.of(), List.of(stack), () -> {
                if (!context.active() || !item.isValid() || item.isDead() || !context.inReach(item.getLocation())) return false;
                ItemStack current = item.getItemStack();
                if (current.getAmount() != stack.getAmount() || !current.isSimilar(stack)) return false;
                item.remove();
                return true;
            });
        }));
    }

    /** Executes an explicit managed-inventory crafting recipe. Does not invoke vanilla player crafting, recipe discovery or player events. */
    public static AgentAction craft(AgentRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        return context -> delay(context, recipe.durationTicks()).thenApply(ready -> ready && context.commit(
                () -> context.inventory().exchange(recipe.ingredients(), List.of(recipe.output()))));
    }

    /** Crafts an explicit recipe beside an unchanged crafting table, checking authorization before and after the recipe delay. */
    public static AgentAction craftAt(Location table, AgentRecipe recipe) {
        Location position = location(table);
        Objects.requireNonNull(recipe, "recipe");
        return processAt(position, Material.CRAFTING_TABLE, AgentInteraction.Kind.CRAFT, recipe);
    }

    /** Places one plain inventory block into unchanged air. Does not fire player placement events or consume customized item metadata. */
    public static AgentAction place(Location target, Material material) {
        Location position = location(target);
        Objects.requireNonNull(material, "material");
        position.setX(position.getBlockX());
        position.setY(position.getBlockY());
        position.setZ(position.getBlockZ());
        if (AgentInventory.isAir(material)) throw new IllegalArgumentException("Cannot place air");
        return context -> context.scheduler().callForLocation(position, () -> context.commit(() -> {
            if (!loaded(position) || !context.inReach(position.clone().add(0.5, 0.5, 0.5)) || !material.isBlock()) return false;
            Block block = position.getBlock();
            if (!AgentInventory.isAir(block.getType()) || !context.permitted(new AgentInteraction(AgentInteraction.Kind.PLACE, position, material))) return false;
            return context.inventory().exchange(Map.of(material, 1), List.of(), () -> {
                if (!context.active() || !context.inReach(position.clone().add(0.5, 0.5, 0.5)) || !AgentInventory.isAir(block.getType())) return false;
                block.setType(material);
                return true;
            });
        }));
    }

    /** Processes an explicit recipe beside an unchanged furnace, blast furnace or smoker. Fuel belongs in the recipe; processing uses the managed inventory, not native furnace slots. */
    public static AgentAction smelt(Location furnace, Material expected, AgentRecipe recipe) {
        Location position = location(furnace);
        Objects.requireNonNull(recipe, "recipe");
        if (expected != Material.FURNACE && expected != Material.BLAST_FURNACE && expected != Material.SMOKER)
            throw new IllegalArgumentException("Expected a supported smelting station");
        return processAt(position, expected, AgentInteraction.Kind.SMELT, recipe);
    }

    private static AgentAction processAt(Location position, Material expected, AgentInteraction.Kind kind, AgentRecipe recipe) {
        return context -> context.scheduler().callForLocation(position, () -> validStation(context, position, expected, kind))
                .thenCompose(valid -> valid ? delay(context, recipe.durationTicks()) : CompletableFuture.completedFuture(false))
                .thenCompose(ready -> ready ? context.scheduler().callForLocation(position, () -> context.commit(() ->
                        validStation(context, position, expected, kind) && context.inventory().exchange(recipe.ingredients(), List.of(recipe.output()))))
                        : CompletableFuture.completedFuture(false));
    }

    /** Describes one expected resource yield for planning. Actual inventory state is observed after execution; customized yields have no plain-ingredient facts. */
    public static AgentOperator gather(String name, ItemStack expectedYield, double cost, AgentAction action) {
        ItemStack yield = Objects.requireNonNull(expectedYield, "expectedYield").clone();
        if (AgentInventory.isAir(yield.getType()) || yield.getAmount() < 1) throw new IllegalArgumentException("Invalid expected yield");
        Map<String, Long> changes = yield.hasItemMeta() ? Map.of(yield.getType().name(), (long) yield.getAmount())
                : Map.of(yield.getType().name(), (long) yield.getAmount(), AgentInventory.plainKey(yield.getType()), (long) yield.getAmount());
        return new AgentOperator(name, Map.of(), changes, cost, action);
    }

    private static boolean mine(AgentContext context, Location position, Material expected, ItemStack held) {
        if (!loaded(position) || !context.inReach(position.clone().add(0.5, 0.5, 0.5))) return false;
        Block block = position.getBlock();
        if (block.getType() != expected || !context.permitted(new AgentInteraction(AgentInteraction.Kind.MINE, position, expected))
                || expected.getHardness() < 0 || block.getState() instanceof org.bukkit.block.Container) return false;
        List<ItemStack> drops = new ArrayList<>(block.getDrops(held));
        List<ItemStack> consumed = List.of();
        if (!AgentInventory.isAir(held.getType())) {
            ItemStack used = held.clone();
            used.setAmount(1);
            consumed = List.of(used);
            if (held.getType().getMaxDurability() > 0 && held.getItemMeta() instanceof Damageable damage && !damage.isUnbreakable()) {
                int nextDamage = Math.addExact(damage.getDamage(), 1);
                if (nextDamage < held.getType().getMaxDurability()) {
                    damage.setDamage(nextDamage);
                    used = used.clone();
                    used.setItemMeta(damage);
                    drops.add(used);
                }
            } else drops.add(used.clone());
        }
        return context.inventory().exchangeExact(consumed, drops, () -> {
            if (!context.active() || !context.inReach(position.clone().add(0.5, 0.5, 0.5)) || block.getType() != expected) return false;
            block.setType(Material.AIR);
            return true;
        });
    }

    private static boolean validStation(AgentContext context, Location position, Material expected, AgentInteraction.Kind kind) {
        return context.active() && loaded(position) && context.inReach(new Location(position.getWorld(), position.getBlockX() + 0.5, position.getBlockY() + 0.5, position.getBlockZ() + 0.5))
                && position.getBlock().getType() == expected
                && context.permitted(new AgentInteraction(kind, position, expected))
                && context.active() && position.getBlock().getType() == expected
                && context.inReach(new Location(position.getWorld(), position.getBlockX() + 0.5, position.getBlockY() + 0.5, position.getBlockZ() + 0.5));
    }

    private static boolean loaded(Location position) {
        return position.getWorld().isChunkLoaded(position.getBlockX() >> 4, position.getBlockZ() >> 4);
    }

    private static CompletableFuture<Boolean> delay(AgentContext context, long ticks) {
        return ticks == 0 ? CompletableFuture.completedFuture(context.active()) : context.waitTicks(ticks);
    }

    private static Location location(Location target) {
        Location result = Objects.requireNonNull(target, "target").clone();
        Objects.requireNonNull(result.getWorld(), "world");
        result.checkFinite();
        return result;
    }
}
