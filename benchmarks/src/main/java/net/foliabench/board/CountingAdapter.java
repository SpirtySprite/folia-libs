package net.foliabench.board;

import net.foliaboard.api.format.NumberFormat;
import net.foliaboard.internal.packet.DisplaySlotType;
import net.foliaboard.internal.packet.PacketAdapter;
import net.foliaboard.internal.packet.TeamData;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Collection;

/** A packet adapter that only counts what it is asked to send. */
final class CountingAdapter implements PacketAdapter {
    long packets;

    @Override
    public String describe() {
        return "counting";
    }

    @Override
    public void createObjective(Player viewer, String objectiveId, Component title) {
        packets++;
    }

    @Override
    public void updateObjective(Player viewer, String objectiveId, Component title) {
        packets++;
    }

    @Override
    public void removeObjective(Player viewer, String objectiveId) {
        packets++;
    }

    @Override
    public void setDisplaySlot(Player viewer, String objectiveId, DisplaySlotType slot) {
        packets++;
    }

    @Override
    public void setScore(Player viewer, String objectiveId, String entry, int value, Component displayName,
                         NumberFormat numberFormat) {
        packets++;
    }

    @Override
    public void resetScore(Player viewer, String objectiveId, String entry) {
        packets++;
    }

    @Override
    public void createTeam(Player viewer, TeamData team, Collection<String> entries) {
        packets++;
    }

    @Override
    public void updateTeam(Player viewer, TeamData team) {
        packets++;
    }

    @Override
    public void removeTeam(Player viewer, String teamName) {
        packets++;
    }

    @Override
    public void teamEntries(Player viewer, String teamName, Collection<String> entries, boolean add) {
        packets++;
    }
}
