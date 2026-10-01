package net.foliacommons.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

final class CancellableTask implements TaskHandle {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    Runnable guard(Runnable task) {
        return () -> {
            if (!cancelled.get()) {
                task.run();
            }
        };
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }
}
