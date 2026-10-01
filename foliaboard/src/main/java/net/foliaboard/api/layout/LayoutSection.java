package net.foliaboard.api.layout;

import net.foliaboard.api.BoardBuilder;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.function.Consumer;

/** Reusable section recipe. Safe to share when the supplied callback is thread-safe. */
@ApiStatus.Experimental
public record LayoutSection(String name, Consumer<BoardBuilder> recipe) {
    /** Requires a name and recipe. The recipe runs when configuring a builder, not when rendering it. */
    public LayoutSection {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(recipe, "recipe");
    }

    /** Applies this section to the supplied builder, preserving its existing rows. */
    public void apply(BoardBuilder builder) {
        recipe.accept(Objects.requireNonNull(builder, "builder"));
    }

    /** Composes sections in order without modifying either original recipe. */
    public LayoutSection andThen(LayoutSection next) {
        Objects.requireNonNull(next, "next");
        return new LayoutSection(name + "/" + next.name, builder -> {
            apply(builder);
            next.apply(builder);
        });
    }
}
