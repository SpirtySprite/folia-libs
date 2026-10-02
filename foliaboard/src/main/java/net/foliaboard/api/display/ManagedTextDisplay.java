package net.foliaboard.api.display;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;

/** A managed text display, including multiline components and viewer-specific text. */
@ApiStatus.Experimental
public interface ManagedTextDisplay extends ManagedDisplay {
    /** Sets shared text and clears the viewer-specific provider. */
    void text(Component text);

    /** Sets a provider run on each viewer's owning thread. Exceptions suppress that viewer's presentation. */
    void textFor(Function<Player, Component> provider);

    /** Replaces text rendering properties. */
    void textStyle(TextDisplayStyle style);
}
