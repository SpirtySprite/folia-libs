package net.folianpc.internal.pathfinding;

import net.folianpc.api.NavigationOptions;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedNavigationTest {
    private NavigationOptions options() { return NavigationOptions.builder().radius(8).stepHeight(0).maxDrop(0).build(); }
    private AStar.Node at(int x, int z) { return new AStar.Node(x, 1, z); }

    @Test void gapJumpsAreOptInAndRequireAFreeArc() {
        AStar.WorldSampler gap = (x, y, z) -> y == 0 && z == 0 && x != 1;
        assertTrue(AStar.find(gap, at(0, 0), at(2, 0), options(), 0.6, 1.8).isEmpty());
        var jumps = options().toBuilder().maxJumpGap(1).build();
        assertEquals(List.of(at(0, 0), at(2, 0)), AStar.find(gap, at(0, 0), at(2, 0), jumps, 0.6, 1.8));
        AStar.WorldSampler ceiling = (x, y, z) -> gap.solid(x, y, z) || y == 3;
        assertTrue(AStar.find(ceiling, at(0, 0), at(2, 0), jumps, 0.6, 1.8).isEmpty());
    }

    @Test void terrainPenaltySelectsALongerCheapRouteWithoutJumpingOverFlatGround() {
        AStar.WorldSampler terrain = new AStar.WorldSampler() {
            @Override public boolean solid(int x, int y, int z) { return y == 0; }
            @Override public double penalty(int x, int y, int z) { return z == 0 && x > 0 && x < 4 ? 20 : 0; }
        };
        var route = AStar.find(terrain, at(0, 0), at(4, 0), options().toBuilder().maxJumpGap(3).build(), 0.6, 1.8);
        assertFalse(route.isEmpty());
        assertTrue(route.stream().noneMatch(n -> n.z() == 0 && n.x() > 0 && n.x() < 4));
        for (int i = 1; i < route.size(); i++) assertTrue(Math.abs(route.get(i).x() - route.get(i - 1).x()) <= 1);
    }

    @Test void approachUsesTheActualFloatingTargetAndNeverEntersItsBlock() {
        AStar.WorldSampler world = (x, y, z) -> y == 0 || x == 3 && z == 0 && (y == 1 || y == 2);
        RoutePlanner planner = new RoutePlanner();
        assertTrue(planner.route(world, 0.5, 1, 0.5, 3.5, 1.5, 0.5, options(), 0.6, 1.8).isEmpty());
        var route = planner.route(world, 0.5, 1, 0.5, 3.5, 1.5, 0.5,
                options().toBuilder().arrivalRadius(1.5).build(), 0.6, 1.8);
        assertFalse(route.isEmpty());
        var last = route.getLast();
        assertTrue(Math.pow(last[0] - 3.5, 2) + Math.pow(last[1] - 1.5, 2) + Math.pow(last[2] - 0.5, 2) <= 2.25);
        assertTrue(last[0] != 3.5 || last[2] != 0.5);
    }

    @Test void cancellationStopsSearchBeforeExploringTheWholeBudget() {
        AtomicInteger checks = new AtomicInteger();
        var route = AStar.find((x, y, z) -> y == 0, at(0, 0), at(7, 7), options(), 0.6, 1.8,
                () -> checks.incrementAndGet() > 2);
        assertTrue(route.isEmpty());
        assertEquals(3, checks.get());
    }

    @Test void optionsRetainLegacyDefaultsAndCopyEveryNewSetting() {
        var old = new NavigationOptions(100, 8, 1, 3, NavigationOptions.TerrainPolicy.AVOID_HAZARDS, 0.6, 1.8, true);
        assertEquals(0, old.maxJumpGap());
        assertEquals(0, old.arrivalRadius());
        assertTrue(old.terrainCosts().isEmpty());
        var custom = old.toBuilder().maxJumpGap(2).arrivalRadius(1.5).terrainCosts(Map.of(Material.SOUL_SAND, 5.0)).build();
        assertEquals(custom, custom.toBuilder().build());
        assertThrows(IllegalArgumentException.class, () -> old.toBuilder().maxJumpGap(4).build());
        assertThrows(IllegalArgumentException.class, () -> old.toBuilder().arrivalRadius(Double.NaN).build());
        assertThrows(IllegalArgumentException.class, () -> old.toBuilder().terrainCosts(Map.of(Material.STONE, -1.0)).build());
    }
}
