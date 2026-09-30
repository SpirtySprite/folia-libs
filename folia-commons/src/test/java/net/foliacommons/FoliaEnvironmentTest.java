package net.foliacommons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FoliaEnvironmentTest {

    @Test
    void aPlainClasspathIsNotFolia() {
        assertFalse(FoliaEnvironment.isFolia());
    }
}
