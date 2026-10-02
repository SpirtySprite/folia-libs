package net.foliaboard.api.display;

import org.jetbrains.annotations.ApiStatus;

/** A temporary profile scope. Closing or expiring it reveals the next active layer or base profile. */
@ApiStatus.Experimental
public interface NametagLayer extends AutoCloseable {
    /** Reports closure or expiry. */
    boolean isClosed();
    /** Removes only this layer; repeated calls are harmless. */
    @Override void close();
}
