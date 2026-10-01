package net.folianpc.api;

import org.jetbrains.annotations.ApiStatus;
import java.util.List;
import java.util.Objects;

/** A decoded snapshot and immutable migration warnings, including dropped fields and substituted values. */
@ApiStatus.Experimental
public record NpcDataReadResult(NpcData data, List<String> warnings) {
    /** Requires a result and independently copies its warning list. */
    public NpcDataReadResult {
        Objects.requireNonNull(data, "data");
        warnings = List.copyOf(warnings);
    }
}
