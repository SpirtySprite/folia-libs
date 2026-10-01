package net.foliacommons.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTest {

    @Test
    void malformedAndTrailingCodesStayLiteral() {
        for (String raw : new String[]{"&", "§", "&#1234", "&#zzzzzz", "&x", "&z", "§x"}) {
            assertEquals(raw, Legacy.toMini(raw), raw);
            assertEquals(raw, Legacy.strip(raw), raw);
        }
        assertEquals("", Legacy.toMini(null));
        assertEquals("", Legacy.strip(null));
        assertFalse(Legacy.hasCodes(null));
        assertFalse(Legacy.hasCodes("plain 🐈"));
    }

    @Test
    void conversionPreservesMarkupEscapesAndResetsStyles() {
        assertEquals("<bold>Bold <reset><green>Été 🐈 <reset>plain \\<red>",
                Legacy.toMini("&lBold &aÉté 🐈 &rplain \\<red>"));
        assertEquals("<reset><#Ab12Cd>X", Legacy.toMini("&x&A&b&1&2&C&dX"));
        assertEquals("Été 🐈 <red>markup</red>", Legacy.strip("&AÉté 🐈 <red>markup</red>"));
    }

    @Test
    void convertsAmpersandAndSectionCodes() {
        assertEquals("<reset><green>Hi <bold>there", Legacy.toMini("&aHi &lthere"));
        assertEquals("<reset><red>X", Legacy.toMini("§cX"));
    }

    @Test
    void convertsBothHexForms() {
        assertEquals("<reset><#ff00aa>A", Legacy.toMini("&#ff00aaA"));
        assertEquals("<reset><#123456>B", Legacy.toMini("§x§1§2§3§4§5§6B"));
    }

    @Test
    void leavesPlainAmpersandsAlone() {
        assertEquals("Tom & Jerry &z", Legacy.toMini("Tom & Jerry &z"));
        assertFalse(Legacy.hasCodes("Tom & Jerry"));
        assertTrue(Legacy.hasCodes("&6Gold"));
    }

    @Test
    void stripRemovesEveryCode() {
        assertEquals("Hi there", Legacy.strip("&aHi §lthere&#ffffff"));
    }
}
