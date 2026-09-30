package net.foliaboard.api.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Converts legacy colour codes to MiniMessage.
 *
 * @deprecated this class moved to folia-commons. Use {@link net.foliacommons.text.Legacy}.
 */
@Deprecated(since = "1.1.0")
public final class Legacy {

    private Legacy() {
    }

    public static boolean hasCodes(@Nullable String input) {
        return net.foliacommons.text.Legacy.hasCodes(input);
    }

    public static @NotNull String toMini(@Nullable String input) {
        return net.foliacommons.text.Legacy.toMini(input);
    }

    public static @NotNull String strip(@Nullable String input) {
        return net.foliacommons.text.Legacy.strip(input);
    }
}
