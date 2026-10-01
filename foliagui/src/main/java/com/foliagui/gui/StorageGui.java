package com.foliagui.gui;

import com.foliagui.builder.gui.StorageGuiBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StorageGui extends BaseGui {
    private volatile ItemStack[] storageSnapshot;
    private final java.util.Set<Integer> controlledSlots = new java.util.HashSet<>();

    public StorageGui(int rows, @NotNull Component title) {
        super(rows, title);
        storageSnapshot = new ItemStack[getSize()];
        clearInteractionModifiers();
    }

    @Override
    protected void populateInventory() {
        var items = guiItemsSnapshot();
        for (int slot : controlledSlots) {
            if (!items.containsKey(slot)) {
                applyItem(slot, null);
            }
        }
        items.forEach(this::applyItem);
        controlledSlots.clear();
        controlledSlots.addAll(items.keySet());
        captureStorage();
    }

    /** Reads a deep copy of deposit contents on the owning thread without blocking the caller. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public @NotNull java.util.concurrent.CompletableFuture<ItemStack[]> storageContentsAsync() {
        return inventoryAsync(this::captureStorage);
    }

    public static @NotNull StorageGuiBuilder builder() {
        return new StorageGuiBuilder();
    }

    /**
     * Returns current contents on the owning thread, or the latest captured snapshot elsewhere.
     * @deprecated Use {@link #storageContentsAsync()} for fresh contents from any thread.
     */
    @Deprecated
    public @NotNull ItemStack[] getStorageContents() {
        var viewers = getViewerPlayers();
        if (viewers.isEmpty() || org.bukkit.Bukkit.getServer().isOwnedByCurrentRegion(viewers.getFirst())) {
            return captureStorage();
        }
        storageContentsAsync();
        return copy(storageSnapshot);
    }

    private ItemStack[] captureStorage() {
        ItemStack[] raw = getInventory().getContents();
        ItemStack[] storage = new ItemStack[raw.length];
        for (int slot = 0; slot < raw.length; slot++) {
            if (getGuiItem(slot) == null) {
                storage[slot] = raw[slot] == null ? null : raw[slot].clone();
            }
        }
        storageSnapshot = storage;
        return copy(storage);
    }

    private static ItemStack[] copy(ItemStack[] contents) {
        return java.util.Arrays.stream(contents).map(stack -> stack == null ? null : stack.clone()).toArray(ItemStack[]::new);
    }

    public @NotNull List<ItemStack> getStoredItems() {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : getStorageContents()) {
            if (stack != null && !stack.getType().isAir()) {
                items.add(stack);
            }
        }
        return items;
    }

    public @NotNull StorageGui setStorageContents(@Nullable ItemStack @NotNull [] contents) {
        ItemStack[] copy = copy(contents);
        applyToInventory(() -> {
            for (int slot = 0; slot < copy.length && slot < getSize(); slot++) {
                if (getGuiItem(slot) == null) {
                    getInventory().setItem(slot, copy[slot]);
                }
            }
            captureStorage();
        });
        return this;
    }
}
