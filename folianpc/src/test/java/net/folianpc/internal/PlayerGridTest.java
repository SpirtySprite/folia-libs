package net.folianpc.internal;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerGridTest {

    private static PlayerTracker.Tracked at(double x, double z) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        return new PlayerTracker.Tracked(p, "world", x, 64, z);
    }

    private static Set<UUID> ids(List<PlayerTracker.Tracked> list) {
        Set<UUID> ids = new HashSet<>();
        for (PlayerTracker.Tracked t : list) {
            ids.add(t.uuid());
        }
        return ids;
    }

    @Test
    void emptyGridFindsNobody() {
        List<PlayerTracker.Tracked> out = new ArrayList<>();
        out.addAll(new PlayerGrid().near(0, 0, 48, new ArrayList<>()));
        assertTrue(out.isEmpty());
    }

    @Test
    void neverMissesAPlayerWithinRadiusAndSkipsFarOnes() {
        Random random = new Random(42);
        PlayerGrid grid = new PlayerGrid();
        List<PlayerTracker.Tracked> players = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            // Negative and positive coordinates, to cover cells on both sides of zero.
            PlayerTracker.Tracked t = at((random.nextDouble() - 0.5) * 2000, (random.nextDouble() - 0.5) * 2000);
            players.add(t);
            grid.add(t);
        }
        for (int q = 0; q < 300; q++) {
            double x = (random.nextDouble() - 0.5) * 2000;
            double z = (random.nextDouble() - 0.5) * 2000;
            double radius = 1 + random.nextDouble() * 120;
            List<PlayerTracker.Tracked> found = new ArrayList<>();
            found.addAll(grid.near(x, z, radius, new ArrayList<>()));
            Set<UUID> foundIds = ids(found);
            assertEquals(found.size(), foundIds.size(), "a player must be returned at most once");
            for (PlayerTracker.Tracked t : players) {
                double dx = t.x() - x;
                double dz = t.z() - z;
                if (dx * dx + dz * dz <= radius * radius) {
                    assertTrue(foundIds.contains(t.uuid()), "missed a player at distance "
                            + Math.sqrt(dx * dx + dz * dz) + " of radius " + radius);
                }
            }
            assertTrue(found.size() < players.size(), "a 120 block query over 2000 blocks should not return everybody");
        }
    }

    @Test
    void playersOnCellBoundariesAreFound() {
        PlayerGrid grid = new PlayerGrid();
        PlayerTracker.Tracked onEdge = at(PlayerGrid.CELL, PlayerGrid.CELL);
        PlayerTracker.Tracked justBefore = at(-0.0001, -0.0001);
        grid.add(onEdge);
        grid.add(justBefore);
        List<PlayerTracker.Tracked> out = new ArrayList<>();
        out.addAll(grid.near(PlayerGrid.CELL - 1, PlayerGrid.CELL - 1, 2, new ArrayList<>()));
        assertTrue(ids(out).contains(onEdge.uuid()));
        out.clear();
        out.addAll(grid.near(0.5, 0.5, 2, new ArrayList<>()));
        assertTrue(ids(out).contains(justBefore.uuid()));
    }

    @Test
    void aSmallWorldReturnsEverybodyAndALargeOneFilters() {
        PlayerGrid small = new PlayerGrid();
        for (int i = 0; i < PlayerGrid.LINEAR_LIMIT; i++) {
            small.add(at(i * 500.0, 0));
        }
        List<PlayerTracker.Tracked> out = new ArrayList<>();
        out.addAll(small.near(0, 0, 10, new ArrayList<>()));
        assertEquals(PlayerGrid.LINEAR_LIMIT, out.size());

        PlayerGrid large = new PlayerGrid();
        for (int i = 0; i < PlayerGrid.LINEAR_LIMIT + 1; i++) {
            large.add(at(i * 500.0, 0));
        }
        out.clear();
        out.addAll(large.near(0, 0, 10, new ArrayList<>()));
        assertEquals(1, out.size());
    }

    @Test
    void hugeOrInvalidRadiiReturnEveryoneInsteadOfLooping() {
        PlayerGrid grid = new PlayerGrid();
        int count = PlayerGrid.LINEAR_LIMIT * 2;
        for (int i = 0; i < count; i++) {
            grid.add(at(i * 1000.0, -i * 1000.0));
        }
        for (double radius : new double[] {1.0e7, 1.0e12, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NaN}) {
            List<PlayerTracker.Tracked> out = new ArrayList<>();
            out.addAll(grid.near(0, 0, radius, new ArrayList<>()));
            assertEquals(count, out.size(), "radius " + radius);
        }
    }
}
