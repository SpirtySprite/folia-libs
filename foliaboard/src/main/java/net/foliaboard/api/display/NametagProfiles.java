package net.foliaboard.api.display;

import org.jetbrains.annotations.ApiStatus;
import java.util.Optional;
import java.util.Set;

/** Thread-safe registry of developer-defined immutable profiles. Assignments capture the profile value. */
@ApiStatus.Experimental
public interface NametagProfiles {
    /** Defines or replaces a named profile. */
    void register(String name, NametagProfile profile);
    /** Returns the current named profile, if present. */
    Optional<NametagProfile> find(String name);
    /** Removes a registry entry without changing existing assignments. */
    boolean remove(String name);
    /** Returns an immutable snapshot of registered names. */
    Set<String> names();
}
