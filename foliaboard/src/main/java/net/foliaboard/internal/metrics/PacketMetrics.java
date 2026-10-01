package net.foliaboard.internal.metrics;

import java.util.concurrent.atomic.LongAdder;
import java.util.EnumMap;
import net.foliaboard.api.PresentationStats;

public final class PacketMetrics {
    private final LongAdder total = new LongAdder();
    private final LongAdder refreshes = new LongAdder();
    private final EnumMap<PresentationStats.Surface, LongAdder> requests = new EnumMap<>(PresentationStats.Surface.class);
    private final EnumMap<PresentationStats.Surface, LongAdder> changes = new EnumMap<>(PresentationStats.Surface.class);

    public PacketMetrics() {
        for (PresentationStats.Surface surface : PresentationStats.Surface.values()) {
            requests.put(surface, new LongAdder());
            changes.put(surface, new LongAdder());
        }
    }

    public void requested(PresentationStats.Surface surface) {
        requests.get(surface).increment();
    }

    public void changed(PresentationStats.Surface surface) {
        changes.get(surface).increment();
    }

    public PresentationStats snapshot() {
        EnumMap<PresentationStats.Surface, PresentationStats.Counters> snapshot = new EnumMap<>(PresentationStats.Surface.class);
        requests.forEach((surface, count) -> snapshot.put(surface,
                new PresentationStats.Counters(count.sum(), changes.get(surface).sum())));
        return new PresentationStats(snapshot);
    }

    public void sent() {
        total.increment();
    }

    public void refresh() {
        refreshes.increment();
    }

    public long totalPackets() {
        return total.sum();
    }

    public long refreshCount() {
        return refreshes.sum();
    }
}
