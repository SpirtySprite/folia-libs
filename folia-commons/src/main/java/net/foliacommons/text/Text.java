package net.foliacommons.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Stateless text primitives shared by the libraries. Safe from any thread. Required inputs reject
 * null; callers choose any null fallback and item-specific italic styling. MiniMessage parsing
 * accepts trusted markup, while escaping inserts literal placeholder values.
 */
public final class Text {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer HEX = LegacyComponentSerializer.builder().character('&').hexColors().build();

    private Text() {
    }

    /** Shared immutable MiniMessage parser, available on all supported Paper versions. */
    public static @NotNull MiniMessage miniMessage() {
        return MINI;
    }

    /** Parses trusted MiniMessage markup without changing absent decorations. */
    public static @NotNull Component mini(@NotNull String text) {
        return MINI.deserialize(Objects.requireNonNull(text, "text"));
    }

    /** Parses markup with caller-provided tag resolvers; resolvers must be safe on the calling thread. */
    public static @NotNull Component mini(@NotNull String text, @NotNull TagResolver... resolvers) {
        return MINI.deserialize(Objects.requireNonNull(text, "text"), Objects.requireNonNull(resolvers, "resolvers"));
    }

    /** Parses mixed legacy codes and trusted MiniMessage markup. Legacy colors reset prior decorations. */
    public static @NotNull Component parse(@NotNull String text) {
        return mini(Legacy.toMini(Objects.requireNonNull(text, "text")));
    }

    /** Parses ampersand legacy codes using the standard serializer. */
    public static @NotNull Component legacyAmpersand(@NotNull String text) {
        return AMPERSAND.deserialize(Objects.requireNonNull(text, "text"));
    }

    /** Parses section-sign legacy codes using the standard serializer. */
    public static @NotNull Component legacySection(@NotNull String text) {
        return SECTION.deserialize(Objects.requireNonNull(text, "text"));
    }

    /** Parses ampersand legacy codes with hexadecimal color support. */
    public static @NotNull Component legacyHex(@NotNull String text) {
        return HEX.deserialize(Objects.requireNonNull(text, "text"));
    }

    /** Serializes a component to section-sign legacy text. */
    public static @NotNull String toLegacy(@NotNull Component component) {
        return SECTION.serialize(Objects.requireNonNull(component, "component"));
    }

    /** Serializes a component to MiniMessage markup. */
    public static @NotNull String toMini(@NotNull Component component) {
        return MINI.serialize(Objects.requireNonNull(component, "component"));
    }

    /** Extracts plain text without styling. */
    public static @NotNull String plain(@NotNull Component component) {
        return PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(component, "component"));
    }

    /** Escapes tags recognized by the default MiniMessage parser, preserving existing wrapper behavior. */
    public static @NotNull String escapeTags(@NotNull String value) {
        return MINI.escapeTags(Objects.requireNonNull(value, "value"));
    }

    /** Escapes every tag opener and backslash, including unknown tags used by custom resolvers. */
    public static @NotNull String escape(@NotNull String value) {
        Objects.requireNonNull(value, "value");
        if (value.indexOf('<') < 0 && value.indexOf('\\') < 0) {
            return value;
        }
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '<' || character == '\\') {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
