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
    /** Returns a builder initialized with default settings. Builders are confined to the calling thread. */
    public static Builder builder() {
        return defaults().toBuilder();
    }

    /** Returns an independent builder initialized with these settings. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Fluent configuration builder. Build immutable settings before sharing them between threads. */
    @ApiStatus.Experimental
    public static final class Builder {
        private int lineWidth;
        private int background;
        private int opacity;
        private boolean shadow;
        private boolean seeThrough;
        private boolean defaultBackground;
        private TextDisplay.TextAlignment alignment;

        private Builder(TextDisplayStyle initial) {
            lineWidth = initial.lineWidth();
            background = initial.background();
            opacity = initial.opacity();
            shadow = initial.shadow();
            seeThrough = initial.seeThrough();
            defaultBackground = initial.defaultBackground();
            alignment = initial.alignment();
        }

        /** Sets the positive line width in pixels. */
        public Builder lineWidth(int value) {
            lineWidth = value;
            return this;
        }

        /** Sets the packed ARGB background color. */
        public Builder background(int value) {
            background = value;
            return this;
        }

        /** Sets unsigned text opacity from 0 to 255. */
        public Builder opacity(int value) {
            opacity = value;
            return this;
        }

        /** Enables text shadow. */
        public Builder shadow(boolean value) {
            shadow = value;
            return this;
        }

        /** Enables rendering through blocks. */
        public Builder seeThrough(boolean value) {
            seeThrough = value;
            return this;
        }

        /** Uses the client default text background. */
        public Builder defaultBackground(boolean value) {
            defaultBackground = value;
            return this;
        }

        /** Sets multiline text alignment. */
        public Builder alignment(TextDisplay.TextAlignment value) {
            alignment = value;
            return this;
        }

        /** Validates settings and returns an immutable snapshot. */
        public TextDisplayStyle build() {
            return new TextDisplayStyle(lineWidth, background, opacity, shadow, seeThrough, defaultBackground, alignment);
        }
    }

}
