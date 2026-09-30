package net.foliacommons.version;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The Minecraft version of the running server.
 *
 * <p>Mojang switched from {@code 1.x.y} to calendar-style numbers (26.1 and later). On a calendar
 * version every {@code 1.x} feature check passes, because the release is newer than all of them.
 */
public final class ServerVersion implements Comparable<ServerVersion> {
    private final int major;
    private final int minor;
    private final int patch;

    private ServerVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /** The version of the server we are running on. */
    public static @NotNull ServerVersion current() {
        String raw;
        try {
            raw = Bukkit.getMinecraftVersion();
        } catch (Throwable unavailable) {
            raw = Bukkit.getBukkitVersion();
        }
        return parse(raw);
    }

    /**
     * Reads strings like {@code 1.21.4}, {@code 1.21.4-R0.1-SNAPSHOT} or {@code 26.1}. Parts that are
     * missing or not numeric count as zero.
     */
    public static @NotNull ServerVersion parse(@NotNull String raw) {
        Objects.requireNonNull(raw, "raw");
        String cleaned = raw.split("-")[0].trim();
        String[] parts = cleaned.split("\\.");
        return new ServerVersion(part(parts, 0), part(parts, 1), part(parts, 2));
    }

    public static @NotNull ServerVersion of(int major, int minor, int patch) {
        return new ServerVersion(major, minor, patch);
    }

    private static int part(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    /** True for the calendar-style numbering that replaced {@code 1.x.y}. */
    public boolean isCalendarScheme() {
        return major != 1;
    }

    /** True if this is Minecraft 1.{@code minor}.{@code patch} or newer. Calendar versions always qualify. */
    public boolean isAtLeast(int minor, int patch) {
        if (isCalendarScheme()) {
            return true;
        }
        if (this.minor != minor) {
            return this.minor > minor;
        }
        return this.patch >= patch;
    }

    @Override
    public int compareTo(@NotNull ServerVersion other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ServerVersion version && compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
