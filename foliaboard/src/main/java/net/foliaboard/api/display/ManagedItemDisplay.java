package net.foliaboard.api.display;

import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;

/** A managed item display. Supplied and provider-returned item stacks are copied. */
@ApiStatus.Experimental
public interface ManagedItemDisplay extends ManagedDisplay {
    /** Sets a shared item and clears the viewer-specific provider. Do not mutate the argument concurrently. */
    void item(ItemStack item);

    /** Sets a provider run on each viewer's owning thread. Exceptions suppress that viewer's presentation. */
    void itemFor(Function<Player, ItemStack> provider);

    /** Returns the configured Minecraft item rendering context. */
    ItemDisplay.ItemDisplayTransform itemTransform();

    /** Sets the Minecraft item rendering context. */
    void itemTransform(ItemDisplay.ItemDisplayTransform transform);
}
