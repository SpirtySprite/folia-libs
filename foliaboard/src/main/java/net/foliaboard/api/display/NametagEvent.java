package net.foliaboard.api.display;

import org.jetbrains.annotations.ApiStatus;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

/** Immutable lifecycle notification. Viewer events run on the viewer thread; sampling events on the owner thread. */
@ApiStatus.Experimental
public record NametagEvent(Type type, UUID nametag, Optional<UUID> viewer, NametagStatus status) {
    /** Requires immutable event values. */
    public NametagEvent {
        Objects.requireNonNull(type, "type"); Objects.requireNonNull(nametag, "nametag");
        Objects.requireNonNull(viewer, "viewer"); Objects.requireNonNull(status, "status");
    }
    /** Notifications describe evaluation, not packet delivery acknowledgement. Configuration closure runs on its caller. */
    public enum Type { PRESENTED, SUPPRESSED, RECREATED, SAMPLED, FAILURE, CLOSED }
}
