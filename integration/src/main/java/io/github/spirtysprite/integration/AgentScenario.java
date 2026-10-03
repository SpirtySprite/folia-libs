package io.github.spirtysprite.integration;

import net.foliacommons.scheduler.Scheduler;
import net.folianpc.api.FoliaNpc;
import net.folianpc.api.NavigationOptions;
import net.folianpc.api.Npc;
import net.folianpc.api.agent.AgentActions;
import net.folianpc.api.agent.AgentGoal;
import net.folianpc.api.agent.AgentInventory;
import net.folianpc.api.agent.AgentOperator;
import net.folianpc.api.agent.AgentOptions;
import net.folianpc.api.agent.AgentPerception;
import net.folianpc.api.agent.AgentRecipe;
import net.folianpc.api.agent.AgentResult;
import net.folianpc.api.agent.NpcAgent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class AgentScenario {
    private AgentScenario() { }

    static CompletableFuture<String> run(FoliaNpc service, Scheduler scheduler, Location origin) {
        World world = origin.getWorld();
        org.bukkit.plugin.Plugin plugin = java.util.Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("FoliaIntegration"));
        int edge = Math.floorDiv(origin.getBlockX(), 256) * 256 + 255;
        int y = origin.getBlockY(), z = origin.getBlockZ() + 8;
        List<CompletableFuture<Void>> prepared = new ArrayList<>();
        for (int x = edge - 2; x <= edge + 4; x++) {
            for (int zz = z - 2; zz <= z + 2; zz++) {
                int bx = x, bz = zz;
                prepared.add(world.getChunkAtAsync(bx >> 4, bz >> 4, true).thenCompose(chunk ->
                        scheduler.callForLocation(new Location(world, bx, y, bz), () -> {
                            require(Bukkit.getServer().isOwnedByCurrentRegion(world, bx >> 4, bz >> 4), "Fixture owner missing");
                            world.addPluginChunkTicket(bx >> 4, bz >> 4, plugin);
                            for (int height = y; height <= y + 5; height++) world.getBlockAt(bx, height, bz).setType(Material.AIR);
                            world.getBlockAt(bx, y - 1, bz).setType(Material.STONE);
                            return (Void) null;
                        })));
            }
        }
        Location ore = new Location(world, edge + 2, y, z);
        Location station = new Location(world, edge + 1, y, z + 1);
        return CompletableFuture.allOf(prepared.toArray(CompletableFuture[]::new)).thenCompose(ignored ->
                scheduler.callForLocation(ore, () -> {
                    ore.getBlock().setType(Material.STONE);
                    Item item = world.dropItem(ore.clone().add(0.5, 0.2, 1.5), new ItemStack(Material.RAW_IRON));
                    item.setGravity(false);
                    item.setVelocity(new org.bukkit.util.Vector());
                    item.setUnlimitedLifetime(true);
                    return item;
                })).thenCompose(item -> {
            Npc npc = service.builder().name("AutonomousNpc").location(new Location(world, edge + 0.5, y, z + 0.5)).spawn();
            NpcAgent agent = service.agent(npc);
            AtomicInteger permissions = new AtomicInteger();
            AtomicBoolean stationExists = new AtomicBoolean();
            agent.permission(interaction -> {
                Location target = interaction.location();
                require(Bukkit.getServer().isOwnedByCurrentRegion(world, target.getBlockX() >> 4, target.getBlockZ() >> 4), "World action used wrong region");
                permissions.incrementAndGet();
                return true;
            });
            agent.inventory().add(List.of(new ItemStack(Material.IRON_PICKAXE), new ItemStack(Material.COAL)));
            AgentRecipe furnace = new AgentRecipe("make-station", Map.of(Material.COBBLESTONE, 1), new ItemStack(Material.FURNACE), 1);
            AgentRecipe iron = new AgentRecipe("smelt-iron", Map.of(Material.RAW_IRON, 1, Material.COAL, 1), new ItemStack(Material.IRON_INGOT), 2);
            agent.operator(furnace.operator(2, AgentActions.craft(furnace)));
            agent.operator(new AgentOperator("place-station", Map.of(AgentInventory.plainKey(Material.FURNACE), 1L),
                    Map.of("FURNACE", -1L, AgentInventory.plainKey(Material.FURNACE), -1L, "station", 1L), 2,
                    AgentActions.approachBlock(station, AgentActions.place(station, Material.FURNACE))));
            AgentOperator smelt = iron.operator(2, AgentActions.approachBlock(station, AgentActions.smelt(station, Material.FURNACE, iron)));
            Map<String, Long> required = new HashMap<>(smelt.requires());
            required.put("station", 1L);
            agent.operator(new AgentOperator(smelt.name(), required, smelt.changes(), smelt.cost(), smelt.action()));
            agent.facts(() -> stationExists.get() ? Map.of("station", 1L) : Map.of());
            agent.perception(() -> scheduler.callForLocation(station, () -> {
                stationExists.set(station.getBlock().getType() == Material.FURNACE);
                return stationExists.get();
            }).thenCompose(ignored -> AgentPerception.blocks(scheduler, ore, 1, 0, Set.of(Material.STONE), 4))
                    .thenApply(blocks -> {
                        List<AgentOperator> actions = new ArrayList<>();
                        ItemStack tool = agent.inventory().contents().stream().filter(stack -> stack.getType() == Material.IRON_PICKAXE).findFirst().orElseThrow();
                        for (var block : blocks) actions.add(AgentActions.gather("mine-" + block.x() + "-" + block.z(), new ItemStack(Material.COBBLESTONE), 2,
                                AgentActions.approachBlock(block.center(world), AgentActions.mine(block.center(world), block.material(), tool, 1))));
                        actions.add(AgentActions.gather("pickup-iron", new ItemStack(Material.RAW_IRON), 2,
                                AgentActions.approachItem(item)));
                        return actions;
                    }));
            AgentOptions options = new AgentOptions(4000, 16, 16, 2, 600, 8, 3,
                    NavigationOptions.builder().radius(8).maxNodes(2000).groundFollowing(true).build());
            CompletableFuture<String> result = agent.pursue(AgentGoal.obtain(Material.IRON_INGOT, 1), options).result().thenCompose(outcome -> {
                require(outcome.status() == AgentResult.Status.COMPLETED, "Autonomy: " + outcome.status() + " " + outcome.failure() + " actions=" + outcome.executedActions() + " inventory=" + agent.inventory().resources());
                require(outcome.executedActions() == 5, "Expected mining, pickup, crafting, placement and smelting: " + outcome.executedActions());
                require(agent.inventory().resources().getOrDefault("IRON_INGOT", 0L) == 1, "Smelting result missing");
                require(agent.inventory().resources().getOrDefault("COAL", 0L) == 0, "Fuel was not consumed");
                ItemStack tool = agent.inventory().contents().stream().filter(stack -> stack.getType() == Material.IRON_PICKAXE).findFirst().orElseThrow();
                require(((Damageable) tool.getItemMeta()).getDamage() == 1, "Mining did not wear the owned tool");
                require(permissions.get() >= 5, "World authorization was bypassed");
                return scheduler.callForLocation(ore, () -> { require(ore.getBlock().getType() == Material.AIR, "Mined block survived"); return true; })
                        .thenCompose(ignored -> scheduler.callForLocation(station, () -> {
                            require(station.getBlock().getType() == Material.FURNACE, "Crafted station was not placed");
                            return true;
                        }));
            }).thenCompose(ignored -> {
                agent.permission(interaction -> false);
                Location denied = ore.clone().add(0, 0, 1);
                return scheduler.callForLocation(denied, () -> { denied.getBlock().setType(Material.OAK_LOG); return true; })
                        .thenCompose(ready -> {
                            agent.perception(() -> CompletableFuture.completedFuture(List.of(AgentActions.gather("denied-log", new ItemStack(Material.OAK_LOG), 1,
                                    AgentActions.mine(denied, Material.OAK_LOG, new ItemStack(Material.AIR), 1)))));
                            return agent.pursue(AgentGoal.obtain(Material.OAK_LOG, 1), options).result();
                        }).thenCompose(outcome -> {
                            require(outcome.status() == AgentResult.Status.UNREACHABLE, "Denied action was accepted");
                            return scheduler.callForLocation(denied, () -> {
                                require(denied.getBlock().getType() == Material.OAK_LOG, "Denied action changed the world");
                                return true;
                            }).thenCompose(checked -> cancelledMining(agent, scheduler, denied, options));
                        });
            });
            result.whenComplete((value, failure) -> {
                agent.close(); npc.remove();
                for (int cx = (edge - 2) >> 4; cx <= (edge + 4) >> 4; cx++) {
                    for (int cz = (z - 2) >> 4; cz <= (z + 2) >> 4; cz++) {
                        int chunkX = cx, chunkZ = cz;
                        scheduler.runForLocation(new Location(world, cx * 16.0, y, cz * 16.0), () -> world.removePluginChunkTicket(chunkX, chunkZ, plugin));
                    }
                }
            });
            return result;
        });
    }

    private static CompletableFuture<String> cancelledMining(NpcAgent agent, Scheduler scheduler, Location target, AgentOptions options) {
        CompletableFuture<Void> started = new CompletableFuture<>();
        agent.permission(interaction -> true);
        agent.perception(() -> CompletableFuture.completedFuture(List.of(AgentActions.gather("late-log", new ItemStack(Material.OAK_LOG), 1,
                context -> {
                    var operation = AgentActions.mine(target, Material.OAK_LOG, new ItemStack(Material.AIR), 20).execute(context);
                    started.complete(null);
                    return operation;
                }))));
        var task = agent.pursue(AgentGoal.obtain(Material.OAK_LOG, 1), options);
        return started.thenCompose(ignored -> {
            task.cancel();
            return task.result();
        }).thenCompose(outcome -> {
            require(outcome.status() == AgentResult.Status.CANCELLED, "Pending mining did not cancel");
            CompletableFuture<Void> later = new CompletableFuture<>();
            var timer = scheduler.scheduleGlobalLater(() -> later.complete(null), 24);
            if (timer.isCancelled()) later.completeExceptionally(new IllegalStateException("Cancellation check rejected"));
            return later;
        }).thenCompose(ignored -> scheduler.callForLocation(target, () -> {
            require(target.getBlock().getType() == Material.OAK_LOG, "Cancelled mining changed the world later");
            require(agent.inventory().resources().getOrDefault("OAK_LOG", 0L) == 0, "Cancelled mining credited drops");
            return "NPC autonomous mining, owned tool wear, pickup, crafting, station placement, smelting, perception, region authorization and delayed cancellation passed";
        }));
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
