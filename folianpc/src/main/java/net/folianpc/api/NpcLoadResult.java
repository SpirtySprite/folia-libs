package net.folianpc.api;

import org.jetbrains.annotations.ApiStatus;
import java.util.List;
import java.util.Objects;

/** A spawned NPC and immutable migration warnings from its saved data. */
@ApiStatus.Experimental
public record NpcLoadResult(Npc npc, List<String> warnings) {
    /** Requires a result and independently copies its warning list. */
    public NpcLoadResult {
        Objects.requireNonNull(npc, "npc");
        warnings = List.copyOf(warnings);
    }
}
