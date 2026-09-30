package net.foliaboard.internal.service;

import net.foliaboard.api.Nametag;
import net.foliaboard.api.NametagBuilder;
import net.foliaboard.api.Nametags;
import net.foliaboard.internal.nametag.NametagManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NametagService implements Nametags {
    private final BoardRuntime runtime;
    private final NametagManager manager;

    public NametagService(@NotNull BoardRuntime runtime) {
        this.runtime = runtime;
        this.manager = new NametagManager(runtime.plugin(), runtime.adapter(), runtime.namespace());
    }

    @Override
    public @NotNull NametagBuilder builder(@NotNull Player target) {
        runtime.ensureOpen();
        return new NametagBuilder(this, target);
    }

    @Override
    public @NotNull Nametag get(@NotNull Player target) {
        runtime.ensureOpen();
        return manager.get(target);
    }

    /** Used by the builder: creates the nametag without sending it, so the builder can configure it first. */
    public @NotNull Nametag prepare(@NotNull Player target, @Nullable Integer sortWeight) {
        runtime.ensureOpen();
        return manager.get(target, sortWeight, false);
    }

    @Override
    public @Nullable Nametag getIfPresent(@NotNull Player target) {
        return manager.getIfPresent(target);
    }

    public int active() {
        return manager.active();
    }

    public void onJoin(@NotNull Player player) {
        manager.onJoin(player);
    }

    public void onQuit(@NotNull Player player) {
        manager.onQuit(player);
    }

    public void closeAll() {
        manager.closeAll();
    }
}
