package net.foliacommons.scheduler;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginLifecycleTest {
    private final Plugin plugin = mock(Plugin.class);
    private final Server server = mock(Server.class);
    private final PluginManager manager = mock(PluginManager.class);
    private final GlobalRegionScheduler global = mock(GlobalRegionScheduler.class);
    private final RegionScheduler region = mock(RegionScheduler.class);
    private final EntityScheduler entityScheduler = mock(EntityScheduler.class);
    private final Entity entity = mock(Entity.class);
    private final World world = mock(World.class);
    private final Location location = new Location(world, 1, 64, 2);

    private Scheduler scheduler() {
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(entity.getScheduler()).thenReturn(entityScheduler);
        return Scheduler.forPlugin(plugin);
    }

    private void disable() {
        ArgumentCaptor<Listener> listener = ArgumentCaptor.forClass(Listener.class);
        verify(manager).registerEvents(listener.capture(), eq(plugin));
        when(plugin.isEnabled()).thenReturn(false);
        PluginDisableEvent unrelated = mock(PluginDisableEvent.class);
        when(unrelated.getPlugin()).thenReturn(mock(Plugin.class));
        ((PluginCalls) listener.getValue()).onDisable(unrelated);
        PluginDisableEvent event = mock(PluginDisableEvent.class);
        when(event.getPlugin()).thenReturn(plugin);
        ((PluginCalls) listener.getValue()).onDisable(event);
    }

    @Test
    void acceptedCallsAcrossSchedulerInstancesFailOnDisable() {
        Scheduler scheduler = scheduler();
        AtomicReference<Runnable> queued = new AtomicReference<>();
        doAnswer(invocation -> { queued.set(invocation.getArgument(1)); return null; })
                .when(global).execute(eq(plugin), any());
        when(entityScheduler.run(eq(plugin), any(), any())).thenReturn(mock(ScheduledTask.class));
        AtomicInteger ran = new AtomicInteger();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(global);
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(region);
            CompletableFuture<Integer> globalCall = scheduler.callGlobal(ran::incrementAndGet);
            CompletableFuture<Integer> entityCall = Scheduler.forPlugin(plugin).callForEntity(entity, ran::incrementAndGet);
            CompletableFuture<Integer> regionCall = scheduler.callForLocation(location, ran::incrementAndGet);
            assertFalse(globalCall.isDone());
            disable();
            assertTrue(globalCall.isCompletedExceptionally());
            assertTrue(entityCall.isCompletedExceptionally());
            assertTrue(regionCall.isCompletedExceptionally());
            queued.get().run();
            assertEquals(0, ran.get());
        }
    }

    @Test
    void successfulAndThrowingSuppliersKeepTheirResultsAfterDisable() {
        Scheduler scheduler = scheduler();
        doAnswer(invocation -> { invocation.<Runnable>getArgument(1).run(); return null; })
                .when(global).execute(eq(plugin), any());
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(global);
            CompletableFuture<String> result = scheduler.callGlobal(() -> "done");
            CompletableFuture<String> failure = scheduler.callGlobal(() -> { throw new IllegalArgumentException("bad"); });
            assertEquals("done", result.join());
            assertInstanceOf(IllegalArgumentException.class,
                    assertThrows(java.util.concurrent.CompletionException.class, failure::join).getCause());
            disable();
            assertEquals("done", result.join());
        }
    }

    @Test
    void retirementCancellationAndRejectedDispatchNeverRunTheSupplier() {
        Scheduler scheduler = scheduler();
        AtomicReference<Runnable> retirement = new AtomicReference<>();
        AtomicReference<Consumer<ScheduledTask>> queued = new AtomicReference<>();
        when(entityScheduler.run(eq(plugin), any(), any())).thenAnswer(invocation -> {
            retirement.set(invocation.getArgument(2));
            queued.set(invocation.getArgument(1));
            return mock(ScheduledTask.class);
        });
        CompletableFuture<Integer> retired = scheduler.callForEntity(entity, () -> { throw new AssertionError(); });
        retirement.get().run();
        assertTrue(retired.isCompletedExceptionally());
        CompletableFuture<Integer> cancelled = scheduler.callForEntity(entity, () -> { throw new AssertionError(); });
        cancelled.cancel(false);
        queued.get().accept(mock(ScheduledTask.class));
        when(entityScheduler.run(eq(plugin), any(), any())).thenReturn(null);
        assertTrue(scheduler.callForEntity(entity, () -> 1).isCompletedExceptionally());
        disable();
        assertTrue(scheduler.callGlobal(() -> 1).isCompletedExceptionally());
    }

    @Test
    void dispatchAndRegistrationFailuresAreFutureFailures() {
        Scheduler scheduler = scheduler();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(global);
            doThrow(new IllegalPluginAccessException("disabled")).when(global).execute(eq(plugin), any());
            assertTrue(scheduler.callGlobal(() -> 1).isCompletedExceptionally());
            disable();
        }
        Plugin failing = mock(Plugin.class);
        when(failing.isEnabled()).thenReturn(true);
        when(failing.getServer()).thenReturn(server);
        doThrow(new IllegalPluginAccessException("registration")).when(manager).registerEvents(any(), eq(failing));
        assertTrue(Scheduler.forPlugin(failing).callGlobal(() -> 1).isCompletedExceptionally());
    }

    @Test
    void immediateExecutionChecksOwnerEnablement() {
        Scheduler scheduler = scheduler();
        when(server.isOwnedByCurrentRegion(entity)).thenReturn(true);
        AtomicInteger calls = new AtomicInteger();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            assertTrue(scheduler.ensureForEntity(entity, calls::incrementAndGet, null));
            when(plugin.isEnabled()).thenReturn(false);
            assertFalse(scheduler.ensureForEntity(entity, calls::incrementAndGet, null));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void delayedHandlesCancelActualTasksAndHandleRejectedScheduling() {
        Scheduler scheduler = scheduler();
        ScheduledTask scheduled = mock(ScheduledTask.class);
        AsyncScheduler async = mock(AsyncScheduler.class);
        when(entityScheduler.runDelayed(eq(plugin), any(), any(), anyLong())).thenReturn(scheduled);
        when(global.runDelayed(eq(plugin), any(), anyLong())).thenReturn(scheduled);
        when(async.runDelayed(eq(plugin), any(), anyLong(), any())).thenReturn(scheduled);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(global);
            bukkit.when(Bukkit::getAsyncScheduler).thenReturn(async);
            TaskHandle handle = scheduler.scheduleForEntityLater(entity, () -> {}, null, 0);
            handle.cancel();
            verify(scheduled).cancel();
            when(scheduled.isCancelled()).thenReturn(true);
            assertTrue(handle.isCancelled());
            assertFalse(scheduler.scheduleGlobalLater(() -> {}, 1) == TaskHandle.NOOP);
            assertFalse(scheduler.scheduleAsyncLater(() -> {}, Duration.ZERO) == TaskHandle.NOOP);
            when(entityScheduler.runDelayed(eq(plugin), any(), any(), anyLong())).thenReturn(null);
            assertSame(TaskHandle.NOOP, scheduler.scheduleForEntityLater(entity, () -> {}, null, 1));
            when(global.runDelayed(eq(plugin), any(), anyLong())).thenThrow(new IllegalPluginAccessException("disabled"));
            when(async.runDelayed(eq(plugin), any(), anyLong(), any())).thenThrow(new IllegalPluginAccessException("disabled"));
            when(entityScheduler.runDelayed(eq(plugin), any(), any(), anyLong())).thenThrow(new IllegalPluginAccessException("disabled"));
            assertSame(TaskHandle.NOOP, scheduler.scheduleGlobalLater(() -> {}, 1));
            assertSame(TaskHandle.NOOP, scheduler.scheduleAsyncLater(() -> {}, Duration.ZERO));
            assertSame(TaskHandle.NOOP, scheduler.scheduleForEntityLater(entity, () -> {}, null, 1));
            when(plugin.isEnabled()).thenReturn(false);
            assertSame(TaskHandle.NOOP, scheduler.scheduleGlobalLater(() -> {}, 1));
            assertSame(TaskHandle.NOOP, scheduler.scheduleAsyncLater(() -> {}, Duration.ZERO));
            assertSame(TaskHandle.NOOP, scheduler.scheduleForEntityLater(entity, () -> {}, null, 1));
        }
    }

    @Test
    void locationInputsAreValidatedAndCopiedBeforeDispatch() {
        Scheduler scheduler = scheduler();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(region);
            assertTrue(scheduler.runForLocation(location, () -> {}));
            ArgumentCaptor<Location> copied = ArgumentCaptor.forClass(Location.class);
            verify(region).execute(eq(plugin), copied.capture(), any());
            location.setX(100);
            assertEquals(1, copied.getValue().getX());
            assertThrows(NullPointerException.class, () -> scheduler.runForLocation(new Location(null, 0, 0, 0), () -> {}));
            doThrow(new IllegalPluginAccessException("disabled")).when(region).execute(eq(plugin), any(Location.class), any());
            assertFalse(scheduler.runForLocation(location, () -> {}));
            when(plugin.isEnabled()).thenReturn(false);
            assertFalse(scheduler.runForLocation(location, () -> {}));
            assertThrows(NullPointerException.class, () -> scheduler.runGlobal(null));
        }
    }
}
