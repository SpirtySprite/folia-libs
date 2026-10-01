package net.foliaboard.api;

import net.foliaboard.api.format.NumberFormat;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable desired sidebar frame, safe to build and share from any thread. */
@ApiStatus.Experimental
public record SidebarState(Component title, List<Line> lines, boolean visible) {
    /** Copies the rows and requires a title and non-null rows. The client displays the first 15 rows. */
    public SidebarState {
        Objects.requireNonNull(title, "title");
        lines = List.copyOf(lines);
    }

    /** Immutable row with optional score formatting. */
    public record Line(Component text, Optional<NumberFormat> format) {
        /** Requires text and an optional format; an empty optional uses the default blank format. */
        public Line {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(format, "format");
        }

        /** Creates a row using the default blank number format. */
        public Line(Component text) {
            this(text, Optional.empty());
        }
    }
}
