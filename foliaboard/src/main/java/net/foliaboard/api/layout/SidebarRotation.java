package net.foliaboard.api.layout;

import org.jetbrains.annotations.ApiStatus;

/** Optional rotating temporary layouts. Page changes run on the player's owning thread. */
@ApiStatus.Experimental
public interface SidebarRotation extends LayoutScope {
    /** Requests the next page, wrapping at the end. Safe from any thread. */
    void next();

    /** Requests the previous page, wrapping at the beginning. Safe from any thread. */
    void previous();

    /** Current selected page index, starting at zero. */
    int page();

    /** Number of page recipes supplied when created. */
    int pageCount();
}
