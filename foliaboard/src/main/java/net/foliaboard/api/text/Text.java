package net.foliaboard.api.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public final class Text {
    private static final int CACHE_LIMIT = 2048;
    private static final Map<String, Component> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
            return size() > CACHE_LIMIT;
        }
    });

    private Text() {
    }

    public static @NotNull MiniMessage miniMessage() {
        return net.foliacommons.text.Text.miniMessage();
    }

    public static @NotNull Component mini(@NotNull String miniMessage) {
        return net.foliacommons.text.Text.mini(miniMessage);
    }

    public static @NotNull Component mini(@NotNull String miniMessage, @NotNull TagResolver... resolvers) {
        return net.foliacommons.text.Text.mini(miniMessage, resolvers);
    }

    public static @NotNull Component parse(@NotNull String anyFormat) {
        return net.foliacommons.text.Text.parse(anyFormat == null ? "" : anyFormat);
    }

    public static @NotNull Component cached(@NotNull String anyFormat) {
        Component hit = CACHE.get(anyFormat);
        if (hit != null) {
            return hit;
        }
        Component parsed = parse(anyFormat);
        CACHE.put(anyFormat, parsed);
        return parsed;
    }

    public static @NotNull String toMini(@NotNull Component component) {
        return net.foliacommons.text.Text.toMini(component);
    }

    public static @NotNull String plain(@NotNull Component component) {
        return net.foliacommons.text.Text.plain(component);
    }

    public static @NotNull String escape(@NotNull String value) {
        return net.foliacommons.text.Text.escape(value);
    }

    public static @NotNull Component empty() {
        return Component.empty();
    }
}
