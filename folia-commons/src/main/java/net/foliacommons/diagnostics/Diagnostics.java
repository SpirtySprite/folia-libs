package net.foliacommons.diagnostics;

import net.foliacommons.FoliaEnvironment;
import net.foliacommons.version.ServerVersion;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A small report a library can print so users can paste one block into a bug report.
 *
 * <pre>{@code
 * Diagnostics report = Diagnostics.named("FoliaBoard 1.1.0")
 *         .withEnvironment()
 *         .section("Features")
 *         .ok("Sidebars")
 *         .degraded("Per-viewer tab names", "player info packet not found")
 *         .build();
 * getLogger().info(report.toString());
 * }</pre>
 */
public final class Diagnostics {
    private final String title;
    private final List<Section> sections;

    private Diagnostics(String title, List<Section> sections) {
        this.title = title;
        this.sections = sections;
    }

    public static @NotNull Builder named(@NotNull String title) {
        return new Builder(title);
    }

    public @NotNull String title() {
        return title;
    }

    /** True if no feature was reported as degraded or unavailable. */
    public boolean healthy() {
        return sections.stream().flatMap(section -> section.lines.stream()).allMatch(line -> line.status == Status.OK
                || line.status == Status.INFO);
    }

    /** Names of the features that are degraded or unavailable. */
    public @NotNull List<String> problems() {
        List<String> names = new ArrayList<>();
        for (Section section : sections) {
            for (Line line : section.lines) {
                if (line.status == Status.DEGRADED || line.status == Status.UNAVAILABLE) {
                    names.add(line.name);
                }
            }
        }
        return names;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("=== ").append(title).append(" ===");
        for (Section section : sections) {
            out.append('\n');
            if (section.name != null) {
                out.append(section.name).append(":\n");
            }
            for (Line line : section.lines) {
                out.append("  ").append(line.status.symbol).append(' ').append(line.name);
                if (line.detail != null && !line.detail.isBlank()) {
                    out.append(": ").append(line.detail);
                }
                out.append('\n');
            }
        }
        return out.toString().stripTrailing();
    }

    private enum Status {
        OK("[ok]"), INFO("[--]"), DEGRADED("[!!]"), UNAVAILABLE("[xx]");

        private final String symbol;

        Status(String symbol) {
            this.symbol = symbol;
        }
    }

    private record Line(Status status, String name, @Nullable String detail) {
    }

    private static final class Section {
        private final String name;
        private final List<Line> lines = new ArrayList<>();

        private Section(String name) {
            this.name = name;
        }
    }

    public static final class Builder {
        private final String title;
        private final Map<String, Section> sections = new LinkedHashMap<>();
        private Section current;

        private Builder(String title) {
            this.title = Objects.requireNonNull(title, "title");
            this.current = section0(null);
        }

        private Section section0(String name) {
            return sections.computeIfAbsent(name == null ? "" : name, key -> new Section(name));
        }

        /** Starts (or continues) a named group of lines. */
        public @NotNull Builder section(@NotNull String name) {
            this.current = section0(name);
            return this;
        }

        /** Adds the server software, Minecraft version, Java version and whether the server is Folia. */
        public @NotNull Builder withEnvironment() {
            Section previous = current;
            current = section0("Environment");
            String software;
            try {
                software = Bukkit.getName() + " " + Bukkit.getVersion();
            } catch (Throwable noServer) {
                software = "unknown";
            }
            info("Server", software);
            info("Minecraft", ServerVersionSafe.describe());
            info("Threading", FoliaEnvironment.isFolia() ? "Folia (regionised)" : "Paper (main thread)");
            info("Java", System.getProperty("java.version", "unknown"));
            current = previous;
            return this;
        }

        public @NotNull Builder ok(@NotNull String feature) {
            return add(Status.OK, feature, null);
        }

        public @NotNull Builder info(@NotNull String name, @NotNull String value) {
            return add(Status.INFO, name, value);
        }

        /** The feature works, but with reduced behaviour. */
        public @NotNull Builder degraded(@NotNull String feature, @NotNull String reason) {
            return add(Status.DEGRADED, feature, reason);
        }

        /** The feature does not work at all on this server. */
        public @NotNull Builder unavailable(@NotNull String feature, @NotNull String reason) {
            return add(Status.UNAVAILABLE, feature, reason);
        }

        /** Shorthand for {@link #ok} or {@link #unavailable} depending on {@code available}. */
        public @NotNull Builder feature(@NotNull String feature, boolean available, @NotNull String reasonIfMissing) {
            return available ? ok(feature) : unavailable(feature, reasonIfMissing);
        }

        private Builder add(Status status, String name, @Nullable String detail) {
            current.lines.add(new Line(status, name, detail));
            return this;
        }

        public @NotNull Diagnostics build() {
            List<Section> built = new ArrayList<>();
            for (Section section : sections.values()) {
                if (!section.lines.isEmpty()) {
                    built.add(section);
                }
            }
            return new Diagnostics(title, built);
        }
    }

    private static final class ServerVersionSafe {
        static String describe() {
            try {
                return ServerVersion.current().toString();
            } catch (Throwable noServer) {
                return "unknown";
            }
        }
    }
}
