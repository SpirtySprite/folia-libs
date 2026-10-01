package net.folianpc.internal.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.folianpc.api.Skin;
import net.folianpc.api.SkinFetchResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class MojangSkinService {

    private static final String NAME_TO_ID = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String ID_TO_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    record Cached(CompletableFuture<Skin> skin, long expiresAt) {
    }

    private final HttpClient http;
    private final SkinRequests requests;
    private final java.util.Set<CompletableFuture<Skin>> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private volatile int maxEntries = 1024;

    public MojangSkinService() {
        http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        requests = new SkinRequests(request -> http.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
    }

    MojangSkinService(Function<HttpRequest, CompletableFuture<HttpResponse<String>>> sender) {
        http = null;
        requests = new SkinRequests(sender);
    }

    public synchronized void limits(int entries, int parallel, int queued) {
        if (entries < 1 || entries > 65536 || parallel < 1 || parallel > 64 || queued < 0 || queued > 65536) {
            throw new IllegalArgumentException("Invalid skin cache or request limits");
        }
        maxEntries = entries;
        prune(nameCache);
        prune(idCache);
        prune(urlCache);
        requests.limits(parallel, queued);
    }

    public CompletableFuture<SkinFetchResult> result(CompletableFuture<Skin> future) {
        return future.handle((skin, error) -> {
            if (error == null) return new SkinFetchResult(SkinFetchResult.Status.FOUND, java.util.Optional.of(skin),
                    Duration.ZERO, java.util.Optional.empty());
            Throwable cause = unwrap(error);
            SkinFetchResult.Status status = cause instanceof SkinFailure failure ? failure.status
                    : cause instanceof com.google.gson.JsonParseException || cause instanceof IllegalArgumentException
                    || cause instanceof NullPointerException || cause instanceof IllegalStateException
                    ? SkinFetchResult.Status.MALFORMED : SkinFetchResult.Status.UNAVAILABLE;
            Duration retry = cause instanceof SkinFailure failure ? failure.retryAfter : Duration.ZERO;
            return new SkinFetchResult(status, java.util.Optional.empty(), retry, java.util.Optional.of(cause));
        });
    }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof java.util.concurrent.CompletionException || error instanceof java.util.concurrent.ExecutionException)
                && error.getCause() != null) error = error.getCause();
        return error;
    }

    private static long now() { return System.nanoTime() / 1_000_000; }

    private <K> void prune(ConcurrentHashMap<K, Cached> cache) {
        long time = now();
        cache.entrySet().removeIf(entry -> entry.getValue().skin().isDone() && entry.getValue().expiresAt() <= time);
        if (cache.size() >= maxEntries) {
            for (var entry : cache.entrySet()) {
                if (cache.size() < maxEntries) break;
                if (entry.getValue().skin().isDone()) cache.remove(entry.getKey(), entry.getValue());
            }
        }
    }
    private final ConcurrentHashMap<String, Cached> nameCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Cached> idCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Cached> urlCache = new ConcurrentHashMap<>();

    private volatile long ttlMillis = Duration.ofMinutes(30).toMillis();
    private volatile long failureTtlMillis = Duration.ofSeconds(5).toMillis();

    public void ttl(Duration ttl) {
        this.ttlMillis = Math.max(0, ttl.toMillis());
    }

    public void failureTtl(Duration ttl) {
        this.failureTtlMillis = Math.max(0, ttl.toMillis());
    }

    public CompletableFuture<Skin> byName(String name) {
        return lookup(nameCache, name.toLowerCase(Locale.ROOT),
                key -> get(NAME_TO_ID + key).thenCompose(body -> byId(parseId(body))));
    }

    public CompletableFuture<Skin> byId(UUID id) {
        return lookup(idCache, id,
                key -> get(ID_TO_PROFILE + undash(key) + "?unsigned=false").thenApply(MojangSkinService::parseSkin));
    }

    <K> CompletableFuture<Skin> lookup(ConcurrentHashMap<K, Cached> cache, K key,
                                       Function<K, CompletableFuture<Skin>> fetch) {
        java.util.Objects.requireNonNull(key, "key");
        Cached entry;
        synchronized (this) {
            if (closed) return CompletableFuture.failedFuture(SkinRequests.failure(SkinFetchResult.Status.SHUTDOWN));
            Cached existing = cache.get(key);
            if (existing != null && (!existing.skin().isDone() || existing.expiresAt() > now())) {
                return existing.skin().copy();
            }
            prune(cache);
            if (cache.size() >= maxEntries) return CompletableFuture.failedFuture(SkinRequests.failure(SkinFetchResult.Status.BUSY));
            entry = new Cached(new CompletableFuture<>(), Long.MAX_VALUE);
            cache.put(key, entry);
            pending.add(entry.skin());
        }
        CompletableFuture<Skin> fetched;
        try { fetched = java.util.Objects.requireNonNull(fetch.apply(key), "fetch result"); }
        catch (RuntimeException failure) { fetched = CompletableFuture.failedFuture(failure); }
        fetched.whenComplete((skin, error) -> {
            long duration = error == null ? ttlMillis : failureTtlMillis;
            Throwable cause = error == null ? null : unwrap(error);
            if (cause instanceof SkinFailure failure) duration = Math.max(duration, failure.retryAfter.toMillis());
            boolean ending;
            synchronized (this) {
                ending = closed;
                if (ending) cache.remove(key, entry);
                else if (duration > 0) cache.replace(key, entry, new Cached(entry.skin(), now() + duration));
                else cache.remove(key, entry);
                pending.remove(entry.skin());
            }
            if (ending) entry.skin().completeExceptionally(SkinRequests.failure(SkinFetchResult.Status.SHUTDOWN));
            else if (error == null) entry.skin().complete(skin);
            else entry.skin().completeExceptionally(error);
        });
        return entry.skin().copy();
    }

    private static final String MINESKIN = "https://api.mineskin.org/generate/url";

    public CompletableFuture<Skin> byUrl(String imageUrl) {
        return lookup(urlCache, imageUrl, this::generateFromUrl);
    }

    private CompletableFuture<Skin> generateFromUrl(String imageUrl) {
        JsonObject payload = new JsonObject();
        payload.addProperty("url", imageUrl);
        String body = payload.toString();
        HttpRequest request = HttpRequest.newBuilder(URI.create(MINESKIN))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return requests.send(request).thenApply(MojangSkinService::body).thenApply(MojangSkinService::parseMineskin);
    }

    static Skin parseMineskin(String json) {
        JsonObject texture = JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonObject("data").getAsJsonObject("texture");
        return Skin.of(texture.get("value").getAsString(), texture.get("signature").getAsString());
    }

    private CompletableFuture<String> get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        return requests.send(request).thenApply(MojangSkinService::body);
    }

    private static String body(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 204 || status == 404) throw SkinRequests.failure(SkinFetchResult.Status.NOT_FOUND);
        if (status == 429) {
            Duration retry = response.headers().firstValue("Retry-After").map(MojangSkinService::retryAfter).orElse(Duration.ZERO);
            throw new SkinFailure(SkinFetchResult.Status.RATE_LIMITED, "Skin service returned 429", retry);
        }
        if (status != 200) throw SkinRequests.failure(SkinFetchResult.Status.UNAVAILABLE);
        if (response.body() == null || response.body().isBlank()) throw SkinRequests.failure(SkinFetchResult.Status.MALFORMED);
        return response.body();
    }

    private static Duration retryAfter(String value) {
        try { return Duration.ofSeconds(Math.min(86400, Math.max(0, Long.parseLong(value)))); }
        catch (RuntimeException invalidSeconds) {
            try {
                Duration duration = Duration.between(java.time.Instant.now(), java.time.ZonedDateTime.parse(value,
                        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return duration.isNegative() ? Duration.ZERO : duration.compareTo(Duration.ofDays(1)) > 0 ? Duration.ofDays(1) : duration;
            } catch (RuntimeException invalidDate) { return Duration.ZERO; }
        }
    }

    public void close() {
        java.util.List<CompletableFuture<Skin>> ending;
        synchronized (this) {
            if (closed) return;
            closed = true;
            ending = java.util.List.copyOf(pending);
            pending.clear();
            nameCache.clear();
            idCache.clear();
            urlCache.clear();
        }
        ending.forEach(future -> future.completeExceptionally(SkinRequests.failure(SkinFetchResult.Status.SHUTDOWN)));
        requests.close();
        if (http != null) http.shutdownNow();
    }

    static UUID parseId(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return dash(root.get("id").getAsString());
    }

    static Skin parseSkin(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray properties = root.getAsJsonArray("properties");
        for (JsonElement element : properties) {
            JsonObject property = element.getAsJsonObject();
            if ("textures".equals(property.get("name").getAsString())) {
                String value = property.get("value").getAsString();
                String signature = property.has("signature") ? property.get("signature").getAsString() : null;
                return Skin.of(value, signature);
            }
        }
        throw new IllegalStateException("Profile has no textures property");
    }

    private static String undash(UUID id) {
        return id.toString().replace("-", "");
    }

    static UUID dash(String undashed) {
        return UUID.fromString(undashed.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"));
    }
}