package com.foliagui.gui;

import com.foliagui.item.GuiItem;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** Fetches one page at a time, rejecting superseded results and completions after the menu closes. */
@ApiStatus.Experimental
public final class RemotePages<T> implements AutoCloseable {
    /** One-based page request and its effective capacity. */
    public record Request(int page, int capacity) {
        /** Rejects nonpositive page numbers or capacities. */
        public Request {
            if (page < 1 || capacity < 1) {
                throw new IllegalArgumentException("Positive page and capacity required");
            }
        }
    }

    /** Entries for the requested page and the total catalogue size. */
    public record Page<T>(List<T> entries, int total) {
        /** Freezes entries and rejects impossible catalogue sizes. */
        public Page {
            entries = List.copyOf(entries);
            if (total < entries.size()) {
                throw new IllegalArgumentException("total is smaller than page entries");
            }
        }
    }

    private final PaginatedGui gui;
    private final Player player;
    private final Function<Request, CompletionStage<Page<T>>> fetch;
    private final Function<T, GuiItem> renderer;
    private volatile boolean closed;
    private final ThreadLocal<Boolean> applying = ThreadLocal.withInitial(() -> false);
    private final java.util.function.IntConsumer listener;

    RemotePages(PaginatedGui gui, Player player, Function<Request, CompletionStage<Page<T>>> fetch, Function<T, GuiItem> renderer) {
        this.gui = gui;
        this.player = player;
        this.fetch = Objects.requireNonNull(fetch, "fetch");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        listener = page -> {
            if (!applying.get()) {
                load(page);
            }
        };
        gui.pageListener(listener);
        gui.openAsync(player).thenAccept(outcome -> {
            if (outcome == GuiOperationResult.OPENED) {
                load(1);
            }
        });
    }

    /** Refreshes the currently selected page without loading the whole catalogue. */
    public CompletableFuture<GuiOperationResult> refresh() {
        return load(gui.getCurrentPage());
    }

    private CompletableFuture<GuiOperationResult> load(int page) {
        var result = new CompletableFuture<GuiOperationResult>();
        int capacity = gui.pageCapacity();
        if (closed || capacity < 1 || !gui.ownsPageListener(listener)) {
            result.complete(GuiOperationResult.REJECTED);
            return result;
        }
        long revision = gui.beginContentRequest();
        boolean accepted = gui.service().scheduler().tryRunAsync(() -> {
            try {
                fetch.apply(new Request(page, capacity)).whenComplete((data, failure) ->
                        gui.service().scheduler().runForEntity(player, () -> {
                            if (closed || !gui.acceptsContent(player, revision)) {
                                result.complete(GuiOperationResult.SUPERSEDED);
                                return;
                            }
                            if (failure != null) {
                                result.completeExceptionally(failure);
                                return;
                            }
                            try {
                                Objects.requireNonNull(data, "page result");
                                int offset = Math.multiplyExact(page - 1, capacity);
                                if (data.entries().size() > capacity || offset + (long) data.entries().size() > data.total()) {
                                    throw new IllegalArgumentException("Page exceeds requested bounds");
                                }
                                applying.set(true);
                                gui.setPageItemSupplier(data.total(), index -> index >= offset && index < offset + data.entries().size()
                                        ? renderer.apply(data.entries().get(index - offset)) : null);
                                gui.openPage(page);
                                result.complete(GuiOperationResult.OPENED);
                            } catch (RuntimeException invalid) {
                                result.completeExceptionally(invalid);
                            } finally {
                                applying.set(false);
                            }
                        }, () -> result.complete(GuiOperationResult.RETIRED)));
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        if (!accepted) {
            result.complete(GuiOperationResult.REJECTED);
        }
        return result;
    }

    /** Stops further requests and prevents outstanding results from updating the GUI. */
    @Override
    public void close() {
        closed = true;
        if (gui.clearPageListener(listener)) {
            gui.beginContentRequest();
        }
    }
}
