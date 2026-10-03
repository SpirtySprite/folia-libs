package net.folianpc.api.agent;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentInventoryTest {
    static ItemStack stack(Material type, int amount) { return stack(type, amount, false); }
    static ItemStack stack(Material type, int amount, boolean metadata) {
        ItemStack item = mock(ItemStack.class);
        AtomicInteger count = new AtomicInteger(amount);
        when(item.getType()).thenAnswer(invocation -> count.get() == 0 ? Material.AIR : type);
        when(item.getAmount()).thenAnswer(invocation -> count.get());
        when(item.getMaxStackSize()).thenReturn(64);
        when(item.hasItemMeta()).thenReturn(metadata);
        doAnswer(invocation -> { count.set(invocation.getArgument(0)); return null; }).when(item).setAmount(anyInt());
        when(item.clone()).thenAnswer(invocation -> stack(type, count.get(), metadata));
        when(item.isSimilar(any())).thenAnswer(invocation -> {
            ItemStack other = invocation.getArgument(0);
            return other != null && other.getType() == type && other.hasItemMeta() == metadata;
        });
        return item;
    }

    @Test void copiesStacksAndCombinesThemWithinTheirStackLimit() {
        var inventory = new AgentInventory(2);
        ItemStack input = stack(Material.STONE, 65);
        assertTrue(inventory.add(List.of(input)));
        input.setAmount(1);
        assertEquals(65L, inventory.resources().get("STONE"));
        assertEquals(List.of(64, 1), inventory.contents().stream().map(ItemStack::getAmount).toList());
        inventory.contents().getFirst().setAmount(1);
        assertEquals(65L, inventory.resources().get("STONE"));
    }

    @Test void fullInventoryNeverPartiallyConsumesOrAddsItems() {
        var inventory = new AgentInventory(1);
        inventory.add(List.of(stack(Material.STONE, 64)));
        assertFalse(inventory.add(List.of(stack(Material.DIRT, 1))));
        assertFalse(inventory.exchange(Map.of(Material.STONE, 1), List.of(stack(Material.DIRT, 1))));
        assertEquals(64L, inventory.resources().get("STONE"));
        assertTrue(inventory.exchange(Map.of(Material.STONE, 64), List.of(stack(Material.DIRT, 1))));
        assertEquals(1L, inventory.resources().get("DIRT"));
        assertFalse(inventory.resources().containsKey("STONE"));
    }

    @Test void missingIngredientsAndRejectedWorldEffectsNeverCommit() {
        var inventory = new AgentInventory(2);
        inventory.add(List.of(stack(Material.STONE, 4)));
        assertFalse(inventory.exchange(Map.of(Material.STONE, 5), List.of(stack(Material.DIRT, 1))));
        AtomicInteger effects = new AtomicInteger();
        assertFalse(inventory.exchange(Map.of(Material.STONE, 4), List.of(stack(Material.DIRT, 1)), () -> {
            effects.incrementAndGet();
            return false;
        }));
        assertEquals(1, effects.get());
        assertEquals(4L, inventory.resources().get("STONE"));
    }

    @Test void customizedItemsArePreservedAndExcludedFromPlainRecipeInputs() {
        var inventory = new AgentInventory(2);
        inventory.add(List.of(stack(Material.STONE, 3, true)));
        assertEquals(3L, inventory.resources().get("STONE"));
        assertFalse(inventory.resources().containsKey(AgentInventory.plainKey(Material.STONE)));
        assertFalse(inventory.remove(Map.of(Material.STONE, 1)));
        assertTrue(inventory.exchangeExact(List.of(stack(Material.STONE, 1, true)), List.of(stack(Material.DIRT, 1))));
        assertEquals(2L, inventory.resources().get("STONE"));
        assertEquals(1L, inventory.resources().get("DIRT"));
    }

    @Test void recursiveMutationDuringAWorldEffectCannotOverwriteInventory() {
        var inventory = new AgentInventory(2);
        inventory.add(List.of(stack(Material.STONE, 1)));
        assertThrows(IllegalStateException.class, () -> inventory.exchange(Map.of(Material.STONE, 1), List.of(stack(Material.DIRT, 1)),
                () -> inventory.add(List.of(stack(Material.DIAMOND, 1)))));
        assertEquals(1L, inventory.resources().get("STONE"));
        assertFalse(inventory.resources().containsKey("DIAMOND"));
        assertTrue(inventory.remove(Map.of(Material.STONE, 1)));
    }

    @Test void aFullDestinationSuppressesWorldMutation() {
        var inventory = new AgentInventory(1);
        inventory.add(List.of(stack(Material.STONE, 64)));
        AtomicInteger commits = new AtomicInteger();
        assertFalse(inventory.exchangeExact(List.of(), List.of(stack(Material.DIRT, 1)), () -> { commits.incrementAndGet(); return true; }));
        assertEquals(0, commits.get());
    }
}
