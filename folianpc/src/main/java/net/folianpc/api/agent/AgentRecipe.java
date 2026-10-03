package net.folianpc.api.agent;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** An explicit production recipe. Developers choose ingredients, fuel, outputs and durations; no recipe or game rules are installed automatically. */
@ApiStatus.Experimental
public record AgentRecipe(String name, Map<Material, Integer> ingredients, ItemStack output, long durationTicks) {
    /** Copies inputs and output and validates positive ingredient/output amounts and a bounded nonnegative duration. Include fuel in smelting ingredients. */
    public AgentRecipe {
        Objects.requireNonNull(name, "name");
        ingredients = Map.copyOf(ingredients);
        output = Objects.requireNonNull(output, "output").clone();
        if (name.isBlank() || ingredients.isEmpty() || durationTicks < 0 || durationTicks > 72000
                || AgentInventory.isAir(output.getType()) || output.getAmount() < 1
                || ingredients.entrySet().stream().anyMatch(e -> AgentInventory.isAir(e.getKey()) || e.getValue() < 1)) {
            throw new IllegalArgumentException("Invalid production recipe");
        }
    }

    /** Returns an independent result stack. */
    @Override public ItemStack output() { return output.clone(); }

    /** Produces the planning requirements and resource changes for one execution, including plain-ingredient distinctions. */
    public AgentOperator operator(double cost, AgentAction action) {
        Map<String, Long> requires = new HashMap<>();
        Map<String, Long> changes = new HashMap<>();
        ingredients.forEach((type, amount) -> {
            requires.put(AgentInventory.plainKey(type), (long) amount);
            changes.merge(type.name(), -(long) amount, Math::addExact);
            changes.merge(AgentInventory.plainKey(type), -(long) amount, Math::addExact);
        });
        changes.merge(output.getType().name(), (long) output.getAmount(), Math::addExact);
        if (!output.hasItemMeta()) changes.merge(AgentInventory.plainKey(output.getType()), (long) output.getAmount(), Math::addExact);
        changes.values().removeIf(n -> n == 0);
        return new AgentOperator(name, requires, changes, cost, action);
    }
}
