package net.foliacommons.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextTest {
    @Test
    void mixedTextResetsLegacyDecorationsAndPreservesUnicode() {
        Component result = Text.parse("&lBold &aGreen <red>rouge 🐈</red>");
        assertEquals("Bold Green rouge 🐈", Text.plain(result));
        assertEquals(TextDecoration.State.NOT_SET, Text.mini("<green>Text").decoration(TextDecoration.ITALIC));
        assertEquals(Component.text("literal <red>"), Text.mini(Text.escape("literal <red>")));
    }

    @Test
    void escapingCoversUnknownTagsAndExistingBackslashes() {
        String raw = "<custom>\\value <red>";
        assertEquals(raw, Text.plain(Text.mini(Text.escape(raw))));
        assertEquals("\\<custom>\\\\value \\<red>", Text.escape(raw));
        assertEquals("<custom>\\<red>", Text.escapeTags("<custom><red>"));
        String plain = "plain";
        assertSame(plain, Text.escape(plain));
    }

    @Test
    void serializersAndCustomResolversKeepTheirConventions() {
        assertEquals(Component.text("Green", NamedTextColor.GREEN), Text.legacyAmpersand("&aGreen"));
        assertEquals(Component.text("Red", NamedTextColor.RED), Text.legacySection("§cRed"));
        assertEquals("Color", Text.plain(Text.legacyHex("&#ff00aaColor")));
        Component component = Text.mini("<name>", Placeholder.unparsed("name", "<red>"));
        assertEquals("<red>", Text.plain(component));
        assertEquals("§aGreen", Text.toLegacy(Text.mini("<green>Green")));
        assertEquals(Text.mini("<green>Green"), Text.mini(Text.toMini(Text.mini("<green>Green"))));
        assertSame(Text.miniMessage(), Text.miniMessage());
    }

    @Test
    void requiredInputsRejectNull() {
        assertThrows(NullPointerException.class, () -> Text.parse(null));
        assertThrows(NullPointerException.class, () -> Text.escape(null));
        assertThrows(NullPointerException.class, () -> Text.plain(null));
    }
}
