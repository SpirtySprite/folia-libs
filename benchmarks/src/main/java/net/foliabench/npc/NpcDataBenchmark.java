package net.foliabench.npc;

import net.folianpc.api.NpcData;
import net.folianpc.api.Skin;
import org.bukkit.entity.EntityType;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Saving and loading NPC snapshots, done for every NPC on startup and shutdown. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class NpcDataBenchmark {
    private NpcData data;
    private Map<String, Object> serialized;

    @Setup
    public void setUp() {
        data = NpcData.builder()
                .id(UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6"))
                .name("Shopkeeper")
                .type(EntityType.PLAYER)
                .position("world", 10.5, 64, -3.25, 90f, -15f)
                .lookAtPlayers(true)
                .skin(Skin.of("A".repeat(400), "B".repeat(684)))
                .nametag("<gold>Shop", "<gray>Right-click")
                .build();
        serialized = data.serialize();
        if (!NpcData.deserialize(serialized).equals(data)) {
            throw new IllegalStateException("the snapshot must survive a round trip");
        }
    }

    @Benchmark
    public NpcData buildWithBuilder() {
        return data.toBuilder().name("Banker").build();
    }

    @Benchmark
    public Map<String, Object> serialize() {
        return data.serialize();
    }

    @Benchmark
    public NpcData deserialize() {
        return NpcData.deserialize(serialized);
    }
}
