package com.foliagui.gui;

import com.foliagui.item.GuiItem;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AsyncContent {

    private static final Logger LOGGER = Logger.getLogger(AsyncContent.class.getName());

    private AsyncContent() {
    }

    public static <T> void load(@NotNull BaseGui gui, @NotNull Player player,
                                 @NotNull Supplier<T> fetch, @NotNull Consumer<T> onLoaded) {
        load(gui, player, fetch, onLoaded, null);
    }

    public static <T> void load(@NotNull BaseGui gui, @NotNull Player player, @NotNull Supplier<T> fetch,
                                 @NotNull Consumer<T> onLoaded, @Nullable Consumer<Throwable> onError) {
        loadAsync(gui, player, fetch, onLoaded, onError);
    }

    /** Fetches asynchronously and renders on the player thread. Superseded or closed sessions never invoke callbacks. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public static <T> @NotNull java.util.concurrent.CompletableFuture<GuiOperationResult> loadAsync(
            @NotNull BaseGui gui, @NotNull Player player, @NotNull Supplier<T> fetch,
            @NotNull Consumer<T> onLoaded, @Nullable Consumer<Throwable> onError) {
        var completion = new java.util.concurrent.CompletableFuture<GuiOperationResult>();
        long request = gui.beginContentRequest();
        int slot = centre(gui);
        GuiItem marker;
        synchronized (gui) {
            GuiItem current = gui.getGuiItem(slot);
            marker = current == null || current == gui.loadingItem ? gui.service().theme().loading() : null;
            if (marker != null) {
                gui.loadingItem = marker;
                gui.setItem(slot, marker);
            }
        }
        gui.openAsync(player).whenComplete((outcome, openingFailure) -> {
            if (openingFailure != null) {
                completion.completeExceptionally(openingFailure);
                return;
            }
            if (outcome != GuiOperationResult.OPENED) {
                completion.complete(outcome);
                return;
            }
            boolean accepted = gui.service().scheduler().tryRunAsync(() -> {
                T value = null;
                Throwable failure = null;
                try {
                    value = fetch.get();
                } catch (Exception thrown) {
                    failure = thrown;
                }
                T loaded = value;
                Throwable error = failure;
                gui.service().scheduler().runForEntity(player, () -> {
                    if (!gui.acceptsContent(player, request)) {
                        completion.complete(GuiOperationResult.SUPERSEDED);
                        return;
                    }
                    try {
                        if (marker != null && gui.getGuiItem(slot) == marker) {
                            gui.loadingItem = null;
                            if (error == null) {
                                gui.removeItem(slot);
                            } else {
                                gui.setItem(slot, gui.theme().loadFailed());
                            }
                        }
                        if (error == null) {
                            onLoaded.accept(loaded);
                            completion.complete(GuiOperationResult.OPENED);
                        } else {
                            LOGGER.log(Level.WARNING, "Async GUI content failed to load", error);
                            if (onError != null) {
                                onError.accept(error);
                            }
                            completion.completeExceptionally(error);
                        }
                        gui.update();
                    } catch (RuntimeException renderingFailure) {
                        completion.completeExceptionally(renderingFailure);
                    }
                }, () -> completion.complete(GuiOperationResult.RETIRED));
            });
            if (!accepted) {
                completion.complete(GuiOperationResult.REJECTED);
            }
        });
        return completion;
    }

    public static <T> void loadPages(@NotNull PaginatedGui gui, @NotNull Player player,
                                      @NotNull Supplier<List<T>> fetch, @NotNull Function<T, GuiItem> renderer) {
        load(gui, player, fetch, entries -> {
            List<T> snapshot = new java.util.ArrayList<>(entries);
            gui.setPageItemSupplier(snapshot.size(), index -> renderer.apply(snapshot.get(index)));
        }, null);
    }

    static int centre(@NotNull BaseGui gui) {
        int rows = gui.getRows();
        if (rows <= 0) {
            return gui.getSize() / 2;
        }
        return ((rows - 1) / 2) * 9 + 4;
    }
}
