package net.foliacommons.version;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Minecraft version of the running server.
 *
 * <p>Mojang switched from {@code 1.x.y} to calendar-style numbers (26.1 and later). On a calendar
 * version every {@code 1.x} feature check passes, because the release is newer than all of them.
 */
public final class ServerVersion implements Comparable<ServerVersion> {
    private static final Pattern VERSION = Pattern.compile("([0-9]+)(?:\\.([0-9]+))?(?:\\.([0-9]+))?(?:-.*)?");
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
     * missing count as zero. Unrecognized or overflowing strings return {@code 0.0.0}, which never
     * passes a feature check.
     */
    public static @NotNull ServerVersion parse(@NotNull String raw) {
        Objects.requireNonNull(raw, "raw");
        Matcher matcher = VERSION.matcher(raw.trim());
        if (!matcher.matches()) {
            return new ServerVersion(0, 0, 0);
        }
        try {
            return new ServerVersion(part(matcher.group(1)), part(matcher.group(2)), part(matcher.group(3)));
        } catch (NumberFormatException overflow) {
            return new ServerVersion(0, 0, 0);
        }
    }

    public static @NotNull ServerVersion of(int major, int minor, int patch) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version components must be non-negative");
        }
        return new ServerVersion(major, minor, patch);
    }

    private static int part(String value) {
        return value == null ? 0 : Integer.parseInt(value);
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
        return major >= 26;
    }

    /** True if this is Minecraft 1.{@code minor}.{@code patch} or newer. Calendar versions always qualify. */
    public boolean isAtLeast(int minor, int patch) {
        if (isCalendarScheme()) {
            return true;
        }
        if (major != 1) {
            return false;
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
