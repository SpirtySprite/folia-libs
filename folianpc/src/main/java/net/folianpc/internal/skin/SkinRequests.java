package net.folianpc.internal.skin;

import net.folianpc.api.SkinFetchResult;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

final class SkinRequests {
    private record Pending(HttpRequest request, CompletableFuture<HttpResponse<String>> result) { }
    private final Function<HttpRequest, CompletableFuture<HttpResponse<String>>> sender;
    private final ArrayDeque<Pending> queued = new ArrayDeque<>();
    private final Set<Pending> active = new HashSet<>();
    private int parallel = 4;
    private int capacity = 256;
    private boolean closed;
    private boolean draining;

    SkinRequests(Function<HttpRequest, CompletableFuture<HttpResponse<String>>> sender) { this.sender = sender; }

    void limits(int parallel, int capacity) {
        synchronized (this) { this.parallel = parallel; this.capacity = capacity; }
        drain();
    }

    CompletableFuture<HttpResponse<String>> send(HttpRequest request) {
        Pending pending = new Pending(request, new CompletableFuture<>());
        synchronized (this) {
            if (closed) return CompletableFuture.failedFuture(failure(SkinFetchResult.Status.SHUTDOWN));
            if (active.size() + queued.size() >= parallel + capacity) {
                return CompletableFuture.failedFuture(failure(SkinFetchResult.Status.BUSY));
            }
            queued.addLast(pending);
        }
        drain();
        return pending.result();
    }

    private void drain() {
        synchronized (this) {
            if (draining) return;
            draining = true;
        }
        while (true) {
            Pending pending;
            synchronized (this) {
                if (closed || active.size() >= parallel || queued.isEmpty()) { draining = false; return; }
                pending = queued.removeFirst();
                active.add(pending);
            }
            CompletableFuture<HttpResponse<String>> transport;
            try { transport = java.util.Objects.requireNonNull(sender.apply(pending.request()), "transport result"); }
            catch (RuntimeException failure) { transport = CompletableFuture.failedFuture(failure); }
            transport.whenComplete((response, failure) -> {
                synchronized (this) { active.remove(pending); }
                if (failure == null) pending.result().complete(response);
                else pending.result().completeExceptionally(failure);
                drain();
            });
        }
    }

    void close() {
        List<Pending> ending;
        synchronized (this) {
            closed = true;
            ending = java.util.stream.Stream.concat(active.stream(), queued.stream()).toList();
            active.clear();
            queued.clear();
        }
        ending.forEach(pending -> pending.result().completeExceptionally(failure(SkinFetchResult.Status.SHUTDOWN)));
    }

    static SkinFailure failure(SkinFetchResult.Status status) {
        return new SkinFailure(status, "Skin request " + status.name().toLowerCase(java.util.Locale.ROOT), Duration.ZERO);
    }
}
