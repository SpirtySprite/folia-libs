package net.foliaboard.api.display;

import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Immutable text rendering properties. Background is packed ARGB; opacity is unsigned 0 to 255. */
@ApiStatus.Experimental
public record TextDisplayStyle(int lineWidth, int background, int opacity, boolean shadow,
                               boolean seeThrough, boolean defaultBackground, TextDisplay.TextAlignment alignment) {
    /** Validates text rendering limits. */
    public TextDisplayStyle {
        Objects.requireNonNull(alignment, "alignment");
        if (lineWidth < 1 || opacity < 0 || opacity > 255) {
            throw new IllegalArgumentException("Line width must be positive and opacity between 0 and 255");
        }
    }

    /** Returns shadowed, centered text with a transparent background. */
    public static TextDisplayStyle defaults() {
        return new TextDisplayStyle(200, 0, 255, true, false, false, TextDisplay.TextAlignment.CENTER);
    }
}
