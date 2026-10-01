package com.foliagui.gui;

import com.foliagui.builder.gui.PaginatedGuiBuilder;
import com.foliagui.item.GuiItem;
import com.foliagui.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

public class PaginatedGui extends BaseGui {

    private final List<GuiItem> pageItems = Collections.synchronizedList(new ArrayList<>());
    private final Map<Integer, GuiItem> currentPage = new ConcurrentHashMap<>();
    private final Map<Integer, GuiItem> suppliedItems = new java.util.LinkedHashMap<>(16, 0.75f, true);
    private volatile int cachedPages = 8;
    private volatile java.util.function.IntConsumer pageChanged = page -> { };
    private volatile List<Integer> cachedPageSlots;
    private final AtomicInteger pageNum = new AtomicInteger();
    private volatile int pageSize;
    private volatile int suppliedItemCount;
    private volatile IntFunction<GuiItem> pageItemSupplier;
    private volatile int previousSlot = -1;
    private volatile int indicatorSlot = -1;
    private volatile int nextSlot = -1;
    private volatile boolean hideUnavailableControls = true;
    private GuiTheme renderedTheme;
    private long renderedThemeRevision;
    private volatile long renderedControlState = Long.MIN_VALUE;

    public PaginatedGui(int rows, @NotNull Component title, int pageSize) {
        super(rows, title);
        this.pageSize = Math.max(0, pageSize);
    }

    public PaginatedGui(@NotNull GuiType type, @NotNull Component title, int pageSize) {
        super(type, title);
        this.pageSize = Math.max(0, pageSize);
    }

    public static @NotNull PaginatedGuiBuilder builder() {
        return new PaginatedGuiBuilder();
    }

    public @NotNull PaginatedGui addPageItem(@NotNull GuiItem item) {
        pageItems.add(item);
        return this;
    }

    public @NotNull PaginatedGui addPageItem(@NotNull GuiItem... items) {
        for (GuiItem item : items) {
            pageItems.add(item);
        }
        return this;
    }

    public @NotNull PaginatedGui addPageItem(@NotNull Collection<GuiItem> items) {
        pageItems.addAll(items);
        return this;
    }

    public @NotNull PaginatedGui clearPageItems() {
        pageItems.clear();
        synchronized (suppliedItems) {
            suppliedItems.clear();
        }
        suppliedItemCount = 0;
        pageItemSupplier = null;
        return this;
    }

    /**
     * Returns the legacy mutable entry list.
     * @deprecated Use {@link #pageItemsSnapshot()} and explicit entry mutation methods.
     */
    @Deprecated
    public @NotNull List<GuiItem> getPageItems() {
        return pageItems;
    }

    /** Returns an immutable snapshot of eagerly added items. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public @NotNull List<GuiItem> pageItemsSnapshot() {
        synchronized (pageItems) {
            return List.copyOf(pageItems);
        }
    }

    /** Removes an eager entry. Supplier-backed entries are invalidated through invalidateItem. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public boolean removePageItem(@NotNull GuiItem item) {
        return pageItems.remove(item);
    }

    /** Replaces an eager entry while preserving its position. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public @NotNull PaginatedGui replacePageItem(int index, @NotNull GuiItem item) {
        pageItems.set(index, Objects.requireNonNull(item, "item"));
        return this;
    }

    /** Bounds supplier retention by this many effective pages. Zero disables retention. Default is eight. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public @NotNull PaginatedGui cachePages(int pages) {
        if (pages < 0) {
            throw new IllegalArgumentException("pages must be nonnegative");
        }
        cachedPages = pages;
        synchronized (suppliedItems) {
            suppliedItems.clear();
        }
        return this;
    }

    /** Invalidates one supplier entry and schedules a redraw of the active page. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public @NotNull PaginatedGui invalidateItem(int index) {
        if (index < 0 || index >= getPageItemsCount()) {
            throw new IllegalArgumentException("entry index out of bounds");
        }
        synchronized (suppliedItems) {
            suppliedItems.remove(index);
        }
        update();
        return this;
    }

    /** Invalidates one one-based page and schedules a redraw. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public @NotNull PaginatedGui invalidatePage(int page) {
        if (page < 1 || page > getPagesCount()) {
            throw new IllegalArgumentException("page out of bounds");
        }
        int start = (page - 1) * perPage();
        synchronized (suppliedItems) {
            suppliedItems.keySet().removeIf(index -> index >= start && index < start + perPage());
        }
        update();
        return this;
    }

    /** Returns the effective content capacity used by both page counting and rendering. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public int pageCapacity() {
        return perPage();
    }

    /** Opens a remotely paginated catalogue. Fetch callbacks run asynchronously and rendering runs on the player thread. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public <T> @NotNull RemotePages<T> remotePages(@NotNull Player player,
            @NotNull java.util.function.Function<RemotePages.Request, java.util.concurrent.CompletionStage<RemotePages.Page<T>>> fetch,
            @NotNull java.util.function.Function<T, GuiItem> renderer) {
        return new RemotePages<>(this, player, fetch, renderer);
    }

    boolean ownsPageListener(java.util.function.IntConsumer listener) {
        return pageChanged == listener;
    }

    synchronized boolean clearPageListener(java.util.function.IntConsumer listener) {
        if (pageChanged != listener) {
            return false;
        }
        pageChanged = page -> { };
        return true;
    }

    synchronized void pageListener(java.util.function.IntConsumer listener) {
        pageChanged = listener;
    }

    public int getPageItemsCount() {
        return pageItemSupplier == null ? pageItems.size() : suppliedItemCount;
    }

    public @NotNull PaginatedGui setPageItemSupplier(int itemCount, @NotNull IntFunction<GuiItem> supplier) {
        pageItems.clear();
        synchronized (suppliedItems) {
            suppliedItems.clear();
        }
        suppliedItemCount = Math.max(0, itemCount);
        pageItemSupplier = Objects.requireNonNull(supplier, "supplier cannot be null");
        pageNum.set(0);
        return this;
    }

    public <T> @NotNull PageView<T> view(@NotNull Collection<T> entries, @NotNull java.util.function.Function<T, GuiItem> renderer) {
        return new PageView<>(this, entries, renderer);
    }

    public @NotNull PaginatedGui pageControls() {
        return pageControls(false);
    }

    public @NotNull PaginatedGui pageControls(boolean fillRow) {
        int row = getRows() > 0 ? getRows() : 1;
        pageControls(com.foliagui.util.Slot.of(row, 1), com.foliagui.util.Slot.of(row, 5),
                com.foliagui.util.Slot.of(row, 9));
        if (fillRow && getRows() > 0) {
            filler().fillRow(row, theme().filler());
        }
        return this;
    }

    public @NotNull PaginatedGui pageControls(int previousSlot, int indicatorSlot, int nextSlot) {
        validateControl(previousSlot);
        validateControl(indicatorSlot);
        validateControl(nextSlot);
        this.previousSlot = previousSlot;
        this.indicatorSlot = indicatorSlot;
        this.nextSlot = nextSlot;
        GuiItem placeholder = theme().filler();
        for (int slot : new int[]{previousSlot, indicatorSlot, nextSlot}) {
            if (slot >= 0) {
                setItem(slot, placeholder);
            }
        }
        renderedControlState = Long.MIN_VALUE;
        return this;
    }

    public @NotNull PaginatedGui hideUnavailableControls(boolean hide) {
        this.hideUnavailableControls = hide;
        renderedControlState = Long.MIN_VALUE;
        return this;
    }

    public boolean hasPageControls() {
        return previousSlot >= 0 || indicatorSlot >= 0 || nextSlot >= 0;
    }

    private void validateControl(int slot) {
        if (slot >= 0) {
            validateSlot(slot);
        }
    }

    private void renderControls() {
        if (!hasPageControls()) {
            return;
        }
        int page = pageNum.get();
        int pages = getPagesCount();
        long state = ((long) page << 32) | (pages & 0xffffffffL);
        GuiTheme theme = theme();
        if (state == renderedControlState && theme == renderedTheme && renderedThemeRevision == theme.revision()) {
            return;
        }
        renderedControlState = state;
        renderedTheme = theme;
        renderedThemeRevision = theme.revision();

        if (previousSlot >= 0) {
            setItem(previousSlot, hasPrevious() || !hideUnavailableControls ? theme.previousButton(this) : theme.filler());
        }
        if (indicatorSlot >= 0) {
            setItem(indicatorSlot, theme.pageIndicator(this));
        }
        if (nextSlot >= 0) {
            setItem(nextSlot, hasNext() || !hideUnavailableControls ? theme.nextButton(this) : theme.filler());
        }
    }

    public @NotNull PaginatedGui setPageSize(int pageSize) {
        this.pageSize = Math.max(0, pageSize);
        return this;
    }

    public int getCurrentPage() {
        return pageNum.get() + 1;
    }

    public int getPagesCount() {
        int perPage = perPage();
        if (perPage <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(getPageItemsCount() / (double) perPage));
    }

    public boolean hasNext() {
        return pageNum.get() + 1 < getPagesCount();
    }

    public boolean hasPrevious() {
        return pageNum.get() > 0;
    }

    public boolean next() {
        int pageCount = getPagesCount();
        int previousValue = pageNum.getAndUpdate(current -> current + 1 < pageCount ? current + 1 : current);
        boolean advanced = previousValue + 1 < pageCount;
        if (advanced) {
            pageChanged.accept(getCurrentPage());
            update();
        }
        return advanced;
    }

    public boolean previous() {
        int previousValue = pageNum.getAndUpdate(current -> current > 0 ? current - 1 : current);
        boolean moved = previousValue > 0;
        if (moved) {
            pageChanged.accept(getCurrentPage());
            update();
        }
        return moved;
    }

    public @NotNull PaginatedGui openPage(int page) {
        pageNum.set(Math.max(0, Math.min(page - 1, getPagesCount() - 1)));
        pageChanged.accept(getCurrentPage());
        update();
        return this;
    }

    public @NotNull PaginatedGui openLastPage() {
        return openPage(getPagesCount());
    }

    public void open(@NotNull HumanEntity player, int page) {
        pageNum.set(Math.max(0, Math.min(page - 1, getPagesCount() - 1)));
        open(player);
    }

    public void promptJumpToPage(@NotNull Player player) {
        ChatPrompt.ask(service(), player, service().theme(player).message(GuiMessage.PAGE_PROMPT, getPagesCount()), 20 * 20, input -> {
            if (input == null) {
                return;
            }
            try {
                openPage(Integer.parseInt(input.trim()));
            } catch (NumberFormatException e) {
                player.sendMessage(Text.of(service().theme(player).message(GuiMessage.INVALID_NUMBER)));
            }
            open(player);
        });
    }

    @Override
    protected void populateInventory() {
        renderControls();
        List<Integer> slots = pageSlots();
        boolean[] pagedSlots = new boolean[getSize()];
        for (int slot : slots) {
            pagedSlots[slot] = true;
        }
        for (int slot = 0; slot < getSize(); slot++) {
            if (!pagedSlots[slot]) {
                applyItem(slot, getGuiItem(slot));
            }
        }
        int perPage = perPage();
        pageNum.updateAndGet(page -> Math.min(page, getPagesCount() - 1));
        int start = pageNum.get() * perPage;

        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            int itemIndex = start + i;
            GuiItem item = perPage > 0 && i < perPage && itemIndex < getPageItemsCount()
                    ? pageItem(itemIndex) : null;
            if (item != null) {
                currentPage.put(slot, item);
            } else {
                currentPage.remove(slot);
            }
            applyItem(slot, item);
        }
    }

    @Override
    public @Nullable GuiItem itemAt(int slot) {
        GuiItem paged = currentPage.get(slot);
        return paged != null ? paged : super.itemAt(slot);
    }

    @Override
    protected void onLayoutChanged() {
        cachedPageSlots = null;
    }

    protected @NotNull List<Integer> pageSlots() {
        List<Integer> cached = cachedPageSlots;
        if (cached != null) {
            return cached;
        }
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < getSize(); slot++) {
            if (getGuiItem(slot) == null) {
                slots.add(slot);
            }
        }
        cachedPageSlots = slots;
        return slots;
    }

    private int perPage() {
        return pageSize > 0 ? Math.min(pageSize, pageSlots().size()) : pageSlots().size();
    }

    private @Nullable GuiItem pageItem(int index) {
        IntFunction<GuiItem> supplier = pageItemSupplier;
        if (supplier == null) {
            synchronized (pageItems) {
                return index < pageItems.size() ? pageItems.get(index) : null;
            }
        }
        synchronized (suppliedItems) {
            if (cachedPages == 0) {
                return supplier.apply(index);
            }
            GuiItem item = suppliedItems.computeIfAbsent(index, supplier::apply);
            long limit = (long) cachedPages * Math.max(1, perPage());
            while (suppliedItems.size() > limit) {
                suppliedItems.remove(suppliedItems.keySet().iterator().next());
            }
            return item;
        }
    }
}
