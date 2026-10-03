package net.folianpc.api.agent;

import org.bukkit.Material;
import org.jetbrains.annotations.ApiStatus;
import java.util.Map;
import java.util.Objects;

/** Immutable minimum fact counts to achieve. Inventory resources use Material.name(); custom facts must use other keys. */
@ApiStatus.Experimental
public record AgentGoal(Map<String, Long> requires) {
    /** Requires at least one nonblank fact with a positive target count. */
    public AgentGoal {
        requires = Map.copyOf(requires);
        if (requires.isEmpty() || requires.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getValue() < 1)) {
            throw new IllegalArgumentException("Invalid goal");
        }
    }

    /** Creates a goal for a non-air inventory material. */
    public static AgentGoal obtain(Material material, long count) {
        Objects.requireNonNull(material, "material");
        if (AgentInventory.isAir(material)) throw new IllegalArgumentException("Air is not a resource");
        return new AgentGoal(Map.of(material.name(), count));
    }

    /** Tests a previously captured fact snapshot without accessing game state. */
    public boolean satisfied(Map<String, Long> state) {
        return requires.entrySet().stream().allMatch(e -> state.getOrDefault(e.getKey(), 0L) >= e.getValue());
    }
}
