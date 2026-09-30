package net.foliabench.gui;

import com.foliagui.FoliaGUI;
import com.foliagui.FoliaGUIService;
import org.bukkit.plugin.Plugin;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Starts MockBukkit and a FoliaGUI service once per benchmark fork. */
final class MockServerState {
    final FoliaGUIService service;

    MockServerState() {
        MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin("Bench");
        this.service = FoliaGUI.create(plugin);
    }

    void close() {
        service.close();
        MockBukkit.unmock();
    }
}
