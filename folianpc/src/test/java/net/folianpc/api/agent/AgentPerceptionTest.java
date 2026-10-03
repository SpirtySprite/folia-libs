package net.folianpc.api.agent;

import net.foliacommons.scheduler.Scheduler;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentPerceptionTest {
    @Test void capturesLoadedChunksBeforeScanningNearestImmutableObservations() {
        try (var scheduler = Scheduler.deterministic()) {
            World world = mock(World.class);
            UUID id = UUID.randomUUID();
            when(world.getUID()).thenReturn(id);
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            when(world.isChunkLoaded(0, 0)).thenReturn(true);
            Chunk chunk = mock(Chunk.class);
            ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
            when(world.getChunkAt(0, 0)).thenReturn(chunk);
            when(chunk.getChunkSnapshot(false, false, false)).thenReturn(snapshot);
            when(snapshot.getBlockType(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
                int x = call.getArgument(0), y = call.getArgument(1), z = call.getArgument(2);
                return y == 64 && z == 8 && (x == 7 || x == 9 || x == 10) ? Material.IRON_ORE : Material.AIR;
            });
            Location center = new Location(world, 8.5, 64.5, 8.5);
            var result = AgentPerception.blocks(scheduler, center, 2, 1, Set.of(Material.IRON_ORE), 2);
            center.setX(30);
            verify(world, never()).getChunkAt(anyInt(), anyInt());
            assertFalse(result.isDone());
            scheduler.advanceTicks(2);
            assertEquals(2, result.join().size());
            assertEquals(7, result.join().getFirst().x());
            assertEquals(9, result.join().getLast().x());
            assertEquals(id, result.join().getFirst().world());
            assertEquals(7.5, result.join().getFirst().center(world).getX());
            World another = mock(World.class);
            when(another.getUID()).thenReturn(UUID.randomUUID());
            assertThrows(IllegalArgumentException.class, () -> result.join().getFirst().center(another));
            assertEquals(0, scheduler.pendingTasks());
        }
    }

    @Test void unloadedAndCancelledCapturesNeverLoadWorldChunks() {
        try (var scheduler = Scheduler.deterministic()) {
            World world = mock(World.class);
            Location center = new Location(world, 8, 64, 8);
            var empty = AgentPerception.blocks(scheduler, center, 1, 1, Set.of(Material.STONE), 1);
            scheduler.advanceTicks(2);
            assertTrue(empty.join().isEmpty());
            var cancelled = AgentPerception.blocks(scheduler, center, 1, 1, Set.of(Material.STONE), 1);
            cancelled.cancel(false);
            scheduler.advanceTicks(2);
            verify(world, never()).getChunkAt(anyInt(), anyInt());
            assertEquals(0, scheduler.pendingTasks());
            assertThrows(IllegalArgumentException.class, () -> AgentPerception.blocks(scheduler, center, 65, 1, Set.of(Material.STONE), 1));
        }
    }
}
