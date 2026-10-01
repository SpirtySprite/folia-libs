package com.foliagui.gui;

import com.foliagui.internal.InventoryViews;
import com.foliagui.FoliaGUI;
import com.foliagui.FoliaGUIService;
import com.foliagui.item.GuiAction;
import com.foliagui.item.GuiItem;
import com.foliagui.util.Slot;
import com.foliagui.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BaseGui implements InventoryHolder {

    private volatile Component title;
    private final java.util.concurrent.atomic.AtomicReference<HumanEntity> activeViewer = new java.util.concurrent.atomic.AtomicReference<>();
    volatile GuiItem loadingItem;
    private final java.util.concurrent.atomic.AtomicLong contentRevision = new java.util.concurrent.atomic.AtomicLong();
    private final int size;
    private final GuiType guiType;
    private final int rows;

    private volatile Inventory inventory;
    private final Map<Integer, GuiItem> guiItems = new ConcurrentHashMap<>();
    private final GuiItem[] renderedItems;
    private final ItemStack[] renderedStacks;
    private final Map<Integer, GuiAction<InventoryClickEvent>> slotActions = new ConcurrentHashMap<>();
    private final Set<InteractionModifier> interactionModifiers =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private volatile GuiAction<InventoryClickEvent> defaultClickAction;
    private volatile GuiAction<InventoryClickEvent> defaultTopClickAction;
    private volatile GuiAction<InventoryClickEvent> playerInventoryAction;
    private volatile GuiAction<InventoryClickEvent> outsideClickAction;
    private volatile GuiAction<InventoryDragEvent> dragAction;
    private volatile GuiAction<InventoryOpenEvent> openAction;
    private volatile GuiAction<InventoryCloseEvent> closeAction;

    private volatile boolean updating;

    private volatile long updateIntervalTicks;
    private volatile java.util.function.Consumer<BaseGui> tickAction;
    private final Map<UUID, com.foliagui.scheduler.TaskHandle> updateTasks = new ConcurrentHashMap<>();

    private volatile FoliaGUIService service;

    private volatile GuiTheme resolvedTheme;
    private volatile boolean forceOpen;
    private final Set<UUID> allowedCloses = ConcurrentHashMap.newKeySet();

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(BaseGui.class.getName());

    {
        interactionModifiers.addAll(EnumSet.allOf(InteractionModifier.class));
    }

    protected BaseGui(int rows, @NotNull Component title) {
        this.rows = Math.max(1, Math.min(6, rows));
        this.guiType = null;
        this.size = this.rows * Slot.ROW_WIDTH;
        this.title = title;
        this.inventory = Bukkit.createInventory(this, size, title);
        this.renderedItems = new GuiItem[size];
        this.renderedStacks = new ItemStack[size];
    }

    protected BaseGui(@NotNull GuiType guiType, @NotNull Component title) {
        this.guiType = guiType;
        this.rows = 0;
        this.size = guiType.getSize();
        this.title = title;
        this.inventory = Bukkit.createInventory(this, guiType.getInventoryType(), title);
        this.renderedItems = new GuiItem[size];
        this.renderedStacks = new ItemStack[size];
    }

    /** The service this GUI belongs to: the one set with {@link #service(FoliaGUIService)}, else the default. */
    public @NotNull FoliaGUIService service() {
        FoliaGUIService bound = service;
        return bound != null ? bound : FoliaGUI.service();
    }

    /** True when {@code candidate} is the service this GUI is bound to, explicitly or by default. */
    @ApiStatus.Internal
    public boolean belongsTo(@NotNull FoliaGUIService candidate) {
        FoliaGUIService bound = service;
        if (bound != null) {
            return bound == candidate;
        }
        return FoliaGUI.isInitialised() && FoliaGUI.service() == candidate;
    }

    /** Binds this GUI to an explicit service. Call before the GUI is first opened. */
    public synchronized @NotNull BaseGui service(@NotNull FoliaGUIService service) {
        Objects.requireNonNull(service, "service cannot be null");
        if (activeViewer.get() != null && this.service != service) {
            throw new IllegalStateException("Cannot change service while the GUI is open");
        }
        this.service = service;
        return this;
    }

    protected void populateInventory() {
        Inventory target = getInventory();
        int slotCount = target.getSize();
        for (int slot = 0; slot < slotCount; slot++) {
            applyItem(slot, guiItems.get(slot));
        }
    }

    protected final void applyItem(int slot, @Nullable GuiItem item) {
        ItemStack stack = item == null ? null : item.getItemStack();
        if (renderedItems[slot] == item && Objects.equals(renderedStacks[slot], stack)) {
            return;
        }
        getInventory().setItem(slot, stack == null ? null : stack.clone());
        renderedItems[slot] = item;
        renderedStacks[slot] = stack == null ? null : stack.clone();
    }

    public @NotNull BaseGui setItem(int slot, @NotNull GuiItem guiItem) {
        Objects.requireNonNull(guiItem, "guiItem cannot be null");
        validateSlot(slot);
        guiItems.put(slot, guiItem);
        onLayoutChanged();
        return this;
    }

    public @NotNull BaseGui setItem(int row, int column, @NotNull GuiItem guiItem) {
        return setItem(Slot.of(row, column), guiItem);
    }

    public @NotNull BaseGui setItem(@NotNull List<Integer> slots, @NotNull GuiItem guiItem) {
        Objects.requireNonNull(slots, "slots cannot be null");
        Objects.requireNonNull(guiItem, "guiItem cannot be null");
        for (int slot : slots) {
            setItem(slot, guiItem);
        }
        return this;
    }

    public @NotNull BaseGui addItem(@NotNull GuiItem... items) {
        Objects.requireNonNull(items, "items cannot be null");
        int slot = 0;
        for (GuiItem item : items) {
            Objects.requireNonNull(item, "items cannot contain null");
            while (slot < size && guiItems.containsKey(slot)) {
                slot++;
            }
            if (slot >= size) {
                break;
            }
            guiItems.put(slot++, item);
        }
        onLayoutChanged();
        return this;
    }

    public @NotNull BaseGui removeItem(int slot) {
        guiItems.remove(slot);
        onLayoutChanged();
        return this;
    }

    public @NotNull BaseGui removeItem(int row, int column) {
        return removeItem(Slot.of(row, column));
    }

    public @NotNull BaseGui removeItem(@NotNull GuiItem guiItem) {
        Objects.requireNonNull(guiItem, "guiItem cannot be null");
        guiItems.values().removeIf(existing -> existing.equals(guiItem));
        onLayoutChanged();
        return this;
    }

    protected void onLayoutChanged() {
    }

    public @Nullable GuiItem getGuiItem(int slot) {
        return guiItems.get(slot);
    }

    public @Nullable GuiItem itemAt(int slot) {
        return guiItems.get(slot);
    }

    public @NotNull BaseGui updateItem(int slot, @NotNull ItemStack itemStack) {
        Objects.requireNonNull(itemStack, "itemStack cannot be null");
        GuiItem existing = guiItems.get(slot);
        if (existing == null) {
            return this;
        }
        GuiItem replacement = existing.withItemStack(itemStack);
        guiItems.put(slot, replacement);
        applyToInventory(() -> applyItem(slot, guiItems.get(slot)));
        return this;
    }

    public @NotNull BaseGui updateItem(int slot, @NotNull GuiItem guiItem) {
        Objects.requireNonNull(guiItem, "guiItem cannot be null");
        validateSlot(slot);
        guiItems.put(slot, guiItem);
        applyToInventory(() -> applyItem(slot, guiItems.get(slot)));
        return this;
    }

    public void open(@NotNull HumanEntity player) {
        openAsync(player);
    }

    /** Opens on the player's thread and reports cancellation, shared-instance rejection or retirement. */
    @ApiStatus.Experimental
    public @NotNull java.util.concurrent.CompletableFuture<GuiOperationResult> openAsync(@NotNull HumanEntity player) {
        Objects.requireNonNull(player, "player");
        var result = new java.util.concurrent.CompletableFuture<GuiOperationResult>();
        final FoliaGUIService owner;
        synchronized (this) {
            owner = service();
            if (owner.isClosed()) {
                result.complete(GuiOperationResult.REJECTED);
                return result;
            }
            if (service == null) {
                service = owner;
            }
            HumanEntity current = activeViewer.get();
            if (current != player && !activeViewer.compareAndSet(null, player)) {
                result.complete(GuiOperationResult.REJECTED);
                return result;
            }
        }
        try {
            owner.scheduler().runForEntity(player, () -> {
                try {
                    if (owner.isClosed() || player.isSleeping()) {
                        releaseViewer(player);
                        result.complete(GuiOperationResult.REJECTED);
                        return;
                    }
                    resolveTheme();
                    populateInventory();
                    if (InventoryViews.top(player.getOpenInventory()) == inventory) {
                        result.complete(GuiOperationResult.OPENED);
                    } else if (player.openInventory(inventory) == null) {
                        releaseViewer(player);
                        result.complete(GuiOperationResult.REJECTED);
                    } else {
                        result.complete(GuiOperationResult.OPENED);
                    }
                } catch (RuntimeException failure) {
                    releaseViewer(player);
                    result.completeExceptionally(failure);
                }
            }, () -> {
                releaseViewer(player);
                result.complete(GuiOperationResult.RETIRED);
            });
        } catch (RuntimeException failure) {
            releaseViewer(player);
            result.completeExceptionally(failure);
        }
        return result;
    }

    public void close(@NotNull HumanEntity player) {
        closeAsync(player);
    }

    /** Closes this GUI on the owning thread; another active window is never closed by this operation. */
    @ApiStatus.Experimental
    public @NotNull java.util.concurrent.CompletableFuture<GuiOperationResult> closeAsync(@NotNull HumanEntity player) {
        var result = new java.util.concurrent.CompletableFuture<GuiOperationResult>();
        allowedCloses.add(player.getUniqueId());
        service().scheduler().runForEntity(player, () -> {
            try {
                if (InventoryViews.top(player.getOpenInventory()) != inventory) {
                    allowedCloses.remove(player.getUniqueId());
                    result.complete(GuiOperationResult.REJECTED);
                    return;
                }
                player.closeInventory();
                releaseViewer(player);
                result.complete(GuiOperationResult.CLOSED);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        }, () -> {
            allowedCloses.remove(player.getUniqueId());
            releaseViewer(player);
            result.complete(GuiOperationResult.RETIRED);
        });
        return result;
    }

    void closeOwnedViewer(HumanEntity player) {
        allowedCloses.add(player.getUniqueId());
        player.closeInventory();
        stopAutoUpdate(player);
        allowedCloses.remove(player.getUniqueId());
    }

    private void releaseViewer(HumanEntity player) {
        if (activeViewer.compareAndSet(player, null)) {
            contentRevision.incrementAndGet();
        }
    }

    long beginContentRequest() {
        return contentRevision.incrementAndGet();
    }

    boolean acceptsContent(Player player, long revision) {
        return !service().isClosed() && contentRevision.get() == revision && isOpenFor(player);
    }

    /** Resolves the theme for the active viewer, or the service default while this GUI is closed. */
    @ApiStatus.Experimental
    public @NotNull GuiTheme theme() {
        GuiTheme current = resolvedTheme;
        return activeViewer.get() == null || current == null ? service().theme() : current;
    }

    private void resolveTheme() {
        if (activeViewer.get() instanceof Player player) {
            resolvedTheme = service().theme(player);
        }
    }

    protected final <T> java.util.concurrent.CompletableFuture<T> inventoryAsync(java.util.function.Supplier<T> action) {
        var result = new java.util.concurrent.CompletableFuture<T>();
        Runnable run = () -> {
            try {
                result.complete(action.get());
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        };
        HumanEntity viewer = activeViewer.get();
        if (viewer == null) {
            synchronized (this) {
                if (activeViewer.get() == null) {
                    run.run();
                    return result;
                }
            }
            return inventoryAsync(action);
        }
        service().scheduler().runForEntity(viewer, run,
                () -> result.completeExceptionally(new IllegalStateException("Viewer retired")));
        return result;
    }

    public void update() {
        applyToInventory(this::populateInventory);
    }

    public @NotNull BaseGui setUpdateInterval(long ticks) {
        this.updateIntervalTicks = Math.max(0, ticks);
        return this;
    }

    public long getUpdateInterval() {
        return updateIntervalTicks;
    }

    public @NotNull BaseGui onTick(long ticks, @NotNull java.util.function.Consumer<BaseGui> action) {
        this.tickAction = Objects.requireNonNull(action, "action cannot be null");
        return setUpdateInterval(ticks);
    }

    private void tick() {
        resolveTheme();
        java.util.function.Consumer<BaseGui> action = tickAction;
        if (action != null) {
            try {
                action.accept(this);
            } catch (RuntimeException failure) {
                LOGGER.log(java.util.logging.Level.WARNING, "GUI tick action failed", failure);
            }
        }
        populateInventory();
    }

    @ApiStatus.Internal
    public void startAutoUpdate(@NotNull HumanEntity player) {
        if (updateIntervalTicks <= 0) {
            return;
        }
        com.foliagui.scheduler.TaskHandle previous = updateTasks.remove(player.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }
        com.foliagui.scheduler.TaskHandle handle = service().scheduler().runForEntityTimer(
                player, this::tick, () -> stopAutoUpdate(player),
                updateIntervalTicks, updateIntervalTicks);
        updateTasks.put(player.getUniqueId(), handle);
    }

    @ApiStatus.Internal
    public void stopAutoUpdate(@NotNull HumanEntity player) {
        releaseViewer(player);
        com.foliagui.scheduler.TaskHandle handle = updateTasks.remove(player.getUniqueId());
        if (handle != null) {
            handle.cancel();
        }
    }

    public @NotNull BaseGui setForceOpen(boolean forceOpen) {
        this.forceOpen = forceOpen;
        return this;
    }

    public boolean isForceOpen() {
        return forceOpen;
    }

    @ApiStatus.Internal
    public boolean consumeAllowedClose(@NotNull UUID viewerId) {
        return allowedCloses.remove(viewerId);
    }

    public void updateTitle(@NotNull String title) {
        Objects.requireNonNull(title, "title cannot be null");
        updateTitle(Text.of(title));
    }

    public void updateTitle(@NotNull Component title) {
        Objects.requireNonNull(title, "title cannot be null");
        applyToInventory(() -> {
            this.title = title;
            HumanEntity viewer = activeViewer.get();
            Inventory previous = inventory;
            Inventory replacement = guiType == null
                    ? Bukkit.createInventory(this, size, title)
                    : Bukkit.createInventory(this, guiType.getInventoryType(), title);
            this.inventory = replacement;
            Arrays.fill(renderedItems, null);
            Arrays.fill(renderedStacks, null);
            if (this instanceof StorageGui) {
                replacement.setContents(previous.getContents());
            }
            populateInventory();
            if (viewer != null && InventoryViews.top(viewer.getOpenInventory()) == previous) {
                updating = true;
                try {
                    if (viewer.openInventory(replacement) == null) {
                        releaseViewer(viewer);
                        service().guis().unregister(viewer);
                    }
                } finally {
                    updating = false;
                }
            }
        });
    }

    protected final void applyToInventory(@NotNull Runnable mutation) {
        HumanEntity viewer = activeViewer.get();
        if (viewer == null) {
            synchronized (this) {
                if (activeViewer.get() == null) {
                    mutation.run();
                    return;
                }
            }
            applyToInventory(mutation);
            return;
        }
        if (Bukkit.getServer().isOwnedByCurrentRegion(viewer)) {
            resolveTheme();
            mutation.run();
            return;
        }
        service().scheduler().runForEntity(viewer, () -> {
            if (activeViewer.get() == viewer) {
                resolveTheme();
                mutation.run();
            }
        }, null);
    }

    public @NotNull BaseGui addInteractionModifier(@NotNull InteractionModifier modifier) {
        interactionModifiers.add(modifier);
        return this;
    }

    public @NotNull BaseGui removeInteractionModifier(@NotNull InteractionModifier modifier) {
        interactionModifiers.remove(modifier);
        return this;
    }

    public @NotNull BaseGui clearInteractionModifiers() {
        interactionModifiers.clear();
        return this;
    }

    public boolean isModifierActive(@NotNull InteractionModifier modifier) {
        return interactionModifiers.contains(modifier);
    }

    public @NotNull BaseGui setDefaultClickAction(@Nullable GuiAction<InventoryClickEvent> action) {
        this.defaultClickAction = action;
        return this;
    }

    public @NotNull BaseGui setDefaultTopClickAction(@Nullable GuiAction<InventoryClickEvent> action) {
        this.defaultTopClickAction = action;
        return this;
    }

    public @NotNull BaseGui setPlayerInventoryAction(@Nullable GuiAction<InventoryClickEvent> action) {
        this.playerInventoryAction = action;
        return this;
    }

    public @NotNull BaseGui setOutsideClickAction(@Nullable GuiAction<InventoryClickEvent> action) {
        this.outsideClickAction = action;
        return this;
    }

    public @NotNull BaseGui setDragAction(@Nullable GuiAction<InventoryDragEvent> action) {
        this.dragAction = action;
        return this;
    }

    public @NotNull BaseGui setOpenAction(@Nullable GuiAction<InventoryOpenEvent> action) {
        this.openAction = action;
        return this;
    }

    public @NotNull BaseGui setCloseAction(@Nullable GuiAction<InventoryCloseEvent> action) {
        this.closeAction = action;
        return this;
    }

    public @NotNull BaseGui setSlotAction(int slot, @Nullable GuiAction<InventoryClickEvent> action) {
        validateSlot(slot);
        if (action == null) {
            slotActions.remove(slot);
        } else {
            slotActions.put(slot, action);
        }
        return this;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public @NotNull GuiFiller filler() {
        return new GuiFiller(this);
    }

    public @NotNull List<Player> getViewerPlayers() {
        List<Player> players = new ArrayList<>();
        if (activeViewer.get() instanceof Player player) {
            players.add(player);
        }
        return players;
    }

    public boolean isOpenFor(@NotNull HumanEntity player) {
        return service().guis().getOpenGui(player) == this;
    }

    public @NotNull Component title() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public int getRows() {
        return rows;
    }

    public @Nullable GuiType getGuiType() {
        return guiType;
    }

    /**
     * Returns the legacy mutable slot map.
     * @deprecated Use {@link #guiItemsSnapshot()} for reads and {@link #setItem(int, GuiItem)} for mutations.
     */
    @Deprecated
    public @NotNull Map<Integer, GuiItem> getGuiItems() {
        return guiItems;
    }

    /** Returns an immutable slot-map snapshot; GuiItem values retain their documented mutable identity. */
    @ApiStatus.Experimental
    public @NotNull Map<Integer, GuiItem> guiItemsSnapshot() {
        return Map.copyOf(guiItems);
    }

    /** Creates and opens a separate GUI from the factory for this player on their owning thread. */
    @ApiStatus.Experimental
    public static @NotNull java.util.concurrent.CompletableFuture<GuiOperationResult> openFor(
            @NotNull FoliaGUIService service, @NotNull Player player,
            @NotNull java.util.function.Function<Player, ? extends BaseGui> factory) {
        var result = new java.util.concurrent.CompletableFuture<GuiOperationResult>();
        service.scheduler().runForEntity(player, () -> {
            try {
                factory.apply(player).service(service).openAsync(player).whenComplete((outcome, failure) -> {
                    if (failure == null) {
                        result.complete(outcome);
                    } else {
                        result.completeExceptionally(failure);
                    }
                });
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        }, () -> result.complete(GuiOperationResult.RETIRED));
        return result;
    }

    public @Nullable GuiAction<InventoryClickEvent> getSlotAction(int slot) {
        return slotActions.get(slot);
    }

    public @Nullable GuiAction<InventoryClickEvent> getDefaultClickAction() {
        return defaultClickAction;
    }

    public @Nullable GuiAction<InventoryClickEvent> getDefaultTopClickAction() {
        return defaultTopClickAction;
    }

    public @Nullable GuiAction<InventoryClickEvent> getPlayerInventoryAction() {
        return playerInventoryAction;
    }

    public @Nullable GuiAction<InventoryClickEvent> getOutsideClickAction() {
        return outsideClickAction;
    }

    public @Nullable GuiAction<InventoryDragEvent> getDragAction() {
        return dragAction;
    }

    public @Nullable GuiAction<InventoryOpenEvent> getOpenAction() {
        return openAction;
    }

    public @Nullable GuiAction<InventoryCloseEvent> getCloseAction() {
        return closeAction;
    }

    public boolean isUpdating() {
        return updating;
    }

    protected void validateSlot(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IllegalArgumentException("slot " + slot + " is out of bounds for size " + size);
        }
    }

    protected static @NotNull Player asPlayer(@NotNull HumanEntity entity) {
        return (Player) entity;
    }
}
