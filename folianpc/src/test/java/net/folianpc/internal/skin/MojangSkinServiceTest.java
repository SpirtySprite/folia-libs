package net.folianpc.internal.skin;

import net.folianpc.api.Skin;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MojangSkinServiceTest {

    @Test
    void parsesUndashedIdIntoUuid() {
        String json = "{\"id\":\"853c80ef3c3749fdaa49938b674adae6\",\"name\":\"jeb_\"}";
        assertEquals(UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"), MojangSkinService.parseId(json));
    }

    @Test
    void parsesTexturesProperty() {
        String json = "{\"id\":\"x\",\"name\":\"n\",\"properties\":["
                + "{\"name\":\"textures\",\"value\":\"BASE64VALUE\",\"signature\":\"SIG\"}]}";
        Skin skin = MojangSkinService.parseSkin(json);
        assertEquals("BASE64VALUE", skin.value());
        assertEquals("SIG", skin.signature());
    }

    @Test
    void allowsMissingSignature() {
        String json = "{\"properties\":[{\"name\":\"textures\",\"value\":\"V\"}]}";
        Skin skin = MojangSkinService.parseSkin(json);
        assertEquals("V", skin.value());
        assertNull(skin.signature());
    }

    @Test
    void successfulLookupsAreCached() {
        MojangSkinService service = new MojangSkinService();
        var cache = new ConcurrentHashMap<String, MojangSkinService.Cached>();
        AtomicInteger fetches = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            service.lookup(cache, "steve", key -> {
                fetches.incrementAndGet();
                return CompletableFuture.completedFuture(Skin.of("value", "signature"));
            });
        }

        assertEquals(1, fetches.get(), "the skin should be fetched once and reused");
    }

    @Test
    void withNoCooldownFailedLookupsAreRetriedImmediately() {
        MojangSkinService service = new MojangSkinService();
        service.failureTtl(Duration.ZERO);
        var cache = new ConcurrentHashMap<String, MojangSkinService.Cached>();
        AtomicInteger fetches = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            service.lookup(cache, "steve", key -> {
                fetches.incrementAndGet();
                return CompletableFuture.failedFuture(new IllegalStateException("mojang is down"));
            });
        }

        assertEquals(3, fetches.get(), "one network blip must not break a skin until restart");
        assertTrue(cache.isEmpty());
    }

    @Test
    void failedLookupsAreRememberedBrieflySoAnOutageIsNotHammered() {
        MojangSkinService service = new MojangSkinService();
        service.failureTtl(Duration.ofMinutes(1));
        var cache = new ConcurrentHashMap<String, MojangSkinService.Cached>();
        AtomicInteger fetches = new AtomicInteger();

        CompletableFuture<Skin> last = null;
        for (int i = 0; i < 5; i++) {
            last = service.lookup(cache, "steve", key -> {
                fetches.incrementAndGet();
                return CompletableFuture.failedFuture(new IllegalStateException("rate limited"));
            });
        }

        assertEquals(1, fetches.get(), "repeat requests inside the cooldown must not reach Mojang again");
        assertTrue(last.isCompletedExceptionally());
    }

    @Test
    void afterTheCooldownAFailedLookupIsTriedAgain() throws InterruptedException {
        MojangSkinService service = new MojangSkinService();
        service.failureTtl(Duration.ofMillis(20));
        var cache = new ConcurrentHashMap<String, MojangSkinService.Cached>();
        AtomicInteger fetches = new AtomicInteger();

        service.lookup(cache, "steve", key -> {
            fetches.incrementAndGet();
            return CompletableFuture.failedFuture(new IllegalStateException("down"));
        });
        Thread.sleep(60);
        CompletableFuture<Skin> retry = service.lookup(cache, "steve", key -> {
            fetches.incrementAndGet();
            return CompletableFuture.completedFuture(Skin.of("value", null));
        });

        assertEquals(2, fetches.get());
        assertEquals("value", retry.join().value());
    }

    @Test
    void anExpiredEntryIsRefetched() {
        MojangSkinService service = new MojangSkinService();
        service.ttl(Duration.ZERO);
        var cache = new ConcurrentHashMap<String, MojangSkinService.Cached>();
        AtomicInteger fetches = new AtomicInteger();

        service.lookup(cache, "steve", key -> {
            fetches.incrementAndGet();
            return CompletableFuture.completedFuture(Skin.of("value", null));
        });
        service.lookup(cache, "steve", key -> {
            fetches.incrementAndGet();
            return CompletableFuture.completedFuture(Skin.of("value", null));
        });

        assertEquals(2, fetches.get(), "an expired skin is fetched again");
    }

    @Test
    void concurrentLookupsShareOneFetch() {
        MojangSkinService service = new MojangSkinService();
        var cache = new ConcurrentHashMap<String, MojangSkinService.Cached>();
        AtomicInteger fetches = new AtomicInteger();
        CompletableFuture<Skin> slow = new CompletableFuture<>();
        CompletableFuture<Skin> first = service.lookup(cache, "steve", key -> {
            fetches.incrementAndGet();
            return slow;
        });
        CompletableFuture<Skin> second = service.lookup(cache, "steve", key -> {
            fetches.incrementAndGet();
            return CompletableFuture.completedFuture(Skin.of("other", null));
        });
        slow.complete(Skin.of("value", null));
        assertEquals(1, fetches.get(), "a skin already on its way is not requested twice");
        assertEquals("value", first.join().value());
        assertEquals("value", second.join().value());
    }

    @Test
    void aThrowingFetchIsReportedAndForgottenWhenThereIsNoCooldown() {
        MojangSkinService service = new MojangSkinService();
        service.failureTtl(Duration.ZERO);
        var cache = new ConcurrentHashMap<String, MojangSkinService.Cached>();
        CompletableFuture<Skin> result = service.lookup(cache, "steve", key -> {
            throw new IllegalStateException("broken");
        });
        assertTrue(result.isCompletedExceptionally());
        assertTrue(cache.isEmpty());
    }

    @Test
    void parsesMineskinResponse() {
        String json = "{\"data\":{\"texture\":{\"value\":\"V\",\"signature\":\"S\"}}}";
        Skin skin = MojangSkinService.parseMineskin(json);
        assertEquals("V", skin.value());
        assertEquals("S", skin.signature());
    }

    @Test
    void throwsWhenNoTextures() {
        String json = "{\"properties\":[{\"name\":\"other\",\"value\":\"V\"}]}";
        assertThrows(IllegalStateException.class, () -> MojangSkinService.parseSkin(json));
    }
    private java.net.http.HttpResponse<String> response(int status, String body, String retry) {
        @SuppressWarnings("unchecked")
        java.net.http.HttpResponse<String> response = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        org.mockito.Mockito.when(response.statusCode()).thenReturn(status);
        org.mockito.Mockito.when(response.body()).thenReturn(body);
        org.mockito.Mockito.when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
                retry == null ? java.util.Map.of() : java.util.Map.of("Retry-After", java.util.List.of(retry)), (key, value) -> true));
        return response;
    }

    @Test
    void typedOutcomesPreserveRateLimitAndFallbackWithoutRepeatingHttp() {
        AtomicInteger calls = new AtomicInteger();
        MojangSkinService service = new MojangSkinService(request -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(response(429, "", "60"));
        });
        service.failureTtl(Duration.ZERO);
        UUID id = UUID.randomUUID();
        for (int attempt = 0; attempt < 3; attempt++) {
            var result = service.result(service.byId(id)).join();
            assertEquals(net.folianpc.api.SkinFetchResult.Status.RATE_LIMITED, result.status());
            assertEquals(Duration.ofSeconds(60), result.retryAfter());
            Skin fallback = Skin.of("fallback", null);
            assertEquals(fallback, result.skinOr(fallback));
        }
        assertEquals(1, calls.get());
        service.close();
    }

    @Test
    void httpStatusesAndMalformedBodiesHaveDistinctOutcomes() {
        var statuses = java.util.Map.of(404, net.folianpc.api.SkinFetchResult.Status.NOT_FOUND,
                204, net.folianpc.api.SkinFetchResult.Status.NOT_FOUND,
                503, net.folianpc.api.SkinFetchResult.Status.UNAVAILABLE,
                200, net.folianpc.api.SkinFetchResult.Status.MALFORMED);
        statuses.forEach((status, expected) -> {
            MojangSkinService service = new MojangSkinService(request ->
                    CompletableFuture.completedFuture(response(status, "bad-json", null)));
            assertEquals(expected, service.result(service.byId(UUID.randomUUID())).join().status());
            service.close();
        });
    }

    @Test
    void requestConcurrencyAndQueueCapacityAreBoundedAndShutdownCompletesAllObservers() {
        AtomicInteger started = new AtomicInteger();
        java.util.List<CompletableFuture<java.net.http.HttpResponse<String>>> transport = new java.util.ArrayList<>();
        MojangSkinService service = new MojangSkinService(request -> {
            started.incrementAndGet();
            var pending = new CompletableFuture<java.net.http.HttpResponse<String>>();
            transport.add(pending);
            return pending;
        });
        service.limits(100, 2, 2);
        java.util.List<CompletableFuture<Skin>> observers = new java.util.ArrayList<>();
        for (int index = 0; index < 4; index++) observers.add(service.byId(UUID.randomUUID()));
        assertEquals(2, started.get());
        assertEquals(net.folianpc.api.SkinFetchResult.Status.BUSY,
                service.result(service.byId(UUID.randomUUID())).join().status());
        transport.getFirst().complete(response(200, "{\"properties\":[{\"name\":\"textures\",\"value\":\"V\"}]}", null));
        assertEquals(3, started.get());
        assertEquals("V", observers.getFirst().join().value());
        service.close();
        for (int index = 1; index < observers.size(); index++) {
            assertEquals(net.folianpc.api.SkinFetchResult.Status.SHUTDOWN, service.result(observers.get(index)).join().status());
        }
        assertEquals(net.folianpc.api.SkinFetchResult.Status.SHUTDOWN,
                service.result(service.byId(UUID.randomUUID())).join().status());
    }

    @Test
    void inflightCacheEntriesRemainSharedAndCallerCancellationDoesNotPoisonThem() {
        MojangSkinService service = new MojangSkinService();
        service.ttl(Duration.ZERO);
        service.limits(2, 1, 1);
        var cache = new ConcurrentHashMap<String, MojangSkinService.Cached>();
        CompletableFuture<Skin> source = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        var observer = service.lookup(cache, "a", key -> { calls.incrementAndGet(); return source; });
        observer.cancel(false);
        var other = service.lookup(cache, "a", key -> { calls.incrementAndGet(); return source; });
        assertEquals(1, calls.get());
        source.complete(Skin.of("V", null));
        assertEquals("V", other.join().value());
        for (int index = 0; index < 30; index++) {
            service.lookup(cache, "entry-" + index, key -> CompletableFuture.completedFuture(Skin.of("V", null))).join();
            assertTrue(cache.size() <= 2);
        }
        service.close();
    }

    @Test
    void immediatelyCompletedQueuedRequestsDrainWithoutRecursiveStackGrowth() {
        AtomicInteger started = new AtomicInteger();
        CompletableFuture<java.net.http.HttpResponse<String>> first = new CompletableFuture<>();
        var successful = response(200, "{\"properties\":[{\"name\":\"textures\",\"value\":\"V\"}]}", null);
        MojangSkinService service = new MojangSkinService(request -> started.incrementAndGet() == 1
                ? first : CompletableFuture.completedFuture(successful));
        service.limits(4096, 1, 2048);
        java.util.List<CompletableFuture<Skin>> pending = new java.util.ArrayList<>();
        for (int index = 0; index < 2000; index++) pending.add(service.byId(UUID.randomUUID()));
        assertEquals(1, started.get());
        first.complete(successful);
        assertEquals(2000, started.get());
        assertTrue(pending.stream().allMatch(CompletableFuture::isDone));
        assertTrue(pending.stream().noneMatch(CompletableFuture::isCompletedExceptionally));
        service.close();
    }

}
