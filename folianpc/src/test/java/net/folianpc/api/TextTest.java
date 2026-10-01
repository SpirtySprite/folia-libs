package net.folianpc.api;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextTest {
    @Test
    void preservesEmptyFallbacksAndMixedFormatting() {
        assertEquals(Component.empty(), Text.parse(null));
        assertEquals(Component.empty(), Text.parse(""));
        assertEquals("", Text.escape(null));
        assertEquals("Gold blue", Text.plain(Text.parse("&6Gold <blue>blue")));
        assertEquals(Text.mini("<red>hello"), Text.parse("&chello"));
    }

    @Test
    void preservesHexAndTagEscapingRoundTrips() {
        assertEquals(Text.mini("<#ff00aa>hello"), Text.legacy("&#ff00aahello"));
        Component value = Text.parse("<green>hello");
        assertEquals(value, Text.mini(Text.toMini(value)));
        String untrusted = "<red>name <unknown>";
        assertEquals(untrusted, Text.plain(Text.mini(Text.escape(untrusted))));
    }
}
