package net.foliaboard.api.animation;

import org.jetbrains.annotations.ApiStatus;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

/** Controllable monotonic playback. Controls are thread-safe; the sample function must support its calling threads. */
@ApiStatus.Experimental
public final class AnimationTimeline<T> implements Animation<T> {
    private final LongFunction<T> sample;
    private final LongSupplier clock;
    private long anchor;
    private long position;
    private boolean paused;

    /** Starts playback at zero using System.nanoTime and a sample function receiving elapsed nanoseconds. */
    public AnimationTimeline(LongFunction<T> sample) {
        this(sample, System::nanoTime);
    }

    /** Starts playback using an injectable monotonic nanosecond clock for synchronized playback or tests. */
    public AnimationTimeline(LongFunction<T> sample, LongSupplier clock) {
        this.sample = Objects.requireNonNull(sample, "sample");
        this.clock = Objects.requireNonNull(clock, "clock");
        anchor = clock.getAsLong();
    }

    /** Samples the current playback position without holding a control lock during the callback. */
    @Override
    public T current() {
        return sample.apply(elapsed().toNanos());
    }

    /** Current non-negative playback position. */
    public synchronized Duration elapsed() {
        return Duration.ofNanos(elapsedNanos());
    }

    /** Freezes the current position. Repeated pauses have no effect. */
    public synchronized void pause() {
        position = elapsedNanos();
        paused = true;
    }

    /** Resumes from the paused position without counting the paused duration. */
    public synchronized void resume() {
        if (paused) {
            anchor = clock.getAsLong();
            paused = false;
        }
    }

    /** Whether playback is paused. */
    public synchronized boolean paused() {
        return paused;
    }

    /** Restarts at zero and resumes playback. */
    public synchronized void restart() {
        position = 0;
        anchor = clock.getAsLong();
        paused = false;
    }

    /** Seeks to a non-negative duration fitting in nanoseconds, retaining the paused state. */
    public synchronized void seek(Duration elapsed) {
        long nanos = Objects.requireNonNull(elapsed, "elapsed").toNanos();
        if (nanos < 0) {
            throw new IllegalArgumentException("Playback position must be non-negative");
        }
        position = nanos;
        anchor = clock.getAsLong();
    }

    /** Applies a signed offset. Rejects overflow or a resulting negative position. */
    public synchronized void offset(Duration offset) {
        seek(Duration.ofNanos(Math.addExact(elapsedNanos(), Objects.requireNonNull(offset, "offset").toNanos())));
    }

    /** Copies another timeline's position and pause state without acquiring both control locks. */
    public void synchronizeWith(AnimationTimeline<?> other) {
        Playback playback = Objects.requireNonNull(other, "other").playback();
        synchronized (this) {
            seek(Duration.ofNanos(playback.nanos()));
            paused = playback.paused();
        }
    }

    private synchronized Playback playback() {
        return new Playback(elapsedNanos(), paused);
    }

    private long elapsedNanos() {
        return paused ? position : Math.addExact(position, Math.max(0, clock.getAsLong() - anchor));
    }

    private record Playback(long nanos, boolean paused) {
    }
}
