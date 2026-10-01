package io.github.spirtysprite.integration;

import net.foliaboard.FoliaBoard;
import net.foliaboard.api.format.NumberFormat;
import net.foliaboard.internal.packet.PacketAdapter;
import net.foliaboard.internal.packet.PacketAdapterFactory;
import net.foliaboard.internal.packet.TeamData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class BoardFixturePlugin extends JavaPlugin {
    private FoliaBoard board;
    private PacketAdapter external;

    @Override
    public void onEnable() {
        board = FoliaBoard.create(this, Bukkit.getPluginManager().getPlugin("FoliaIntegration"));
        external = PacketAdapterFactory.create(getLogger());
    }

    public void install(Player player) {
        String label = getName();
        int value = label.endsWith("A") ? 11 : 22;
        board.createBoard(player).title(label).line("Owned row").build();
        board.objectives().belowName().scoreFor(player, player.getName(), value).score(player.getName(), 5);
        board.nametag(player).prefix(Component.text(label)).apply();
        board.tab(player).header(label).name(label).order(value).build();
        board.bossBar(player, "owned").text(label).show();
        if (label.endsWith("B")) {
            external.createObjective(player, "foreign-objective", Component.text("Foreign objective"));
            external.setScore(player, "foreign-objective", "Foreign entry", 31, Component.text("Foreign score"), NumberFormat.blank());
            external.createTeam(player, new TeamData("foreign-team"), List.of("Foreign entry"));
        }
    }

    public void hideObjective() {
        board.objectives().belowName().hide();
        board.objectives().belowName().score("Hidden", 9);
        board.objectives().belowName().show();
    }

    @Override
    public void onDisable() {
        if (board != null) {
            board.close();
        }
    }
}
