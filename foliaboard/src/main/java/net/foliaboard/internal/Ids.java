package net.foliaboard.internal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class Ids {
    public static final int MAX_LENGTH = 16;

    private Ids() {
    }

    public static @NotNull String namespace(@NotNull String pluginName) {
        int h = pluginName.toLowerCase(Locale.ROOT).hashCode();
        int folded = (h ^ (h >>> 16)) & 0xFFFF;
        return String.format(Locale.ROOT, "%04x", folded);
    }

    public static String instanceNamespace() {
        return java.util.UUID.randomUUID().toString();
    }

    public static @NotNull String sidebarObjective(@NotNull String namespace, int counter) {
        return checked("fb" + token(namespace, "sidebar", counter, 12));
    }

    public static @NotNull String belowNameObjective(@NotNull String namespace) {
        return checked("fb" + token(namespace, "below", 0, 11) + "_bn");
    }

    public static @NotNull String tabListObjective(@NotNull String namespace) {
        return checked("fb" + token(namespace, "tab", 0, 10) + "_tab");
    }

    public static @NotNull String team(@NotNull String namespace, @Nullable Integer sortWeight, int counter) {
        String unique = token(namespace, "team", counter, 12);
        String name = sortWeight != null
                ? String.format(Locale.ROOT, "%04d", Math.max(0, Math.min(9999, sortWeight))) + unique
                : "fbn" + unique;
        return checked(name);
    }

    private static String token(String namespace, String surface, int counter, int length) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((namespace + ":" + surface + ":" + counter).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String encoded = Long.toUnsignedString(java.nio.ByteBuffer.wrap(bytes).getLong(), 36);
            String padded = "0".repeat(13 - encoded.length()) + encoded;
            return padded.substring(padded.length() - length);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String checked(String name) {
        if (name.length() > MAX_LENGTH) {
            throw new IllegalStateException("FoliaBoard: generated name exceeds " + MAX_LENGTH + " chars: " + name);
        }
        return name;
    }
}
