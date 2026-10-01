package net.foliaboard.api.animation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationTimelineTest {
    @Test
    void pauseResumeSeekOffsetAndSynchronizationPreservePlayback() {
        AtomicLong clock = new AtomicLong(100);
        AnimationTimeline<Long> first = new AnimationTimeline<>(nanos -> nanos, clock::get);
        clock.addAndGet(10);
        assertEquals(10L, first.current());
        first.pause();
        first.pause();
        clock.addAndGet(100);
        assertEquals(10L, first.current());
        first.seek(Duration.ofNanos(30));
        first.offset(Duration.ofNanos(-5));
        assertEquals(25L, first.current());
        assertTrue(first.paused());
        AnimationTimeline<Long> second = new AnimationTimeline<>(nanos -> nanos, clock::get);
        second.synchronizeWith(first);
        assertEquals(first.elapsed(), second.elapsed());
        assertTrue(second.paused());
        first.resume();
        clock.addAndGet(7);
        assertEquals(32L, first.current());
        assertEquals(25L, second.current());
        first.restart();
        assertFalse(first.paused());
        assertEquals(Duration.ZERO, first.elapsed());
        assertThrows(IllegalArgumentException.class, () -> first.offset(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> first.seek(Duration.ofNanos(-1)));
    }

    @Test
    void frameTimelinesLoopAndValidateCadence() {
        AnimationTimeline<String> timeline = Animations.timeline(Duration.ofDays(1), List.of("one", "two"));
        timeline.pause();
        timeline.seek(Duration.ZERO);
        assertEquals("one", timeline.current());
        timeline.seek(Duration.ofDays(1));
        assertEquals("two", timeline.current());
        timeline.seek(Duration.ofDays(2));
        assertEquals("one", timeline.current());
        assertThrows(IllegalArgumentException.class, () -> Animations.timeline(Duration.ZERO, List.of("one")));
        assertThrows(IllegalArgumentException.class, () -> Animations.timeline(Duration.ofSeconds(1), List.of()));
    }
}
