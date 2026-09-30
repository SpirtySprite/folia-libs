package net.foliacommons.scheduler;

/** A task could not be run because it could not be scheduled. */
public final class SchedulingException extends RuntimeException {

    public SchedulingException(String message) {
        super(message);
    }
}
