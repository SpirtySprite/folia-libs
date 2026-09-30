package net.foliacommons.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A handle that can be given to a task before the task has been scheduled, so the task can cancel
 * itself. Cancelling before the real handle is known is remembered and applied as soon as it is.
 */
final class DeferredHandle implements TaskHandle {
    private final AtomicReference<TaskHandle> target = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    /** Connects the real handle. If {@link #cancel()} already happened, the real task is cancelled now. */
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
