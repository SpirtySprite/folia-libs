package net.foliaboard.internal.display;

import net.foliaboard.api.display.DisplayStyle;
import net.foliaboard.api.display.TextDisplayStyle;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.List;
import java.util.function.BooleanSupplier;

public record DisplayFrame(UUID id, long generation, UUID world, double x, double y, double z,
                           float yaw, float pitch, int vehicle, float mountCorrection,
                           Component text, ItemStack item, ItemDisplay.ItemDisplayTransform itemTransform,
                           DisplayStyle style, TextDisplayStyle textStyle, double range,
                           BooleanSupplier accepted, List<Integer> nativePassengers) {
}
