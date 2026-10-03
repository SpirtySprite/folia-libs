package net.folianpc.internal.pathfinding;

import net.foliacommons.scheduler.Scheduler;
import net.folianpc.api.NavigationOptions;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class TerrainCapture {
    private final Scheduler scheduler;

    public TerrainCapture(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public CompletableFuture<AStar.WorldSampler> capture(World world, double x, double z, int radius,
                                                         NavigationOptions.TerrainPolicy policy) {
        return capture(world, x, z, radius, policy, Map.of());
    }

    public CompletableFuture<AStar.WorldSampler> capture(World world, double x, double z, int radius,
                                                         NavigationOptions.TerrainPolicy policy, Map<Material, Double> costs) {
        int minX = Math.floorDiv((int) Math.floor(x) - radius - 9, 16);
        int minZ = Math.floorDiv((int) Math.floor(z) - radius - 9, 16);
        int maxX = Math.floorDiv((int) Math.floor(x) + radius + 9, 16);
        int maxZ = Math.floorDiv((int) Math.floor(z) + radius + 9, 16);
        Capture capture = new Capture(world, minX, minZ, maxX - minX + 1, maxZ - minZ + 1, policy, Map.copyOf(costs));
        for (int worker = 0; worker < 4 && !capture.result.isDone(); worker++) {
            capture.next();
        }
        return capture.result;
    }

    private final class Capture {
        final World world;
        final int minX;
        final int minZ;
        final int width;
        final int count;
        final NavigationOptions.TerrainPolicy policy;
        final Map<Material, Double> costs;
        final Map<Long, ChunkSnapshot> snapshots = new ConcurrentHashMap<>();
        final AtomicInteger next = new AtomicInteger();
        final AtomicInteger remaining;
        final CompletableFuture<AStar.WorldSampler> result = new CompletableFuture<>();

        Capture(World world, int minX, int minZ, int width, int height, NavigationOptions.TerrainPolicy policy, Map<Material, Double> costs) {
            this.world = world;
            this.minX = minX;
            this.minZ = minZ;
            this.width = width;
            count = Math.multiplyExact(width, height);
            remaining = new AtomicInteger(count);
            this.policy = policy;
            this.costs = costs;
        }

        void next() {
            if (result.isDone()) {
                return;
            }
            int index = next.getAndIncrement();
            if (index >= count) {
                return;
            }
            int x = minX + index % width;
            int z = minZ + index / width;
            try {
                world.getChunkAtAsync(x, z, false).thenCompose(chunk -> chunk == null
                        ? CompletableFuture.<ChunkSnapshot>completedFuture(null)
                        : scheduler.callForLocation(new Location(world, x * 16.0 + 8, 0, z * 16.0 + 8),
                                () -> chunk.getChunkSnapshot(false, false, false))).whenComplete((snapshot, failure) -> {
                    if (failure != null) {
                        result.completeExceptionally(failure);
                        return;
                    }
                    if (snapshot != null) {
                        snapshots.put(key(x, z), snapshot);
                    }
                    if (remaining.decrementAndGet() == 0) {
                        result.complete(new Sample(Map.copyOf(snapshots), world.getMinHeight(), world.getMaxHeight(), policy, costs));
                    } else {
                        next();
                    }
                });
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        }
    }

    private record Sample(Map<Long, ChunkSnapshot> chunks, int minY, int maxY,
                          NavigationOptions.TerrainPolicy policy, Map<Material, Double> costs) implements AStar.WorldSampler {
        @Override public double penalty(int x, int y, int z) {
            if (costs.isEmpty()) return 0;
            Material ground = type(x, y - 1, z), foot = type(x, y, z);
            return (ground == null ? 0 : costs.getOrDefault(ground, 0.0)) + (foot == null ? 0 : costs.getOrDefault(foot, 0.0));
        }
        private Material type(int x, int y, int z) {
            ChunkSnapshot snapshot = chunks.get(key(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
            return snapshot == null || y < minY || y >= maxY ? null
                    : snapshot.getBlockType(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
        }

        @Override public boolean solid(int x, int y, int z) {
            Material material = type(x, y, z);
            return material == null || material.isSolid();
        }

        @Override public boolean passable(int x, int y, int z) {
            Material material = type(x, y, z);
            return material != null && !material.isSolid()
                    && (policy == NavigationOptions.TerrainPolicy.SOLID_GROUND || !hazard(material));
        }

        @Override public boolean ground(int x, int y, int z) {
            Material material = type(x, y, z);
            return material != null && material.isSolid()
                    && (policy == NavigationOptions.TerrainPolicy.SOLID_GROUND || !hazard(material));
        }
    }

    private static boolean hazard(Material type) {
        return switch (type) {
            case WATER, LAVA, FIRE, SOUL_FIRE, CACTUS, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE,
                    SWEET_BERRY_BUSH, WITHER_ROSE, POWDER_SNOW -> true;
            default -> false;
        };
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
