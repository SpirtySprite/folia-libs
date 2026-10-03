package net.folianpc.internal.pathfinding;

import net.foliacommons.scheduler.Scheduler;
import net.folianpc.api.NavigationOptions;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TerrainCaptureTest {
    @Test
    void copiedTerrainCostsCountFootAndSupportMaterials() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        Chunk chunk = mock(Chunk.class);
        ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false))).thenReturn(CompletableFuture.completedFuture(chunk));
        when(chunk.getChunkSnapshot(false, false, false)).thenReturn(snapshot);
        when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenAnswer(call -> (int) call.getArgument(1) == 0 ? org.bukkit.Material.SOUL_SAND : org.bukkit.Material.AIR);
        Scheduler scheduler = Scheduler.synchronous();
        var costs = new java.util.HashMap<org.bukkit.Material, Double>();
        costs.put(org.bukkit.Material.SOUL_SAND, 4.0);
        costs.put(org.bukkit.Material.AIR, 2.0);
        var sample = new TerrainCapture(scheduler).capture(world, 0.5, 0.5, 1, NavigationOptions.TerrainPolicy.SOLID_GROUND, costs).join();
        costs.clear();
        assertEquals(6.0, sample.penalty(0, 1, 0));
        assertEquals(0.0, sample.penalty(1000, 1, 1000));
    }

    @Test
    void eachChunkSnapshotIsDispatchedAgainstItsOwnLocation() {
        World world = mock(World.class);
        Scheduler scheduler = mock(Scheduler.class);
        AtomicInteger calls = new AtomicInteger();
        ThreadLocal<Location> owner = new ThreadLocal<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false))).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int z = invocation.getArgument(1);
            Chunk chunk = mock(Chunk.class);
            when(chunk.getChunkSnapshot(false, false, false)).thenAnswer(capture -> {
                assertEquals(x, Math.floorDiv(owner.get().getBlockX(), 16));
                assertEquals(z, Math.floorDiv(owner.get().getBlockZ(), 16));
                calls.incrementAndGet();
                return mock(ChunkSnapshot.class);
            });
            return CompletableFuture.completedFuture(chunk);
        });
        when(scheduler.callForLocation(any(Location.class), any())).thenAnswer(invocation -> {
            owner.set(invocation.getArgument(0));
            java.util.function.Supplier<?> supplier = invocation.getArgument(1);
            try { return CompletableFuture.completedFuture(supplier.get()); }
            finally { owner.remove(); }
        });
        new TerrainCapture(scheduler).capture(world, 15.5, 0.5, 1, NavigationOptions.TerrainPolicy.AVOID_HAZARDS).join();
        assertEquals(4, calls.get());
    }

    @Test
    void refusedRegionDispatchCompletesCaptureExceptionally() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false))).thenReturn(CompletableFuture.completedFuture(chunk));
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.callForLocation(any(Location.class), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Dispatch refused")));
        var capture = new TerrainCapture(scheduler).capture(world, 0.5, 0.5, 1, NavigationOptions.TerrainPolicy.SOLID_GROUND);
        assertThrows(java.util.concurrent.CompletionException.class, capture::join);
    }
}
