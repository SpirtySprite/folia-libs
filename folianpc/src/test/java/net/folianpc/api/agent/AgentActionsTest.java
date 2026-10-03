package net.folianpc.api.agent;

import net.foliacommons.scheduler.DeterministicScheduler;
import net.foliacommons.scheduler.Scheduler;
import net.folianpc.api.Npc;
import net.folianpc.api.NpcPosition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentActionsTest {
    private static final class Fixture implements AutoCloseable {
        final DeterministicScheduler scheduler = Scheduler.deterministic();
        final World world = mock(World.class);
        final Block block = mock(Block.class);
        final NpcAgent agent;
        final AgentContext context;
        final Location target = new Location(world, 1, 64, 0);
        Fixture() {
            when(world.getName()).thenReturn("world");
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
            when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(block);
            Npc npc = mock(Npc.class);
            when(npc.positionSnapshot()).thenReturn(new NpcPosition("world", 0.5, 64, 0.5));
            agent = new NpcAgent(npc, new AgentInventory(2), scheduler, Runnable::run);
            AtomicReference<AgentContext> scope = new AtomicReference<>();
            agent.operator(new AgentOperator("capture", Map.of(), Map.of("goal", 1L), 1, c -> { scope.set(c); return new CompletableFuture<>(); }));
            agent.pursue(new AgentGoal(Map.of("goal", 1L)));
            context = scope.get();
        }
        @Override public void close() { agent.close(); scheduler.close(); }
    }

    @Test void recipesConsumePlainInputsAndNeverDestroyCustomItems() {
        try (var fixture = new Fixture()) {
            fixture.agent.inventory().add(List.of(AgentInventoryTest.stack(Material.OAK_LOG, 1), AgentInventoryTest.stack(Material.OAK_LOG, 1, true)));
            var recipe = new AgentRecipe("planks", Map.of(Material.OAK_LOG, 1), AgentInventoryTest.stack(Material.OAK_PLANKS, 4), 1);
            var execution = AgentActions.craft(recipe).execute(fixture.context).toCompletableFuture();
            assertFalse(execution.isDone());
            fixture.scheduler.advanceTicks(1);
            assertTrue(execution.join());
            assertEquals(4L, fixture.agent.inventory().resources().get("OAK_PLANKS"));
            assertEquals(1L, fixture.agent.inventory().resources().get("OAK_LOG"));
            assertFalse(AgentActions.craft(recipe).execute(fixture.context).toCompletableFuture().isDone());
            fixture.scheduler.advanceTicks(1);
            assertEquals(1L, fixture.agent.inventory().resources().get("OAK_LOG"));
            assertTrue(recipe.operator(1, AgentActions.craft(recipe)).requires().containsKey("plain:OAK_LOG"));
        }
    }

    @Test void authorizationCannotReplaceThePickupStackAndStillCreditTheOldItems() {
        try (var fixture = new Fixture()) {
            Item item = mock(Item.class);
            when(item.isValid()).thenReturn(true);
            when(item.getLocation()).thenReturn(fixture.target);
            var initial = AgentInventoryTest.stack(Material.DIAMOND, 1);
            var replacement = AgentInventoryTest.stack(Material.DIRT, 1);
            when(item.getItemStack()).thenReturn(initial);
            fixture.agent.permission(interaction -> { when(item.getItemStack()).thenReturn(replacement); return true; });
            var operation = AgentActions.pickup(item).execute(fixture.context).toCompletableFuture();
            fixture.scheduler.advanceTicks(1);
            assertFalse(operation.join());
            verify(item, never()).remove();
            assertTrue(fixture.agent.inventory().resources().isEmpty());
        }
    }

    @Test void stationChangesInsideAuthorizationDoNotConsumeIngredients() {
        try (var fixture = new Fixture()) {
            fixture.agent.inventory().add(List.of(AgentInventoryTest.stack(Material.OAK_LOG, 1)));
            when(fixture.block.getType()).thenReturn(Material.CRAFTING_TABLE);
            fixture.agent.permission(interaction -> { when(fixture.block.getType()).thenReturn(Material.AIR); return true; });
            var recipe = new AgentRecipe("planks", Map.of(Material.OAK_LOG, 1), AgentInventoryTest.stack(Material.OAK_PLANKS, 4), 0);
            var operation = AgentActions.craftAt(fixture.target, recipe).execute(fixture.context).toCompletableFuture();
            fixture.scheduler.advanceTicks(2);
            assertFalse(operation.join());
            assertEquals(1L, fixture.agent.inventory().resources().get("OAK_LOG"));
        }
    }

    @Test void reachableFloatingItemsDoNotRequireAStandingPointAtItemHeight() {
        try (var fixture = new Fixture()) {
            Item item = mock(Item.class);
            when(item.isValid()).thenReturn(true);
            when(item.getLocation()).thenReturn(new Location(fixture.world, 1, 66, 0));
            var dropped = AgentInventoryTest.stack(Material.RAW_IRON, 1);
            when(item.getItemStack()).thenReturn(dropped);
            fixture.agent.permission(interaction -> true);
            var operation = AgentActions.approachItem(item).execute(fixture.context).toCompletableFuture();
            fixture.scheduler.advanceTicks(2);
            assertTrue(operation.join());
            verify(item).remove();
            assertEquals(1L, fixture.agent.inventory().resources().get("RAW_IRON"));
        }
    }

    @Test void pickupCommitsOnlyAfterOwnerDispatchAndCapacityAuthorization() {
        try (var fixture = new Fixture()) {
            Item item = mock(Item.class);
            when(item.isValid()).thenReturn(true);
            when(item.getLocation()).thenReturn(fixture.target);
            var dropped = AgentInventoryTest.stack(Material.DIAMOND, 2);
            when(item.getItemStack()).thenReturn(dropped);
            var denied = AgentActions.pickup(item).execute(fixture.context).toCompletableFuture();
            verify(item, never()).getItemStack();
            fixture.scheduler.advanceTicks(1);
            assertFalse(denied.join());
            verify(item, never()).remove();
            fixture.agent.permission(interaction -> interaction.kind() == AgentInteraction.Kind.PICKUP);
            var accepted = AgentActions.pickup(item).execute(fixture.context).toCompletableFuture();
            fixture.scheduler.advanceTicks(1);
            assertTrue(accepted.join());
            verify(item).remove();
            assertEquals(2L, fixture.agent.inventory().resources().get("DIAMOND"));
        }
    }

    @Test void miningBuildersDoNotReadLiveBlocksAndCancellationRejectsQueuedMutations() {
        try (var fixture = new Fixture()) {
            when(fixture.block.getType()).thenReturn(Material.STONE);
            var mine = AgentActions.mine(fixture.target, Material.STONE, AgentInventoryTest.stack(Material.AIR, 1), 2);
            verify(fixture.world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
            var denied = mine.execute(fixture.context).toCompletableFuture();
            fixture.scheduler.advanceTicks(3);
            assertFalse(denied.join());
            verify(fixture.block, never()).setType(Material.AIR);
            fixture.agent.permission(interaction -> true);
            var pending = mine.execute(fixture.context).toCompletableFuture();
            fixture.agent.close();
            fixture.scheduler.advanceTicks(4);
            assertTrue(pending.isCompletedExceptionally());
            verify(fixture.block, never()).setType(Material.AIR);
        }
    }

    @Test void stationRemovalDuringSmeltingPreservesAllInputs() {
        try (var fixture = new Fixture()) {
            fixture.agent.permission(interaction -> true);
            fixture.agent.inventory().add(List.of(AgentInventoryTest.stack(Material.RAW_IRON, 1), AgentInventoryTest.stack(Material.COAL, 1)));
            when(fixture.block.getType()).thenReturn(Material.FURNACE);
            var recipe = new AgentRecipe("iron", Map.of(Material.RAW_IRON, 1, Material.COAL, 1), AgentInventoryTest.stack(Material.IRON_INGOT, 1), 2);
            var operation = AgentActions.smelt(fixture.target, Material.FURNACE, recipe).execute(fixture.context).toCompletableFuture();
            fixture.scheduler.advanceTicks(1);
            when(fixture.block.getType()).thenReturn(Material.AIR);
            fixture.scheduler.advanceTicks(4);
            assertFalse(operation.join());
            assertEquals(1L, fixture.agent.inventory().resources().get("RAW_IRON"));
            assertEquals(1L, fixture.agent.inventory().resources().get("COAL"));
            assertFalse(fixture.agent.inventory().resources().containsKey("IRON_INGOT"));
        }
    }

    @Test void interactionsAndRecipesCopyMutableInputsAndRejectInvalidArguments() {
        try (var fixture = new Fixture()) {
            var interaction = new AgentInteraction(AgentInteraction.Kind.MINE, fixture.target, Material.STONE);
            fixture.target.setX(20);
            assertEquals(1, interaction.location().getX());
            interaction.location().setX(30);
            assertEquals(1, interaction.location().getX());
            assertThrows(IllegalArgumentException.class, () -> AgentActions.smelt(fixture.target, Material.STONE,
                    new AgentRecipe("recipe", Map.of(Material.STONE, 1), AgentInventoryTest.stack(Material.DIAMOND, 1), 0)));
            assertThrows(IllegalArgumentException.class, () -> AgentActions.mine(fixture.target, Material.AIR, AgentInventoryTest.stack(Material.AIR, 1), 1));
            assertThrows(IllegalArgumentException.class, () -> AgentActions.move(new Location(fixture.world, Double.NaN, 64, 0)));
        }
    }
}
