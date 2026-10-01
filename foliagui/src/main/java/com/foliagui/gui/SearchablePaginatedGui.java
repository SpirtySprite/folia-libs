package com.foliagui.gui;

import com.foliagui.item.GuiItem;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

public class SearchablePaginatedGui extends PaginatedGui {

    private final List<GuiItem> master = new CopyOnWriteArrayList<>();
    private final Map<GuiItem, String> searchKeys = new ConcurrentHashMap<>();
    private volatile BiPredicate<GuiItem, String> matcher = (item, term) -> {
        String key = searchKeys.get(item);
        return key != null && java.util.Arrays.stream(normalize(term).split("\\s+"))
                .allMatch(key::contains);
    };
    private final java.util.concurrent.atomic.AtomicLong searches = new java.util.concurrent.atomic.AtomicLong();
    private volatile String currentTerm = "";

    public SearchablePaginatedGui(int rows, @NotNull Component title, int pageSize) {
        super(rows, title, pageSize);
    }

    public SearchablePaginatedGui(@NotNull GuiType type, @NotNull Component title, int pageSize) {
        super(type, title, pageSize);
    }

    public @NotNull SearchablePaginatedGui addSearchableItem(@NotNull GuiItem item, @NotNull String searchKey) {
        master.add(item);
        searchKeys.put(item, normalize(searchKey));
        if (currentTerm.isBlank() || matcher.test(item, currentTerm)) {
            addPageItem(item);
        }
        return this;
    }

    private static String normalize(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip();
    }

    /** Removes an item from master entries, keys and the visible result set. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public synchronized boolean removeSearchableItem(@NotNull GuiItem item) {
        boolean removed = master.removeIf(existing -> existing == item);
        searchKeys.remove(item);
        search(currentTerm);
        return removed;
    }

    /** Replaces one master entry and its search key, preserving insertion order. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public synchronized @NotNull SearchablePaginatedGui replaceSearchableItem(@NotNull GuiItem previous,
            @NotNull GuiItem replacement, @NotNull String key) {
        int index = master.indexOf(previous);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown searchable item");
        }
        if (replacement != previous && master.contains(replacement)) {
            throw new IllegalArgumentException("Replacement item already exists");
        }
        master.set(index, java.util.Objects.requireNonNull(replacement, "replacement"));
        searchKeys.remove(previous);
        searchKeys.put(replacement, normalize(key));
        search(currentTerm);
        return this;
    }

    /** Updates a normalized key and aliases, then recomputes visibility. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public synchronized @NotNull SearchablePaginatedGui searchKey(@NotNull GuiItem item, @NotNull String key, String... aliases) {
        if (!master.contains(item)) {
            throw new IllegalArgumentException("Unknown searchable item");
        }
        searchKeys.put(item, normalize(key + " " + String.join(" ", aliases)));
        search(currentTerm);
        return this;
    }

    /** Matches an immutable catalogue snapshot asynchronously; only the latest active session may apply results. */
    @org.jetbrains.annotations.ApiStatus.Experimental
    public @NotNull java.util.concurrent.CompletableFuture<GuiOperationResult> searchAsync(@NotNull Player player,
            @NotNull String term, long debounceTicks) {
        if (debounceTicks < 0) {
            throw new IllegalArgumentException("debounceTicks must be nonnegative");
        }
        long revision = searches.incrementAndGet();
        long content = beginContentRequest();
        var result = new java.util.concurrent.CompletableFuture<GuiOperationResult>();
        Runnable start = () -> {
            if (revision != searches.get()) {
                result.complete(GuiOperationResult.SUPERSEDED);
                return;
            }
            List<GuiItem> snapshot = List.copyOf(master);
            BiPredicate<GuiItem, String> matches = matcher;
            boolean accepted = service().scheduler().tryRunAsync(() -> {
                try {
                    List<GuiItem> filtered = snapshot.stream().filter(item -> term.isBlank() || matches.test(item, term)).toList();
                    service().scheduler().runForEntity(player, () -> {
                        if (revision != searches.get() || !acceptsContent(player, content)) {
                            result.complete(GuiOperationResult.SUPERSEDED);
                            return;
                        }
                        currentTerm = term;
                        clearPageItems();
                        addPageItem(filtered);
                        openPage(1);
                        result.complete(GuiOperationResult.OPENED);
                    }, () -> result.complete(GuiOperationResult.RETIRED));
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            });
            if (!accepted) {
                result.complete(GuiOperationResult.REJECTED);
            }
        };
        if (debounceTicks == 0) {
            service().scheduler().runForEntity(player, start, () -> result.complete(GuiOperationResult.RETIRED));
        } else {
            service().scheduler().runForEntityLater(player, start, () -> result.complete(GuiOperationResult.RETIRED), debounceTicks);
        }
        return result;
    }

    public @NotNull SearchablePaginatedGui matcher(@NotNull BiPredicate<GuiItem, String> matcher) {
        this.matcher = matcher;
        return this;
    }

    public @NotNull SearchablePaginatedGui search(@NotNull String term) {
        searches.incrementAndGet();
        this.currentTerm = term;
        clearPageItems();
        for (GuiItem item : master) {
            if (term.isBlank() || matcher.test(item, term)) {
                addPageItem(item);
            }
        }
        openPage(1);
        return this;
    }

    public @NotNull SearchablePaginatedGui clearSearch() {
        return search("");
    }

    public @NotNull String getCurrentSearchTerm() {
        return currentTerm;
    }

    public void promptSearch(@NotNull Player player) {
        ChatPrompt.ask(service(), player, service().theme(player).message(GuiMessage.SEARCH_PROMPT), 0, term -> {
            if (term == null) {
                return;
            }
            if (term.equalsIgnoreCase(service().theme(player).message(GuiMessage.SEARCH_CLEAR))) {
                clearSearch();
            } else {
                search(term);
            }
            open(player);
        });
    }
}
