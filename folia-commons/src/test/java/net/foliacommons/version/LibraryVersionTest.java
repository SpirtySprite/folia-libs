package net.foliacommons.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LibraryVersionTest {

    @Test
    void readsTheVersionProperty() {
        assertEquals("2.3.4", LibraryVersion.read(LibraryVersionTest.class, "/test-version.properties"));
    }

    @Test
    void aMissingResourceIsUnknown() {
        assertEquals(LibraryVersion.UNKNOWN, LibraryVersion.read(LibraryVersionTest.class, "/nope.properties"));
    }

    @Test
    void anUnfilteredPlaceholderIsUnknown() {
        assertEquals(LibraryVersion.UNKNOWN,
                LibraryVersion.read(LibraryVersionTest.class, "/unfiltered-version.properties"));
    }

    @Test
    void aFileWithoutTheKeyIsUnknown() {
        assertEquals(LibraryVersion.UNKNOWN, LibraryVersion.read(LibraryVersionTest.class, "/empty.properties"));
    }

    @Test
    void aBrokenEscapeIsUnknownInsteadOfAnException() {
        assertEquals(LibraryVersion.UNKNOWN, LibraryVersion.read(LibraryVersionTest.class, "/broken.properties"));
    }
}
