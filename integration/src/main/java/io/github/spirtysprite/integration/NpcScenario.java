package io.github.spirtysprite.integration;

import net.foliacommons.scheduler.Scheduler;
import net.folianpc.api.FoliaNpc;
import net.folianpc.api.MovementResult;
import net.folianpc.api.NametagLayout;
import net.folianpc.api.NavigationOptions;
import net.folianpc.api.Npc;
import net.folianpc.api.NpcAppearance;
import net.folianpc.api.PatrolOptions;
import net.folianpc.api.ViewerAppearance;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

final class NpcScenario {
    private NpcScenario() { }

    static CompletableFuture<String> run(FoliaNpc service, Player player, Scheduler scheduler) {
        return scheduler.callForEntity(player, () -> player.getLocation().clone()).thenCompose(origin -> {
            World world = origin.getWorld();
            if (world == null) return CompletableFuture.failedFuture(new IllegalStateException("Missing viewer world"));
            int edge = Math.floorDiv(origin.getBlockX(), 256) * 256 + 255;
            int z = origin.getBlockZ();
            int y = origin.getBlockY();
            List<CompletableFuture<Void>> preparation = new ArrayList<>();
            for (int x = edge - 2; x <= edge + 4; x++) {
                for (int zz = z - 2; zz <= z + 2; zz++) {
                    int blockX = x;
                    int blockZ = zz;
                    preparation.add(world.getChunkAtAsync(Math.floorDiv(x, 16), Math.floorDiv(zz, 16), true)
                            .thenCompose(chunk -> scheduler.callForLocation(new Location(world, blockX, y, blockZ), () -> {
                                for (int height = y; height <= y + 4; height++) world.getBlockAt(blockX, height, blockZ).setType(Material.AIR);
                                world.getBlockAt(blockX, y - 1, blockZ).setType(Material.STONE);
                                return (Void) null;
                            })));
                }
            }
            return CompletableFuture.allOf(preparation.toArray(CompletableFuture[]::new)).thenCompose(ignored -> {
                Location start = new Location(world, edge + 0.5, y, z + 0.5);
                Location destination = new Location(world, edge + 2.5, y, z + 0.5);
                Npc npc = service.builder().name("NavigationNpc").location(start).spawn();
                AtomicInteger predicateCalls = new AtomicInteger();
                npc.showTo(player.getUniqueId()).visibleWhen(viewer -> {
                    if (!Bukkit.getServer().isOwnedByCurrentRegion(viewer)) throw new IllegalStateException("Predicate ran outside viewer ownership");
                    predicateCalls.incrementAndGet();
                    return true;
                });
                npc.resetVisibility(player.getUniqueId()).viewDistance(512).visibilityHysteresis(2);
                npc.batch(target -> target.nametag(List.of("path", "region"))
                        .nametagLayout(new NametagLayout(0.3, 0.25, true)).glowing(true)
                        .equipment(EquipmentSlot.HAND, new ItemStack(Material.DIAMOND)));
                npc.appearanceFor(player.getUniqueId(), ViewerAppearance.builder()
                        .appearance(new NpcAppearance(true, false, true, 1.2,
                                net.kyori.adventure.text.format.NamedTextColor.AQUA, true, false)).build());
                npc.batch(target -> target.appearance(new NpcAppearance(false, false, true, 1.0, null, true, false))
                        .nametag(List.of("updated path", "region")));
                NavigationOptions options = NavigationOptions.builder().radius(8).maxNodes(2000).build();
                var movement = npc.navigateTo(destination, 8, options);
                var completed = movement.route().thenCompose(setup -> {
                    require(setup.status() == MovementResult.Status.ROUTE_FOUND, "Navigation setup: " + setup.status() + " " + setup.failure());
                    return movement.result();
                }).thenCompose(arrival -> {
                    require(arrival.status() == MovementResult.Status.ARRIVED, "Navigation arrival: " + arrival.status());
                    require(Math.abs(npc.x() - destination.getX()) < 1e-6, "Navigation ended at wrong position");
                    npc.clearAppearanceFor(player.getUniqueId());
                    npc.equipment(EquipmentSlot.HAND, null);
                    return npc.patrol(List.of(start, destination), new PatrolOptions(8, 2, false, options)).result();
                }).thenCompose(patrol -> {
                    require(patrol.status() == MovementResult.Status.ARRIVED, "Patrol: " + patrol.status());
                    require(predicateCalls.get() > 0, "Visibility predicate was never evaluated");
                    var pending = npc.navigateTo(start, 8, options);
                    npc.teleport(destination);
                    require(pending.result().getNow(MovementResult.of(MovementResult.Status.ROUTE_FOUND)).status() == MovementResult.Status.SUPERSEDED, "Teleport retained pending navigation");
                    npc.teleport(origin.clone().add(1, 0, 0));
                    var follow = npc.follow(player.getUniqueId(), net.folianpc.api.FollowOptions.defaults());
                    CompletableFuture<String> held = new CompletableFuture<>();
                    var waiting = scheduler.scheduleGlobalLater(() -> {
                        try {
                            require(!follow.result().isDone(), "Follow stopped while inside its holding distance");
                            require(!npc.moving(), "Follow moved while inside its holding distance");
                            follow.cancel();
                            require(follow.result().getNow(MovementResult.of(MovementResult.Status.ROUTE_FOUND)).status()
                                    == MovementResult.Status.CANCELLED, "Follow cancellation was not retained");
                            npc.type(org.bukkit.entity.EntityType.POLAR_BEAR).teleport(start);
                            npc.navigateTo(destination, 8, options).result().whenComplete((bear, failure) -> {
                                if (failure != null) held.completeExceptionally(failure);
                                else if (bear.status() != MovementResult.Status.ARRIVED) {
                                    held.completeExceptionally(new IllegalStateException("Registered mob clearance: " + bear.status()));
                                } else {
                                    npc.remove();
                                    held.complete("NPC snapshot navigation across chunk boundaries, registered mob clearance, patrol, follow, viewer ownership and appearance overrides passed");
                                }
                            });
                        } catch (RuntimeException failure) { held.completeExceptionally(failure); }
                    }, 8);
                    if (waiting.isCancelled()) held.completeExceptionally(new IllegalStateException("Follow check dispatch refused"));
                    return held;
                });
                completed.whenComplete((result, failure) -> { if (!npc.removed()) npc.remove(); });
                return completed;
            });
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
