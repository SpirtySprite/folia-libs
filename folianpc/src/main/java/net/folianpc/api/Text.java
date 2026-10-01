package net.folianpc.api;

import net.kyori.adventure.text.Component;

public final class Text {

    private Text() {
    }

    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return net.foliacommons.text.Text.parse(text);
    }

    public static String escape(String value) {
        return value == null ? "" : net.foliacommons.text.Text.escapeTags(value);
    }

    public static String plain(Component component) {
        return net.foliacommons.text.Text.plain(component);
    }

    public static Component mini(String miniMessage) {
        return net.foliacommons.text.Text.mini(miniMessage);
    }

    public static Component legacy(String legacyText) {
        return net.foliacommons.text.Text.legacyHex(legacyText);
    }

    public static String toMini(Component component) {
        return net.foliacommons.text.Text.toMini(component);
    }
}
