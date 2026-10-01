package net.foliaboard.api;

import org.jetbrains.annotations.ApiStatus;

import java.util.Map;
import java.util.Objects;

/** Immutable counters by presentation surface. Operations count adapter calls or changed Adventure properties, not wire packets. */
@ApiStatus.Experimental
public record PresentationStats(Map<Surface, Counters> surfaces) {
    /** Copies counters so later updates cannot change this snapshot. */
    public PresentationStats {
        surfaces = Map.copyOf(surfaces);
    }

    /** Returns a surface's counters, or zero when it has never been used. */
    public Counters surface(Surface surface) {
        return surfaces.getOrDefault(Objects.requireNonNull(surface, "surface"), new Counters(0, 0));
    }

    /** Independently measurable presentation surfaces. */
    public enum Surface {
        /** Sidebar frames and rows. */
        SIDEBAR,
        /** Nametag teams and membership. */
        TEAM,
        /** Below-name and tab-list score objectives. */
        OBJECTIVE,
        /** Player list header, footer, name and order. */
        TAB,
        /** Boss-bar properties, showing and hiding. */
        BOSS_BAR
    }

    /** Requested refreshes or mutations compared with operations actually applied after filtering and diffing. */
    public record Counters(long requests, long changedOperations) {
    }
}
