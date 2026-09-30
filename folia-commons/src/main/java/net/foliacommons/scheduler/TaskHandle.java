package net.foliacommons.scheduler;

/** A scheduled task that can be cancelled. */
public interface TaskHandle {

    /** A handle for a task that was never scheduled, for example because the plugin is disabled. */
    TaskHandle NOOP = new TaskHandle() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };

    void cancel();

    boolean isCancelled();
}
