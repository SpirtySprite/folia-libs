package io.github.spirtysprite.integration;

import net.foliaboard.FoliaBoard;
import net.foliaboard.api.display.DisplayVisibility;
import net.foliaboard.api.display.ManagedNametag;
import net.foliaboard.api.display.NametagLayout;
import net.foliaboard.api.display.NametagProfile;
import net.foliaboard.api.display.NametagRefresh;
import net.foliaboard.api.display.NametagTransition;
import net.foliacommons.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class NametagCompositionScenario {
    private final FoliaBoard board;
    private final Player player;
    private final Scheduler scheduler;
    private ManagedNametag<Double> tag;
    private ManagedNametag<Boolean> mobTag;
    private ArmorStand mob;
    private NametagProfile profile;

    private NametagCompositionScenario(FoliaBoard board, Player player, Scheduler scheduler) {
        this.board=board; this.player=player; this.scheduler=scheduler;
    }

    static CompletableFuture<String> run(FoliaBoard board, Player player, Scheduler scheduler) {
        var test=new NametagCompositionScenario(board, player, scheduler);
        return test.run().whenComplete((value, failure) -> test.cleanup());
    }

    private CompletableFuture<String> run() {
        return entity(() -> {
            profile=NametagProfile.builder().layout(NametagLayout.builder()
                    .text("first", Component.text("BoardCompositionFirst"), 0.25)
                    .text("second", Component.text("BoardCompositionSecond"), 0.55)
                    .item("icon", new ItemStack(Material.EMERALD), 0.9).build())
                    .visibility(DisplayVisibility.builder().selfVisible(true).build())
                    .refresh(new NametagRefresh(2, 3)).transition(new NametagTransition(3, 3)).build();
            board.displays().profiles().register("integration", profile);
            tag=board.displays().nametag(player, profile, owner -> owner.getHeight(), (viewer, snapshot, selected) -> selected.layout());
            tag.replaceVanillaName(true);
            return null;
        }).thenCompose(v -> delay(10)).thenCompose(v -> entity(() -> {
            require(tag.snapshot().orElseThrow() > 0, "owner snapshot missing");
            require(tag.diagnose(player.getUniqueId()).elements() == 3, "composition did not render all elements");
            var layer=tag.layer(profile.toBuilder().layout(NametagLayout.builder()
                    .text("first", Component.text("BoardCompositionLayer"), 0.25).build()).build(), 4, 5);
            require(!layer.isClosed(), "temporary layer closed early");
            return null;
        })).thenCompose(v -> delay(8)).thenCompose(v -> entity(() -> {
            require(tag.diagnose(player.getUniqueId()).elements() == 3, "temporary layout did not restore");
            tag.visible(false);
            return null;
        })).thenCompose(v -> delay(5)).thenCompose(v -> entity(() -> {
            require(board.displays().stats().clientEntities() == 0, "fade-out leaked client entities");
            tag.visible(true); tag.lockOwnerHidden(); tag.refresh();
            mob=player.getWorld().spawn(player.getLocation().clone().add(2, 0, 0), ArmorStand.class);
            mob.setGravity(false); mob.setVisible(false);
            mobTag=board.displays().nametag(mob, profile.toBuilder()
                    .visibility(profile.visibility().toBuilder().hideInvisible(false).build()).build());
            return null;
        })).thenCompose(v -> delay(10)).thenCompose(v -> entity(() -> {
            require(tag.diagnose(player.getUniqueId()).elements() == 0, "owner-hide lock failed");
            require(mobTag.diagnose(player.getUniqueId()).elements() == 3, "mob composition did not render");
            return player.getLocation().clone().add(3, 0, 0);
        })).thenCompose(destination -> scheduler.callForEntity(mob, () -> {
            require(mob.getPassengers().isEmpty(), "composition added real passengers");
            return mob.teleportAsync(destination);
        })).thenCompose(stage -> stage).thenCompose(success -> {
            require(success, "mob teleport failed"); return delay(10);
        }).thenCompose(v -> entity(() -> {
            require(mobTag.diagnose(player.getUniqueId()).elements() == 3, "mob teleport did not recover");
            require(board.displays().stats().transportFailures() == 0, "composition transport failed");
            cleanup(); return null;
        })).thenCompose(v -> delay(5)).thenApply(v -> {
            require(board.displays().stats().clientEntities() == 0, "composition cleanup failed");
            return "atomic text/item compositions, owner sampling, profiles/layers, fades, owner lock, vanilla visibility leases and entity teleport passed";
        });
    }

    private void cleanup() {
        if (tag != null) tag.close();
        if (mobTag != null) mobTag.close();
        if (mob != null) { ArmorStand entity=mob; scheduler.runForEntity(entity, entity::remove, null); mob=null; }
        board.displays().profiles().remove("integration");
    }

    private <T> CompletableFuture<T> entity(Supplier<T> call) { return scheduler.callForEntity(player, call); }
    private CompletableFuture<Void> delay(long ticks) {
        var result=new CompletableFuture<Void>();
        if (!scheduler.runForEntityLater(player, () -> result.complete(null),
                () -> result.completeExceptionally(new IllegalStateException("composition player retired")), ticks)) {
            result.completeExceptionally(new IllegalStateException("composition delay rejected"));
        }
        return result;
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
