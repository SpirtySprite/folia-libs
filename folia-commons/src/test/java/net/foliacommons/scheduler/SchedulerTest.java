package net.foliacommons.scheduler;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulerTest {

    private static Plugin plugin(boolean enabled) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(enabled);
        return plugin;
    }

    private static Entity entityWith(EntityScheduler scheduler) {
        Entity entity = mock(Entity.class);
        when(entity.getScheduler()).thenReturn(scheduler);
        return entity;
    }

    @Test
    void noopHandleIsAlreadyCancelled() {
        assertTrue(TaskHandle.NOOP.isCancelled());
        TaskHandle.NOOP.cancel();
    }

    @Test
    void synchronousSchedulerRunsOneShotTasksImmediately() {
        Scheduler scheduler = Scheduler.synchronous();
        AtomicInteger ran = new AtomicInteger();
        Entity entity = mock(Entity.class);

        assertTrue(scheduler.runForEntity(entity, ran::incrementAndGet, null));
        assertTrue(scheduler.runForEntityLater(entity, ran::incrementAndGet, null, 5));
        assertTrue(scheduler.runGlobal(ran::incrementAndGet));
        assertTrue(scheduler.runAsync(ran::incrementAndGet));
        assertTrue(scheduler.runAsyncLater(ran::incrementAndGet, Duration.ofSeconds(1)));

        assertEquals(5, ran.get());
        assertFalse(scheduler.isFolia());
    }

    @Test
    void synchronousSchedulerIgnoresTimers() {
        AtomicInteger ran = new AtomicInteger();

        TaskHandle entityTimer = Scheduler.synchronous().runForEntityTimer(mock(Entity.class), ran::incrementAndGet,
                null, 1, 1);
        TaskHandle globalTimer = Scheduler.synchronous().runGlobalTimer(ran::incrementAndGet, 1, 1);

        assertEquals(0, ran.get());
        assertSame(TaskHandle.NOOP, entityTimer);
        assertSame(TaskHandle.NOOP, globalTimer);
    }

    @Test
    void aDisabledPluginSchedulesNothing() {
        Scheduler scheduler = Scheduler.forPlugin(plugin(false));
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        Entity entity = entityWith(entityScheduler);
        AtomicInteger ran = new AtomicInteger();

        assertFalse(scheduler.runForEntity(entity, ran::incrementAndGet, null));
        assertFalse(scheduler.runForEntityLater(entity, ran::incrementAndGet, null, 1));
        assertSame(TaskHandle.NOOP, scheduler.runForEntityTimer(entity, ran::incrementAndGet, null, 1, 1));
        assertSame(TaskHandle.NOOP, scheduler.runGlobalTimer(ran::incrementAndGet, 1, 1));
        assertFalse(scheduler.runGlobal(ran::incrementAndGet));
        assertFalse(scheduler.runAsync(ran::incrementAndGet));
        assertFalse(scheduler.runAsyncLater(ran::incrementAndGet, Duration.ofMillis(5)));

        assertEquals(0, ran.get());
        verify(entity, never()).getScheduler();
    }

    @Test
    void runForEntityUsesTheEntitysOwnScheduler() {
        Plugin plugin = plugin(true);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(entityScheduler.run(eq(plugin), any(), any())).thenReturn(mock(ScheduledTask.class));

        boolean scheduled = Scheduler.forPlugin(plugin).runForEntity(entityWith(entityScheduler), () -> {
        }, null);

        assertTrue(scheduled);
        verify(entityScheduler).run(eq(plugin), any(), any());
    }

    @Test
    void runForEntityReportsFalseWhenTheEntityIsAlreadyGone() {
        Plugin plugin = plugin(true);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(entityScheduler.run(eq(plugin), any(), any())).thenReturn(null);

        assertFalse(Scheduler.forPlugin(plugin).runForEntity(entityWith(entityScheduler), () -> {
        }, () -> {
        }));
    }

    @Test
    void aPluginDisabledWhileSchedulingIsNotAnError() {
        Plugin plugin = plugin(true);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(entityScheduler.run(eq(plugin), any(), any())).thenThrow(new IllegalPluginAccessException("disabled"));
        when(entityScheduler.runDelayed(eq(plugin), any(), any(), anyLong()))
                .thenThrow(new IllegalPluginAccessException("disabled"));
        when(entityScheduler.runAtFixedRate(eq(plugin), any(), any(), anyLong(), anyLong()))
                .thenThrow(new IllegalPluginAccessException("disabled"));
        Scheduler scheduler = Scheduler.forPlugin(plugin);
        Entity entity = entityWith(entityScheduler);

        assertFalse(scheduler.runForEntity(entity, () -> {
        }, null));
        assertFalse(scheduler.runForEntityLater(entity, () -> {
        }, null, 1));
        assertSame(TaskHandle.NOOP, scheduler.runForEntityTimer(entity, () -> {
        }, null, 1, 1));
    }

    @Test
    void delaysAndPeriodsAreClampedToAtLeastOneTick() {
        Plugin plugin = plugin(true);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(entityScheduler.runDelayed(eq(plugin), any(), any(), anyLong())).thenReturn(mock(ScheduledTask.class));
        when(entityScheduler.runAtFixedRate(eq(plugin), any(), any(), anyLong(), anyLong()))
                .thenReturn(mock(ScheduledTask.class));
        Scheduler scheduler = Scheduler.forPlugin(plugin);
        Entity entity = entityWith(entityScheduler);

        scheduler.runForEntityLater(entity, () -> {
        }, null, 0);
        scheduler.runForEntityTimer(entity, () -> {
        }, null, -5, 0);

        verify(entityScheduler).runDelayed(eq(plugin), any(), any(), eq(1L));
        verify(entityScheduler).runAtFixedRate(eq(plugin), any(), any(), eq(1L), eq(1L));
    }

    @Test
    void aTimerHandleCancelsTheUnderlyingTask() {
        Plugin plugin = plugin(true);
        ScheduledTask task = mock(ScheduledTask.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(entityScheduler.runAtFixedRate(eq(plugin), any(), any(), anyLong(), anyLong())).thenReturn(task);

        TaskHandle handle = Scheduler.forPlugin(plugin).runForEntityTimer(entityWith(entityScheduler), () -> {
        }, null, 1, 1);
        handle.cancel();

        verify(task).cancel();
    }

    @Test
    void globalAndAsyncWorkGoThroughThePaperSchedulers() {
        Plugin plugin = plugin(true);
        GlobalRegionScheduler global = mock(GlobalRegionScheduler.class);
        AsyncScheduler async = mock(AsyncScheduler.class);
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(global);
            bukkit.when(Bukkit::getAsyncScheduler).thenReturn(async);
            Scheduler scheduler = Scheduler.forPlugin(plugin);
            Runnable task = () -> {
            };

            assertTrue(scheduler.runGlobal(task));
            assertTrue(scheduler.runAsync(task));
            assertTrue(scheduler.runAsyncLater(task, Duration.ZERO));

            verify(global).execute(plugin, task);
            verify(async).runNow(eq(plugin), any());
            verify(async).runDelayed(eq(plugin), any(), eq(1L), eq(TimeUnit.MILLISECONDS));
        }
    }
}
