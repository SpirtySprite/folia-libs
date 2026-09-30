package net.foliacommons.scheduler;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepeatingTaskTest {

    @Test
    void theHandleGivenToTheTaskCancelsTheRealTimer() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        ScheduledTask real = mock(ScheduledTask.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(entityScheduler.runAtFixedRate(eq(plugin), any(), any(), anyLong(), anyLong())).thenReturn(real);
        Entity entity = mock(Entity.class);
        when(entity.getScheduler()).thenReturn(entityScheduler);
        AtomicInteger runs = new AtomicInteger();

        TaskHandle handle = Scheduler.forPlugin(plugin).repeatForEntity(entity, self -> {
            if (runs.incrementAndGet() == 2) {
                self.cancel();
            }
        }, null, 1, 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ScheduledTask>> body = ArgumentCaptor.forClass(Consumer.class);
        verify(entityScheduler).runAtFixedRate(eq(plugin), body.capture(), any(), anyLong(), anyLong());
        body.getValue().accept(real);
        verify(real, never()).cancel();
        body.getValue().accept(real);

        verify(real).cancel();
        assertEquals(2, runs.get());
        assertFalse(handle == TaskHandle.NOOP);
    }

    @Test
    void cancellingBeforeTheRealHandleExistsIsRemembered() {
        DeferredHandle handle = new DeferredHandle();
        TaskHandle real = mock(TaskHandle.class);

        handle.cancel();
        assertTrue(handle.isCancelled());
        handle.bind(real);

        verify(real).cancel();
    }

    @Test
    void anUnboundUncancelledHandleIsNotCancelled() {
        DeferredHandle handle = new DeferredHandle();

        assertFalse(handle.isCancelled());
    }

    @Test
    void aBoundHandleReportsTheRealStateAndForwardsCancel() {
        DeferredHandle handle = new DeferredHandle();
        TaskHandle real = mock(TaskHandle.class);
        handle.bind(real);

        assertFalse(handle.isCancelled());
        when(real.isCancelled()).thenReturn(true);
        assertTrue(handle.isCancelled());

        handle.cancel();
        verify(real).cancel();
    }

    @Test
    void withSynchronousSchedulingTheTimerNeverRuns() {
        AtomicInteger runs = new AtomicInteger();

        Scheduler.synchronous().repeatForEntity(mock(Entity.class), self -> runs.incrementAndGet(), null, 1, 1);

        assertEquals(0, runs.get());
    }
}
