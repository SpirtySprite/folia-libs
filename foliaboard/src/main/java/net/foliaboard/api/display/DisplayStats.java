package net.foliaboard.api.display;

import org.jetbrains.annotations.ApiStatus;

/** Immutable counts. Failures distinguish provider callbacks from packet transport failures. */
@ApiStatus.Experimental
public record DisplayStats(int handles, int viewers, int clientEntities, long providerFailures, long transportFailures) {
}
