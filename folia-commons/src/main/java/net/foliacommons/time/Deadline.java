package net.foliacommons.time;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * An immutable elapsed-time deadline for TTLs and cooldowns, independent of wall-clock changes.
 * Safe from any thread when its clock is thread-safe. It does not schedule or block work.
 */
@ApiStatus.Experimental
public final class Deadline {
    private final LongSupplier clock;
    private final long started;
    private final long budget;

    private Deadline(Duration duration, LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.budget = Objects.requireNonNull(duration, "duration").toNanos();
        if (budget < 0) {
            throw new IllegalArgumentException("duration must be non-negative");
        }
        this.started = clock.getAsLong();
    }

    /** Creates a deadline using {@link System#nanoTime()}, with a non-negative duration fitting in nanoseconds. */
    public static @NotNull Deadline after(@NotNull Duration duration) {
        return new Deadline(duration, System::nanoTime);
    }

    /** Creates a deadline using a monotonic nanosecond source, for example a virtual-time test scheduler. */
    public static @NotNull Deadline after(@NotNull Duration duration, @NotNull LongSupplier clock) {
        return new Deadline(duration, clock);
    }

    /** Whether the elapsed duration has reached the budget. Handles nanosecond counter wraparound. */
    public boolean expired() {
        return elapsed() >= budget;
    }

    /** Non-negative remaining time; zero after expiration. */
    public @NotNull Duration remaining() {
        long elapsed = elapsed();
        return Duration.ofNanos(elapsed >= budget ? 0 : budget - elapsed);
    }

    private long elapsed() {
        return Math.max(0, clock.getAsLong() - started);
    }
}
