package net.foliacommons.time;

import net.foliacommons.scheduler.DeterministicScheduler;
import net.foliacommons.scheduler.Scheduler;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadlineTest {
    @Test
    void virtualTimeControlsTheRemainingBudgetExactly() {
        try (DeterministicScheduler scheduler = Scheduler.deterministic()) {
            Deadline deadline = Deadline.after(Duration.ofMillis(150), scheduler::nanoTime);
            assertFalse(deadline.expired());
            scheduler.advanceTicks(1);
            assertEquals(Duration.ofMillis(100), deadline.remaining());
            scheduler.advanceTicks(2);
            assertTrue(deadline.expired());
            scheduler.advanceTicks(10);
            assertEquals(Duration.ZERO, deadline.remaining());
        }
    }

    @Test
    void counterWraparoundDoesNotExtendTheDeadline() {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 5);
        Deadline deadline = Deadline.after(Duration.ofNanos(10), clock::get);
        clock.addAndGet(10);
        assertTrue(deadline.expired());
        assertEquals(Duration.ZERO, deadline.remaining());
    }

    @Test
    void zeroNegativeAndOverflowingDurationsHaveExplicitBehavior() {
        assertTrue(Deadline.after(Duration.ZERO).expired());
        assertThrows(IllegalArgumentException.class, () -> Deadline.after(Duration.ofNanos(-1)));
        assertThrows(ArithmeticException.class, () -> Deadline.after(Duration.ofDays(Long.MAX_VALUE)));
        assertThrows(NullPointerException.class, () -> Deadline.after(Duration.ZERO, null));
    }
}
