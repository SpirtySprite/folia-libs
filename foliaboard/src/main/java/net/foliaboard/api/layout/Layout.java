package net.foliaboard.api.layout;

import net.foliaboard.FoliaBoard;
import net.foliaboard.api.BoardBuilder;
import net.foliaboard.api.Sidebar;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.ApiStatus;

public final class Layout {
    private final String name;
    private final Consumer<BoardBuilder> recipe;

    private Layout(String name, Consumer<BoardBuilder> recipe) {
        this.name = name;
        this.recipe = recipe;
    }

    public static @NotNull Layout named(@NotNull String name, @NotNull Consumer<BoardBuilder> recipe) {
        return new Layout(Objects.requireNonNull(name, "name"), Objects.requireNonNull(recipe, "recipe"));
    }

    /** Composes reusable sections in order. Recipes must be thread-safe when shared. */
    @ApiStatus.Experimental
    public static @NotNull Layout sections(@NotNull String name, @NotNull List<LayoutSection> sections) {
        List<LayoutSection> copy = List.copyOf(sections);
        return named(name, builder -> copy.forEach(builder::section));
    }

    /** Appends a section without modifying this layout. */
    @ApiStatus.Experimental
    public @NotNull Layout withSection(@NotNull LayoutSection section) {
        Objects.requireNonNull(section, "section");
        return named(name, builder -> {
            recipe.accept(builder);
            section.apply(builder);
        });
    }

    public @NotNull String name() {
        return name;
    }

    public @NotNull Sidebar applyTo(@NotNull FoliaBoard board, @NotNull Player player) {
        BoardBuilder builder = board.createBoard(player);
        recipe.accept(builder);
        return builder.build();
    }
}
