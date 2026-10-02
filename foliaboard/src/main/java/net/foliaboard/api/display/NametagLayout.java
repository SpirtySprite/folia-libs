package net.foliaboard.api.display;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable keyed text/item composition. Stable keys preserve element identity across updates. */
@ApiStatus.Experimental
public record NametagLayout(List<Element> elements) {
    /** Copies elements and rejects duplicate keys. */
    public NametagLayout {
        elements = List.copyOf(elements);
        var keys = new HashSet<String>();
        for (Element element : elements) {
            if (!keys.add(element.key())) throw new IllegalArgumentException("Duplicate element key");
        }
    }
    /** Creates an empty layout builder confined to the calling thread. */
    public static Builder builder() {
        return new Builder();
    }
    /** Returns an empty composition without predefined content. */
    public static NametagLayout empty() {
        return new NametagLayout(List.of());
    }
    /** One independently styled display with an additional gap above the owner's height and an optional distance band. */
    public sealed interface Element permits Text, Item {
        /** Stable developer-supplied element key. */
        String key();
        /** Vertical gap above the owner's current height. */
        double gap();
        /** Immutable shared rendering settings. */
        DisplayStyle style();
        /** Minimum visible distance, inclusive. */
        double minDistance();
        /** Maximum visible distance, exclusive. */
        double maxDistance();
    }
    /** A styled text line; components can contain multiple lines. */
    public record Text(String key, Component text, double gap, DisplayStyle style, TextDisplayStyle textStyle,
                       double minDistance, double maxDistance) implements Element {
        /** Validates text, offsets and distance bounds. */
        public Text {
            validate(key, gap, style, minDistance, maxDistance);
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(textStyle, "textStyle");
        }
    }
    /** An item badge; input and returned item stacks are defensively copied. */
    public record Item(String key, ItemStack item, double gap, DisplayStyle style,
                       ItemDisplay.ItemDisplayTransform transform, double minDistance, double maxDistance) implements Element {
        /** Copies the item and validates offsets and distance bounds. */
        public Item {
            validate(key, gap, style, minDistance, maxDistance);
            item = Objects.requireNonNull(item, "item").clone();
            Objects.requireNonNull(transform, "transform");
        }
        /** Returns an independent item copy. */
        @Override
        public ItemStack item() {
            return item.clone();
        }
    }
    private static void validate(String key, double gap, DisplayStyle style, double min, double max) {
        if (Objects.requireNonNull(key, "key").isBlank()) throw new IllegalArgumentException("Element key cannot be blank");
        Objects.requireNonNull(style, "style");
        if (!Double.isFinite(gap) || Math.abs(gap) > 64 || !Double.isFinite(min) || !Double.isFinite(max)
                || min < 0 || max <= min || max > 1024) throw new IllegalArgumentException("Invalid element bounds");
    }
    /** Fluent composition builder. Element order defines presentation order. */
    public static final class Builder {
        private final List<Element> elements = new ArrayList<>();
        private Builder() {
        }
        /** Adds default-styled text with a developer-defined offset. */
        public Builder text(String key, Component text, double gap) {
            return element(new Text(key, text, gap, DisplayStyle.defaults(), TextDisplayStyle.defaults(), 0, 1024));
        }
        /** Adds a default-styled item badge. */
        public Builder item(String key, ItemStack item, double gap) {
            return element(new Item(key, item, gap, DisplayStyle.defaults(), ItemDisplay.ItemDisplayTransform.FIXED, 0, 1024));
        }
        /** Adds a fully configured element, including distance band and styles. */
        public Builder element(Element element) {
            elements.add(Objects.requireNonNull(element, "element"));
            return this;
        }
        /** Returns a validated immutable layout. */
        public NametagLayout build() {
            return new NametagLayout(elements);
        }
    }
}
