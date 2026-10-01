package net.foliacommons.version;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Objects;

/**
 * Reads a library's own version from a small properties resource that the build fills in.
 *
 * <p>The manifest is not used on purpose: when a library is shaded into a plugin, the manifest that
 * Java sees belongs to the plugin, not to the library.
 */
public final class LibraryVersion {
    public static final String UNKNOWN = "unknown";

    private LibraryVersion() {
    }

    /**
     * @param anchor       a class from the library, used to find its resources
     * @param resourceName absolute resource path such as {@code /foliaboard-version.properties}
     * @return the {@code version} property, or {@value #UNKNOWN} if the resource is missing or unreadable
     */
    public static @NotNull String read(@NotNull Class<?> anchor, @NotNull String resourceName) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(resourceName, "resourceName");
        try (InputStream in = anchor.getResourceAsStream(resourceName)) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version", UNKNOWN).trim();
            return version.isEmpty() || version.startsWith("${") ? UNKNOWN : version;
        } catch (IOException | IllegalArgumentException unreadable) {
            return UNKNOWN;
        }
    }
}
