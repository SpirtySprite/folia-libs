package net.foliaboard.api.display;

import org.jetbrains.annotations.ApiStatus;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** A coordinated entity nametag composition. All methods are safe from any thread. */
@ApiStatus.Experimental
public interface ManagedNametag<T> extends ManagedDisplay {
    /** Returns the immutable base profile, independent of temporary layers. */
    NametagProfile profile();
    /** Atomically replaces the base profile and its rendering policies. */
    void profile(NametagProfile profile);
    /** Replaces the base layout; preserves other profile settings and the owner sampler. */
    void layout(NametagLayout layout);
    /** Replaces the viewer renderer. Exceptions isolate that viewer. */
    void renderer(NametagRenderer<T> renderer);
    /** Returns the last successful owner snapshot. The sampler must return immutable data. */
    Optional<T> snapshot();
    /** Requests fresh owner data and viewer layouts on their next owning ticks. */
    void refreshData();
    /** Adds a priority layer. Newer layers win equal priority. Zero duration lasts until explicitly closed. */
    NametagLayer layer(NametagProfile profile, int priority, long durationTicks);
    /** Permanently prohibits owner visibility for this handle, including future profile or attachment changes. */
    void lockOwnerHidden();
    /** Reports whether the permanent owner-hide lock is enabled. */
    boolean isOwnerHiddenLocked();
    /** Keeps a player definition across reconnects; mob attachments remain bound to their entity lifetime. */
    void persistent(boolean persistent);
    /** Explicitly suppresses vanilla player names through an owned, reversible team visibility lease. */
    void replaceVanillaName(boolean replace);
    /** Replaces the notification callback. Exceptions are isolated and counted. */
    void listener(Consumer<NametagEvent> listener);
    /** Returns the viewer's last evaluation, or an offline status when never evaluated. */
    NametagStatus diagnose(UUID viewer);
}
