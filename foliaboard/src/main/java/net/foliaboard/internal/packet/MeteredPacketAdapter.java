package net.foliaboard.internal.packet;

import net.foliaboard.api.PresentationStats.Surface;
import net.foliaboard.api.format.NumberFormat;
import net.foliaboard.internal.metrics.PacketMetrics;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Collection;

public final class MeteredPacketAdapter implements PacketAdapter {
    private final PacketAdapter delegate;
    private final PacketMetrics metrics;

    public MeteredPacketAdapter(PacketAdapter delegate, PacketMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    private Surface objective(String id) {
        return id.endsWith("_bn") || id.endsWith("_tab") ? Surface.OBJECTIVE : Surface.SIDEBAR;
    }

    @Override
    public String describe() {
        return delegate.describe();
    }

    @Override
    public boolean supportsPerViewerTab() {
        return delegate.supportsPerViewerTab();
    }

    @Override
    public boolean tabDisplayName(Player viewer, Player target, Component name) {
        metrics.requested(Surface.TAB);
        boolean changed = delegate.tabDisplayName(viewer, target, name);
        if (changed) {
            metrics.changed(Surface.TAB);
        }
        return changed;
    }

    @Override
    public void createObjective(Player viewer, String id, Component title) {
        delegate.createObjective(viewer, id, title);
        metrics.changed(objective(id));
    }

    @Override
    public void updateObjective(Player viewer, String id, Component title) {
        delegate.updateObjective(viewer, id, title);
        metrics.changed(objective(id));
    }

    @Override
    public void removeObjective(Player viewer, String id) {
        delegate.removeObjective(viewer, id);
        metrics.changed(objective(id));
    }

    @Override
    public void setDisplaySlot(Player viewer, String id, DisplaySlotType slot) {
        delegate.setDisplaySlot(viewer, id, slot);
        metrics.changed(objective(id));
    }

    @Override
    public void setScore(Player viewer, String id, String entry, int value, Component name, NumberFormat format) {
        delegate.setScore(viewer, id, entry, value, name, format);
        metrics.changed(objective(id));
    }

    @Override
    public void resetScore(Player viewer, String id, String entry) {
        delegate.resetScore(viewer, id, entry);
        metrics.changed(objective(id));
    }

    @Override
    public void createTeam(Player viewer, TeamData team, Collection<String> entries) {
        delegate.createTeam(viewer, team, entries);
        metrics.changed(Surface.TEAM);
    }

    @Override
    public void updateTeam(Player viewer, TeamData team) {
        delegate.updateTeam(viewer, team);
        metrics.changed(Surface.TEAM);
    }

    @Override
    public void removeTeam(Player viewer, String name) {
        delegate.removeTeam(viewer, name);
        metrics.changed(Surface.TEAM);
    }

    @Override
    public void teamEntries(Player viewer, String name, Collection<String> entries, boolean add) {
        delegate.teamEntries(viewer, name, entries, add);
        metrics.changed(Surface.TEAM);
    }
}
