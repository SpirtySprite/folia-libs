package net.foliacommons.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class DeferredHandle implements TaskHandle {
    private final AtomicReference<TaskHandle> target = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    void bind(TaskHandle real) {
        target.set(real);
        if (cancelled.get()) {
            real.cancel();
        }
    }

    @Override
    public void cancel() {
        cancelled.set(true);
        TaskHandle real = target.get();
        if (real != null) {
            real.cancel();
        }
    }

    @Override
    public boolean isCancelled() {
        if (cancelled.get()) {
            return true;
        }
        TaskHandle real = target.get();
        return real != null && real.isCancelled();
    }
}
