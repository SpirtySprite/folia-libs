package net.folianpc.api.agent;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.ApiStatus;
import java.util.Objects;
import java.util.UUID;

/** An immutable block observation, not a promise that the block still exists when an action executes. */
@ApiStatus.Experimental
public record ObservedBlock(UUID world, int x, int y, int z, Material material) {
    /** Requires a world identifier and observed material. */
    public ObservedBlock { Objects.requireNonNull(world, "world"); Objects.requireNonNull(material, "material"); }
    /** Resolves a fresh block-center location against the same world instance's identifier. */
    public Location center(World resolved) {
        Objects.requireNonNull(resolved, "world");
        if (!world.equals(resolved.getUID())) throw new IllegalArgumentException("Observation belongs to another world");
        return new Location(resolved, x + 0.5, y + 0.5, z + 0.5);
    }
}
