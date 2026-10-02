package io.github.spirtysprite.integration;

import net.foliaboard.FoliaBoard;
import net.foliaboard.api.display.DisplayVisibility;
import net.foliaboard.api.display.ManagedItemDisplay;
import net.foliaboard.api.display.ManagedTextDisplay;
import net.foliacommons.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class DisplayScenario {
    private final FoliaBoard board;
    private final Player player;
    private final Scheduler scheduler;
    private ManagedTextDisplay fixedText;
    private ManagedItemDisplay fixedItem;
    private ManagedTextDisplay nametag;
    private ManagedItemDisplay attachedItem;
    private ArmorStand passenger;
    private FoliaBoard peer;
    private Location origin;

    private DisplayScenario(FoliaBoard board, Player player, Scheduler scheduler) {
        this.board = board;
        this.player = player;
        this.scheduler = scheduler;
    }

    static CompletableFuture<String> run(FoliaBoard board, Player player, Scheduler scheduler) {
        DisplayScenario scenario = new DisplayScenario(board, player, scheduler);
        return scenario.run().whenComplete((value, failure) -> scenario.cleanup());
    }

    private CompletableFuture<String> run() {
        return entity(() -> {
            require(board.displays().supported(), "display backend did not initialize");
            origin = player.getLocation();
            fixedText = board.displays().text(origin.clone().add(2, 2, 0), Component.text("BoardFixedText"));
            fixedItem = board.displays().item(origin.clone().add(2, 1, 0), new ItemStack(Material.DIAMOND));
            nametag = board.displays().nametag(player, Component.text("BoardNeverSelf"));
            attachedItem = board.displays().item(player, 0.8, new ItemStack(Material.APPLE));
            return null;
        }).thenCompose(v -> delay(8)).thenCompose(v -> entity(() -> {
            require(board.displays().stats().clientEntities() == 2, "default owner hiding failed");
            nametag.refresh();
            nametag.show(player.getUniqueId());
            nametag.visible(false);
            nametag.visible(true);
            return null;
        })).thenCompose(v -> delay(5)).thenCompose(v -> entity(() -> {
            require(board.displays().stats().clientEntities() == 2, "refresh or show overrode self hiding");
            fixedText.style(fixedText.style().toBuilder().teleportTicks(5).interpolationTicks(5).build());
            fixedText.location(origin.clone().add(3, 2, 0));
            nametag.styleFor(viewer -> net.foliaboard.api.display.DisplayStyle.builder().glowing(true).build());
            nametag.textStyleFor(viewer -> net.foliaboard.api.display.TextDisplayStyle.builder().background(0x80000000).build());
            passenger = (ArmorStand) player.getWorld().spawnEntity(player.getLocation(), EntityType.ARMOR_STAND);
            passenger.setGravity(false);
            passenger.setVisible(false);
            require(player.addPassenger(passenger), "native test passenger could not mount");
            nametag.text(Component.text("BoardPassengerVisible"));
            nametag.visibility(new DisplayVisibility(48, true, true, false, true));
            attachedItem.visibility(new DisplayVisibility(48, true, true, false, true));
            peer = FoliaBoard.create(board.plugin());
            var peerTag = peer.displays().nametag(player, Component.text("BoardPeerPassenger"));
            peerTag.visibility(new DisplayVisibility(48, true, true, false, true));
            return null;
        })).thenCompose(v -> delay(8)).thenCompose(v -> entity(() -> {
            require(board.displays().stats().clientEntities() == 4, "explicit passenger presentation failed");
            require(player.getPassengers().contains(passenger), "native passenger was modified");
            nametag.close();
            attachedItem.close();
            return null;
        })).thenCompose(v -> delay(5)).thenCompose(v -> entity(() -> {
            require(board.displays().stats().clientEntities() == 2, "passenger displays did not remove");
            require(player.getPassengers().contains(passenger), "display cleanup removed a native passenger");
            require(peer.displays().stats().clientEntities() == 1, "closing one instance removed another's display");
            player.removePassenger(passenger);
            passenger.remove();
            passenger = null;
            nametag = board.displays().nametag(player, Component.text("BoardPassengerVisible"));
            nametag.visibility(new DisplayVisibility(48, true, true, false, true));
            return null;
        })).thenCompose(v -> delay(5)).thenCompose(v -> entity(() -> {
            var cancelled = new org.bukkit.event.player.PlayerTeleportEvent(player, player.getLocation(), origin.clone().add(5, 0, 0));
            cancelled.setCancelled(true);
            Bukkit.getPluginManager().callEvent(cancelled);
            return null;
        })).thenCompose(v -> delay(1)).thenCompose(v -> entity(() -> {
            require(board.displays().stats().clientEntities() == 3, "cancelled teleport reset displays");
            player.setSneaking(true);
            return null;
        })).thenCompose(v -> delay(3)).thenCompose(v -> entity(() -> {
            player.setSneaking(false);
            return player.teleportAsync(origin.clone().add(1, 0, 0));
        })).thenCompose(stage -> stage).thenCompose(success -> {
            require(success, "same-world teleport failed");
            return delay(10);
        }).thenCompose(v -> entity(() -> {
            require(board.displays().stats().clientEntities() == 3, "same-world teleport failed to recover displays");
            return null;
        })).thenCompose(v -> scheduler.callGlobal(() -> Bukkit.getWorlds().stream()
                .filter(world -> !world.getUID().equals(origin.getWorld().getUID())).findFirst().orElse(null)))
                .thenCompose(destination -> crossWorld(destination)).thenCompose(v -> entity(() -> {
            nametag.text(Component.text("BoardNeverSelf"));
            nametag.visibility(DisplayVisibility.defaults());
            nametag.refresh();
            return null;
        })).thenCompose(v -> delay(8)).thenCompose(v -> entity(() -> {
            require(board.displays().stats().clientEntities() == 2, "self hiding did not survive recreation");
            require(board.displays().stats().transportFailures() == 0, "display packet transport failed");
            cleanup();
            return null;
        })).thenCompose(v -> delay(5)).thenApply(v -> {
            require(board.displays().stats().handles() == 0, "display handles leaked");
            require(board.displays().stats().clientEntities() == 0, "client displays leaked");
            return "text, items, client passengers, native passenger preservation, self hiding and teleport recovery passed";
        });
    }

    private CompletableFuture<Void> crossWorld(World destination) {
        if (destination == null) return CompletableFuture.failedFuture(new IllegalStateException("no second test world"));
        return entity(() -> player.teleportAsync(new Location(destination, 0.5, 100, 0.5)))
                .thenCompose(stage -> stage).thenCompose(success -> {
                    require(success, "cross-world teleport failed");
                    return delay(10);
                }).thenCompose(v -> entity(() -> {
                    require(board.displays().stats().clientEntities() == 1, "cross-world passenger recovery failed");
                    return player.teleportAsync(origin);
                })).thenCompose(stage -> stage).thenCompose(success -> {
                    require(success, "return teleport failed");
                    return delay(10);
                });
    }

    private void cleanup() {
        if (fixedText != null) fixedText.close();
        if (fixedItem != null) fixedItem.close();
        if (nametag != null) nametag.close();
        if (attachedItem != null) attachedItem.close();
        if (peer != null) peer.close();
        if (passenger != null) {
            ArmorStand remaining = passenger;
            scheduler.runForEntity(remaining, remaining::remove, null);
            passenger = null;
        }
    }

    private <T> CompletableFuture<T> entity(Supplier<T> operation) {
        return scheduler.callForEntity(player, operation);
    }

    private CompletableFuture<Void> delay(long ticks) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (!scheduler.runForEntityLater(player, () -> result.complete(null),
                () -> result.completeExceptionally(new IllegalStateException("display viewer disconnected")), ticks)) {
            result.completeExceptionally(new IllegalStateException("display delay rejected"));
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
