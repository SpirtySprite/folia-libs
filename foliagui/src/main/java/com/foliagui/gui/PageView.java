package com.foliagui.gui;

import com.foliagui.item.GuiItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public final class PageView<T> {

    private final PaginatedGui gui;
    private final Function<T, GuiItem> renderer;
    private final List<T> source = new ArrayList<>();
    private @Nullable Predicate<T> filter;
    private @Nullable Comparator<T> order;
    private List<T> visible = List.of();
    private PositionPolicy positionPolicy = PositionPolicy.RESET;
    private T selected;

    PageView(@NotNull PaginatedGui gui, @NotNull Collection<T> entries, @NotNull Function<T, GuiItem> renderer) {
        this.gui = gui;
        this.renderer = renderer;
        this.source.addAll(entries);
        rebuild(false);
    }

    /** Controls position after entries are filtered, sorted or replaced. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public enum PositionPolicy { RESET, KEEP_PAGE, KEEP_SELECTED }

    /** Selects how rebuilds restore position. Missing selected entries fall back to the current page. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public synchronized @NotNull PageView<T> positionPolicy(@NotNull PositionPolicy policy) {
        positionPolicy = java.util.Objects.requireNonNull(policy, "policy");
        return this;
    }

    /** Sets the entry whose page is retained by KEEP_SELECTED. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public synchronized @NotNull PageView<T> selected(@Nullable T entry) {
        selected = entry;
        return this;
    }

    public synchronized @NotNull PageView<T> filter(@Nullable Predicate<T> filter) {
        this.filter = filter;
        rebuild(true);
        return this;
    }

    public synchronized @NotNull PageView<T> sort(@Nullable Comparator<T> order) {
        this.order = order;
        rebuild(true);
        return this;
    }

    public synchronized @NotNull PageView<T> entries(@NotNull Collection<T> entries) {
        source.clear();
        source.addAll(entries);
        rebuild(true);
        return this;
    }

    public synchronized @NotNull PageView<T> refresh() {
        rebuild(true);
        return this;
    }

    public synchronized @NotNull List<T> visible() {
        return visible;
    }

    public synchronized int size() {
        return visible.size();
    }

    public synchronized boolean isEmpty() {
        return visible.isEmpty();
    }

    private void rebuild(boolean update) {
        int previousPage = gui.getCurrentPage();
        List<T> next = new ArrayList<>(source.size());
        for (T entry : source) {
            if (filter == null || filter.test(entry)) {
                next.add(entry);
            }
        }
        if (order != null) {
            next.sort(order);
        }
        List<T> snapshot = java.util.Collections.unmodifiableList(next);
        visible = snapshot;
        gui.setPageItemSupplier(snapshot.size(), index -> renderer.apply(snapshot.get(index)));
        if (update) {
            int index = selected == null ? -1 : snapshot.indexOf(selected);
            int page = positionPolicy == PositionPolicy.RESET ? 1
                    : positionPolicy == PositionPolicy.KEEP_SELECTED && index >= 0
                    ? index / Math.max(1, gui.pageCapacity()) + 1 : previousPage;
            gui.openPage(page);
        }
    }
}
