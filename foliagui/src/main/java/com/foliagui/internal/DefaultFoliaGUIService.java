package com.foliagui.internal;

import com.foliagui.FoliaGUI;
import com.foliagui.FoliaGUIService;
import com.foliagui.FoliaGUIStats;
import com.foliagui.gui.GuiNavigation;
import com.foliagui.gui.GuiRegistry;
import com.foliagui.gui.GuiSessions;
import com.foliagui.gui.GuiTheme;
import com.foliagui.item.GuiItem;
import com.foliagui.listener.GuiListener;
import com.foliagui.scheduler.PaperFoliaScheduler;
import com.foliagui.scheduler.Scheduler;
import net.foliacommons.diagnostics.Diagnostics;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class DefaultFoliaGUIService implements FoliaGUIService {

    private static final String SIGN_EVENT = "io.papermc.paper.event.packet.UncheckedSignChangeEvent";

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final GuiRegistry guis;
    private final GuiNavigation navigation;
    private final GuiSessions sessions = new GuiSessions();
    private final GuiListener listener;
    private final org.bukkit.event.Listener signListener;
    private volatile GuiTheme theme = new GuiTheme();
    private volatile boolean closed;

    public DefaultFoliaGUIService(@NotNull Plugin owner) {
        this.plugin = Objects.requireNonNull(owner, "owner plugin cannot be null");
        this.scheduler = new PaperFoliaScheduler(owner);
        this.guis = new GuiRegistry(this);
        this.navigation = new GuiNavigation(this);
        this.listener = new GuiListener(this);
        owner.getServer().getPluginManager().registerEvents(listener, owner);
        // Sign input needs an event that older servers lack, and its listener must stay out of GuiListener.
        this.signListener = present(SIGN_EVENT) ? new com.foliagui.listener.SignChangeListener(this) : null;
        if (signListener != null) {
            owner.getServer().getPluginManager().registerEvents(signListener, owner);
        }
        Bukkit.getServicesManager().register(FoliaGUIService.class, this, owner, ServicePriority.Normal);
    }

    @Override
    public @NotNull Plugin plugin() {
        return plugin;
    }

    @Override
    public @NotNull Scheduler scheduler() {
        return scheduler;
    }

    @Override
    public @NotNull NamespacedKey itemKey() {
        return GuiItem.IDENTITY_KEY;
    }

    @Override
    public @NotNull GuiTheme theme() {
        return theme;
    }

    @Override
    public void theme(@NotNull GuiTheme replacement) {
        this.theme = Objects.requireNonNull(replacement, "theme cannot be null");
    }

    @Override
    public @NotNull GuiRegistry guis() {
        return guis;
    }

    @Override
    public @NotNull GuiNavigation navigation() {
        return navigation;
    }

    @Override
    public @NotNull GuiSessions sessions() {
        return sessions;
    }

    @Override
    public @NotNull FoliaGUIStats stats() {
        return new FoliaGUIStats(guis.openCount(), sessions.anvilCount(), sessions.signCount(),
                sessions.merchantCount(), sessions.chatCount());
    }

    @Override
    public @NotNull Diagnostics diagnose() {
        FoliaGUIStats stats = stats();
        return Diagnostics.named("FoliaGUI " + FoliaGUI.VERSION)
                .withEnvironment()
                .section("Service")
                .info("Owner plugin", plugin.getName())
                .info("Scheduler", scheduler.isFolia() ? "Folia regions" : "Paper main thread")
                .info("State", closed ? "closed" : "running")
                .section("Features")
                .ok("Inventory GUIs")
                .feature("Anvil text input", present("org.bukkit.inventory.view.AnvilView"),
                        "AnvilView is missing; needs Paper 1.21 or newer")
                .feature("Sign text input", present(SIGN_EVENT),
                        "UncheckedSignChangeEvent is missing; needs a recent Paper")
                .feature("Merchant windows", present("org.bukkit.inventory.MerchantInventory"),
                        "MerchantInventory is missing")
                .feature("Chat input", present("io.papermc.paper.event.player.AsyncChatEvent"),
                        "AsyncChatEvent is missing; needs Paper")
                .section("Runtime")
                .info("Open GUIs", String.valueOf(stats.openGuis()))
                .info("Anvil sessions", String.valueOf(stats.anvilSessions()))
                .info("Sign sessions", String.valueOf(stats.signSessions()))
                .info("Merchant sessions", String.valueOf(stats.merchantSessions()))
                .info("Chat prompts", String.valueOf(stats.chatPrompts()))
                .build();
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, DefaultFoliaGUIService.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        guis.closeAll();
        guis.clearAll();
        sessions.clearAll();
        navigation.clearAll();
        HandlerList.unregisterAll(listener);
        if (signListener != null) {
            HandlerList.unregisterAll(signListener);
        }
        Bukkit.getServicesManager().unregister(FoliaGUIService.class, this);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
