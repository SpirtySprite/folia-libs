package net.folianpc.api.agent;

import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.ApiStatus;
import java.util.Objects;

/** Copied world-action details delivered to an explicit permission callback on the target's owning thread. */
@ApiStatus.Experimental
public record AgentInteraction(Kind kind, Location location, Material material) {
    /** World-affecting mining, entity pickup and station-based processing. */
    public enum Kind { MINE, PICKUP, PLACE, CRAFT, SMELT }
    /** Requires a valid world and finite coordinates and copies the location. */
    public AgentInteraction {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(material, "material");
        location = Objects.requireNonNull(location, "location").clone();
        Objects.requireNonNull(location.getWorld(), "world");
        location.checkFinite();
    }
    /** Returns an independent location copy. */
    @Override public Location location() { return location.clone(); }
}
