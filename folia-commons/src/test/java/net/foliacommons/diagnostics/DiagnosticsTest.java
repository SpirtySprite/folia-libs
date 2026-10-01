package net.foliacommons.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsTest {

    @Test
    void builtReportsDoNotChangeWhenTheirBuilderIsReused() {
        Diagnostics.Builder builder = Diagnostics.named("Library").section("Features").ok("Scheduling");
        Diagnostics first = builder.build();
        String text = first.toString();
        builder.unavailable("Packets", "missing");
        Diagnostics second = builder.build();

        assertTrue(first.healthy());
        assertEquals(text, first.toString());
        assertEquals(1, first.entries().size());
        assertFalse(second.healthy());
        assertEquals(Diagnostics.Status.UNAVAILABLE, second.entries().get(1).status());
        assertEquals("Features", second.entries().get(1).section().orElseThrow());
        assertEquals("missing", second.entries().get(1).detail().orElseThrow());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> first.entries().clear());
    }

    @Test
    void aReportWithOnlyWorkingFeaturesIsHealthy() {
        Diagnostics report = Diagnostics.named("Lib 1.0").section("Features").ok("A").ok("B").build();

        assertTrue(report.healthy());
        assertTrue(report.problems().isEmpty());
    }

    @Test
    void degradedAndUnavailableFeaturesAreListedAsProblems() {
        Diagnostics report = Diagnostics.named("Lib 1.0")
                .section("Features")
                .ok("Sidebars")
                .degraded("Tab names", "reduced")
                .unavailable("Skins", "class missing")
                .build();

        assertFalse(report.healthy());
        assertEquals(List.of("Tab names", "Skins"), report.problems());
    }

    @Test
    void infoLinesDoNotCountAsProblems() {
        Diagnostics report = Diagnostics.named("Lib").info("Backend", "nms").build();

        assertTrue(report.healthy());
    }

    @Test
    void featureHelperPicksTheRightStatus() {
        Diagnostics report = Diagnostics.named("Lib")
                .feature("Works", true, "n/a")
                .feature("Broken", false, "no class")
                .build();

        assertEquals(List.of("Broken"), report.problems());
    }

    @Test
    void theTextIsReadableAndPasteable() {
        Diagnostics report = Diagnostics.named("FoliaBoard 1.1.0")
                .section("Features")
                .ok("Sidebars")
                .degraded("Tab names", "player info packet not found")
                .build();

        String text = report.toString();

        assertTrue(text.startsWith("=== FoliaBoard 1.1.0 ==="));
        assertTrue(text.contains("Features:"));
        assertTrue(text.contains("[ok] Sidebars"));
        assertTrue(text.contains("[!!] Tab names: player info packet not found"));
    }

    @Test
    void emptySectionsAreLeftOut() {
        Diagnostics report = Diagnostics.named("Lib").section("Unused").section("Used").ok("X").build();

        assertFalse(report.toString().contains("Unused"));
        assertTrue(report.toString().contains("Used"));
    }

    @Test
    void withEnvironmentAddsServerFactsWithoutAServer() {
        Diagnostics report = Diagnostics.named("Lib").withEnvironment().build();

        String text = report.toString();
        assertTrue(text.contains("Environment:"));
        assertTrue(text.contains("Java"));
        assertTrue(text.contains("Threading"));
        assertTrue(report.healthy());
    }
}
