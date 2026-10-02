package io.github.spirtysprite.integration;

import net.foliaboard.FoliaBoard;
import net.foliaboard.api.display.ManagedTextDisplay;
import net.foliacommons.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class DisplayViewerScenario {
    static CompletableFuture<String> run(FoliaBoard board, Player owner, Player viewer, Scheduler scheduler) {
        ManagedTextDisplay first = board.displays().nametag(owner, Component.text("BoardOtherOwner:" + owner.getUniqueId()));
        ManagedTextDisplay second = board.displays().nametag(viewer, Component.text("BoardOtherOwner:" + viewer.getUniqueId()));
        return scheduler.callForEntity(owner, () -> owner.getLocation().clone()).thenCompose(origin ->
                scheduler.callForEntity(viewer, () -> viewer.teleportAsync(origin.clone().add(3, 0, 0)))
                        .thenCompose(stage -> stage).thenCompose(success -> {
                            require(success, "second viewer arrival failed");
                            return delay(scheduler, owner, 12);
                        }).thenApply(v -> {
                            require(first.viewers().equals(Set.of(viewer.getUniqueId())), "owner nametag audience incorrect");
                            require(second.viewers().equals(Set.of(owner.getUniqueId())), "viewer nametag audience incorrect");
                            return null;
                        }).thenCompose(v -> scheduler.callForEntity(owner, () -> {
                            owner.setHealth(0);
                            return null;
                        })).thenCompose(v -> globalDelay(scheduler, 40)).thenCompose(v -> scheduler.callForEntity(owner, () -> {
                            require(!owner.isDead(), "owner did not respawn");
                            return owner.teleportAsync(origin);
                        })).thenCompose(stage -> stage).thenCompose(success -> {
                            require(success, "respawn return failed");
                            return delay(scheduler, owner, 12);
                        }).thenApply(v -> {
                            require(first.viewers().equals(Set.of(viewer.getUniqueId())), "respawn did not restore remote nametag");
                            return null;
                        }).thenCompose(v -> scheduler.callForEntity(owner,
                                () -> owner.teleportAsync(origin.clone().add(300, 0, 0))))
                        .thenCompose(stage -> stage).thenCompose(success -> {
                            require(success, "cross-region teleport failed");
                            return delay(scheduler, owner, 12);
                        }).thenApply(v -> {
                            require(first.viewers().isEmpty() && second.viewers().isEmpty(), "old region retained nametags");
                            return null;
                        }).thenCompose(v -> scheduler.callForEntity(owner, () -> owner.teleportAsync(origin)))
                        .thenCompose(stage -> stage).thenCompose(success -> {
                            require(success, "cross-region return failed");
                            return delay(scheduler, owner, 12);
                        }).thenApply(v -> {
                            require(first.viewers().equals(Set.of(viewer.getUniqueId())), "arrival did not restore remote nametag");
                            first.hide(viewer.getUniqueId());
                            first.show(owner.getUniqueId());
                            first.refresh();
                            return null;
                        }).thenCompose(v -> delay(scheduler, owner, 8)).thenApply(v -> {
                            require(first.viewers().isEmpty(), "explicit hiding was overridden");
                            first.show(viewer.getUniqueId());
                            return null;
                        }).thenCompose(v -> delay(scheduler, owner, 8)).thenApply(v -> {
                            require(first.viewers().equals(Set.of(viewer.getUniqueId())), "explicit audience restoration failed");
                            first.close();
                            second.close();
                            return null;
                        }).thenCompose(v -> delay(scheduler, owner, 8)).thenApply(v -> {
                            require(board.displays().stats().handles() == 0, "remote display handles leaked");
                            require(board.displays().stats().transportFailures() == 0, "remote display transport failed");
                            return "two-viewer self hiding, remote passengers, death/respawn, region transfers and exclusions passed";
                        })).whenComplete((value, failure) -> {
                            first.close();
                            second.close();
                        });
    }

    private static CompletableFuture<Void> delay(Scheduler scheduler, Player player, long ticks) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (!scheduler.runForEntityLater(player, () -> result.complete(null),
                () -> result.completeExceptionally(new IllegalStateException("remote display player retired")), ticks)) {
            result.completeExceptionally(new IllegalStateException("remote display delay rejected"));
        }
        return result;
    }

    private static CompletableFuture<Void> globalDelay(Scheduler scheduler, long ticks) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        scheduler.scheduleGlobalLater(() -> result.complete(null), ticks);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
