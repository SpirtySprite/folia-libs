package net.folianpc.api.agent;

import net.foliacommons.scheduler.Scheduler;
import net.foliacommons.scheduler.TaskGroup;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.ApiStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded region-safe world observations for autonomous resource discovery. Never loads or generates chunks. */
@ApiStatus.Experimental
public final class AgentPerception {
    private record Captured(ChunkSnapshot chunk, UUID world, int minY, int maxY) { }
    private AgentPerception() { }

    /** Captures loaded chunks on their owners, then scans immutable snapshots asynchronously. Returns nearest matching blocks; cancellation closes outstanding capture work. */
    public static CompletableFuture<List<ObservedBlock>> blocks(Scheduler scheduler, Location center, int radius,
                                                                int verticalRadius, Set<Material> materials, int limit) {
        Objects.requireNonNull(scheduler, "scheduler");
        Location origin = Objects.requireNonNull(center, "center").clone();
        World world = Objects.requireNonNull(origin.getWorld(), "world");
        origin.checkFinite();
        Set<Material> accepted = Set.copyOf(materials);
        if (radius < 0 || radius > 64 || verticalRadius < 0 || verticalRadius > 64 || limit < 1 || limit > 1024
                || accepted.isEmpty() || accepted.size() > 64 || Math.abs(origin.getX()) > 30_000_000 || Math.abs(origin.getZ()) > 30_000_000 || Math.abs(origin.getY()) > 30_000_000)
            throw new IllegalArgumentException("Invalid perception bounds");
        CompletableFuture<List<ObservedBlock>> result = new CompletableFuture<>();
        TaskGroup work = new TaskGroup();
        int minCX = (origin.getBlockX() - radius) >> 4, maxCX = (origin.getBlockX() + radius) >> 4;
        int minCZ = (origin.getBlockZ() - radius) >> 4, maxCZ = (origin.getBlockZ() + radius) >> 4;
        int width = maxCX - minCX + 1, total = width * (maxCZ - minCZ + 1);
        AtomicInteger next = new AtomicInteger();
        AtomicInteger remaining = new AtomicInteger(total);
        ConcurrentLinkedQueue<ObservedBlock> observed = new ConcurrentLinkedQueue<>();
        Comparator<ObservedBlock> order = Comparator.comparingDouble((ObservedBlock block) -> distance(block, origin))
                .thenComparingInt(ObservedBlock::x).thenComparingInt(ObservedBlock::y).thenComparingInt(ObservedBlock::z);
        class Worker {
            void next() {
                if (result.isDone()) return;
                int index = next.getAndIncrement();
                if (index >= total) return;
                int cx = minCX + index % width, cz = minCZ + index / width;
                try {
                    CompletableFuture<Captured> capture = scheduler.callForLocation(new Location(world, cx * 16.0 + 8, 0, cz * 16.0 + 8), () -> {
                        if (result.isDone() || !world.isChunkLoaded(cx, cz)) return null;
                        return new Captured(world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false), world.getUID(), world.getMinHeight(), world.getMaxHeight());
                    });
                    work.add(capture);
                    capture.whenComplete((snapshot, failure) -> {
                        if (result.isDone()) return;
                        if (failure != null) result.completeExceptionally(failure);
                        else scan(snapshot, cx, cz);
                    });
                } catch (RuntimeException failure) { result.completeExceptionally(failure); }
            }

            void scan(Captured snapshot, int cx, int cz) {
                try {
                    var scanned = scheduler.callAsync(() -> snapshot == null || result.isDone() ? List.<ObservedBlock>of()
                            : AgentPerception.scan(snapshot, origin, radius, verticalRadius, accepted, limit, cx, cz, order, result));
                    work.add(scanned);
                    scanned.whenComplete((matches, failure) -> {
                        if (result.isDone()) return;
                        if (failure != null) { result.completeExceptionally(failure); return; }
                        observed.addAll(matches);
                        if (remaining.decrementAndGet() == 0) result.complete(observed.stream().sorted(order).limit(limit).toList());
                        else next();
                    });
                } catch (RuntimeException failure) { result.completeExceptionally(failure); }
            }
        }
        result.whenComplete((value, failure) -> work.close());
        Worker worker = new Worker();
        try { for (int i = 0; i < Math.min(4, total); i++) worker.next(); }
        catch (RuntimeException failure) { result.completeExceptionally(failure); }
        return result;
    }

    private static List<ObservedBlock> scan(Captured snapshot, Location origin, int radius, int verticalRadius,
                                           Set<Material> materials, int limit, int cx, int cz,
                                           Comparator<ObservedBlock> order, CompletableFuture<?> result) {
        java.util.PriorityQueue<ObservedBlock> found = new java.util.PriorityQueue<>(limit, order.reversed());
        int minX = Math.max(cx * 16, origin.getBlockX() - radius), maxX = Math.min(cx * 16 + 15, origin.getBlockX() + radius);
        int minZ = Math.max(cz * 16, origin.getBlockZ() - radius), maxZ = Math.min(cz * 16 + 15, origin.getBlockZ() + radius);
        int minY = Math.max(snapshot.minY(), origin.getBlockY() - verticalRadius), maxY = Math.min(snapshot.maxY() - 1, origin.getBlockY() + verticalRadius);
        for (int x = minX; x <= maxX && !result.isDone(); x++) {
            for (int z = minZ; z <= maxZ && !result.isDone(); z++) {
                for (int y = minY; y <= maxY && !result.isDone(); y++) {
                    Material type = snapshot.chunk().getBlockType(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
                    if (materials.contains(type)) {
                        ObservedBlock candidate = new ObservedBlock(snapshot.world(), x, y, z, type);
                        if (found.size() < limit) found.add(candidate);
                        else if (order.compare(candidate, found.peek()) < 0) { found.remove(); found.add(candidate); }
                    }
                }
            }
        }
        return found.stream().sorted(order).limit(limit).toList();
    }

    private static double distance(ObservedBlock block, Location origin) {
        double dx = block.x() + 0.5 - origin.getX(), dy = block.y() + 0.5 - origin.getY(), dz = block.z() + 0.5 - origin.getZ();
        return dx*dx + dy*dy + dz*dz;
    }
}
