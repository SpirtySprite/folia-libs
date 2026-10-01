package com.foliagui.gui;

import com.foliagui.FoliaGUIService;
import com.foliagui.scheduler.TaskHandle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** One managed input attempt with an exactly-once terminal result and explicit cancellation. */
@ApiStatus.Experimental
public final class InputSession implements TaskHandle, AutoCloseable {
    final FoliaGUIService service;
    final Player player;
    final CompletableFuture<InputResult> completion = new CompletableFuture<>();
    private final AtomicBoolean finished = new AtomicBoolean();
    volatile TaskHandle timeout;
    volatile Runnable cleanup = () -> { };

    InputSession(FoliaGUIService service, Player player) {
        this.service = service;
        this.player = player;
    }

    /** Completes on the player's thread when scheduling is available; retirement and shutdown also terminate it. */
    public CompletableFuture<InputResult> result() {
        return completion.copy();
    }

    void finish(InputResult result) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        service.sessions().forgetInput(this);
        TaskHandle timer = timeout;
        if (timer != null) {
            timer.cancel();
        }
        try {
            cleanup.run();
        } finally {
            completion.complete(result);
        }
    }

    /** Cancels the prompt without waiting or blocking a server thread. */
    @Override
    public void cancel() {
        service.scheduler().runForEntity(player, () -> finish(InputResult.ended(InputResult.Status.CANCELLED)),
                () -> finish(InputResult.ended(InputResult.Status.DISCONNECTED)));
    }

    /** True after any terminal result, including successful submission. */
    @Override
    public boolean isCancelled() {
        return finished.get();
    }

    /** Cancels this prompt. */
    @Override
    public void close() {
        cancel();
    }
}
