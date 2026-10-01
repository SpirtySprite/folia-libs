package net.foliaboard.api.text;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TextTest {
    @Test
    void preservesEmptyFallbackMixedFormattingAndCaching() {
        assertEquals(Component.empty(), Text.parse(null));
        assertEquals("Gold blue", Text.plain(Text.parse("&6Gold <blue>blue")));
        assertSame(Text.cached("&aCached"), Text.cached("&aCached"));
        Component value = Text.mini("<red>hello");
        assertEquals(value, Text.mini(Text.toMini(value)));
    }

    @Test
    void escapesUnknownTagsAndBackslashesAsLiteralText() {
        String value = "<red>name <unknown> \\path";
        assertEquals("\\<red>name \\<unknown> \\\\path", Text.escape(value));
        assertEquals(value, Text.plain(Text.mini(Text.escape(value))));
    }
}
