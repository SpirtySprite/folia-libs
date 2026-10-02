package net.foliaboard.internal.nametag;

import net.foliaboard.api.Nametag;
import net.foliaboard.internal.packet.PacketAdapter;
import net.foliaboard.internal.packet.TeamData;
import net.foliaboard.internal.scheduler.Schedulers;
import net.foliaboard.internal.service.BoardRuntime;
import net.foliaboard.internal.service.NametagService;
import net.foliacommons.scheduler.DeterministicScheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class NametagLeaseTest {
    @Test
    void leasesRestoreOnlyOwnedVisibilityAndRespectExternalMutations() throws Exception {
        try (var bukkit = Mockito.mockStatic(Bukkit.class); var scheduler = new DeterministicScheduler()) {
            Schedulers.setSchedulerForTesting(scheduler);
            try {
                Player player = Mockito.mock(Player.class);
                when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.isOnline()).thenReturn(true);
                when(player.getName()).thenReturn("Owner");
                bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
                Plugin plugin = Mockito.mock(Plugin.class); when(plugin.isEnabled()).thenReturn(true);
                PacketAdapter adapter = Mockito.mock(PacketAdapter.class);
                var visible = new ArrayList<TeamData.Visibility>();
                Mockito.doAnswer(call -> { visible.add(((TeamData) call.getArgument(1)).nametagVisibility()); return null; })
                        .when(adapter).updateTeam(any(), any());
                var service = new NametagService(new BoardRuntime(plugin, adapter));
                AutoCloseable first=service.leaseVisibility(player), second=service.leaseVisibility(player);
                scheduler.advanceTicks(2); first.close(); assertEquals(1, service.active());
                second.close(); scheduler.advanceTicks(2); assertEquals(0, service.active());
                AutoCloseable released=service.leaseVisibility(player);
                released.close();
                AutoCloseable reacquired=service.leaseVisibility(player);
                scheduler.advanceTicks(2); assertEquals(1, service.active());
                reacquired.close(); scheduler.advanceTicks(2); assertEquals(0, service.active());
                var tag=service.get(player); tag.prefix(Component.text("custom")).apply();
                AutoCloseable existing=service.leaseVisibility(player); scheduler.advanceTicks(2);
                assertEquals(TeamData.Visibility.NEVER, visible.getLast());
                existing.close(); scheduler.advanceTicks(2); assertEquals(TeamData.Visibility.ALWAYS, visible.getLast());
                AutoCloseable changed=service.leaseVisibility(player); scheduler.advanceTicks(2);
                tag.nametagVisibility(Nametag.Visibility.HIDE_FOR_OWN_TEAM).apply(); scheduler.advanceTicks(2);
                changed.close(); scheduler.advanceTicks(2);
                assertEquals(TeamData.Visibility.HIDE_FOR_OWN_TEAM, visible.getLast());
                assertEquals(1, service.active());
                service.closeAll();
            } finally { Schedulers.setSchedulerForTesting(null); }
        }
    }
}
