package net.folianpc.internal;

import net.foliacommons.scheduler.TaskGroup;
import net.folianpc.api.MovementResult;
import net.folianpc.api.MovementTask;

import java.util.concurrent.CompletableFuture;

final class MovementRequest implements MovementTask {
    final long generation;
    final NpcImpl npc;
    final TaskGroup work = new TaskGroup();
    private final CompletableFuture<MovementResult> route = new CompletableFuture<>();
    private final CompletableFuture<MovementResult> result = new CompletableFuture<>();

    MovementRequest(long generation, NpcImpl npc) {
        this.generation = generation;
        this.npc = npc;
    }

    void ready() {
        route.complete(MovementResult.of(MovementResult.Status.ROUTE_FOUND));
    }

    void finish(MovementResult outcome) {
        if (result.complete(outcome)) {
            route.complete(outcome);
            work.cancel();
        }
    }

    @Override public CompletableFuture<MovementResult> route() { return route.copy(); }
    @Override public CompletableFuture<MovementResult> result() { return result.copy(); }
    @Override public boolean isCancelled() { return result.isDone(); }
    @Override public void cancel() { npc.cancelMovement(this, MovementResult.Status.CANCELLED); }
}
