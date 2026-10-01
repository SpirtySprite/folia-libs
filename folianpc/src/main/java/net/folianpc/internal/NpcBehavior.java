package net.folianpc.internal;

import net.foliacommons.scheduler.TaskGroup;
import net.folianpc.api.BehaviorTask;
import net.folianpc.api.FollowOptions;
import net.folianpc.api.MovementResult;
import net.folianpc.api.MovementTask;
import net.folianpc.api.PatrolOptions;
import org.bukkit.Location;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class NpcBehavior implements BehaviorTask {
    private final NpcImpl npc;
    private final NpcManager manager;
    private final List<Location> waypoints;
    private final PatrolOptions patrol;
    private final UUID target;
    private final FollowOptions follow;
    private final TaskGroup work = new TaskGroup();
    private final CompletableFuture<MovementResult> result = new CompletableFuture<>();
    private MovementTask movement;
    private int waypoint;
    private long ticksUntilNext;
    private boolean resolving;
    private Location lastTarget;

    NpcBehavior(NpcImpl npc, NpcManager manager, List<Location> waypoints, PatrolOptions patrol,
                UUID target, FollowOptions follow) {
        this.npc = npc;
        this.manager = manager;
        this.waypoints = waypoints;
        this.patrol = patrol;
        this.target = target;
        this.follow = follow;
    }

    void tick() {
        if (result.isDone()) return;
        if (ticksUntilNext > 0) { ticksUntilNext = Math.max(0, ticksUntilNext - 2); return; }
        if (follow != null) {
            if (resolving) return;
            resolving = true;
            ticksUntilNext = follow.repathTicks();
            var location = manager.followDestination(target);
            work.add(location);
            location.whenComplete((destination, error) -> {
                synchronized (npc) {
                    resolving = false;
                    if (result.isDone()) return;
                    if (error != null || destination == null) { finish(MovementResult.of(MovementResult.Status.CANCELLED)); return; }
                    Position pos = npc.position();
                    if (destination.getWorld() == null || !destination.getWorld().getName().equals(pos.world())) {
                        finish(MovementResult.of(MovementResult.Status.UNREACHABLE)); return;
                    }
                    double distance = pos.distanceSquared(destination.getX(), destination.getY(), destination.getZ());
                    if (distance > follow.maxDistance() * follow.maxDistance()) {
                        finish(MovementResult.of(MovementResult.Status.UNREACHABLE)); return;
                    }
                    if (distance <= follow.minDistance() * follow.minDistance()) {
                        stopMovement();
                        lastTarget = null;
                    } else if (lastTarget == null || !destination.getWorld().equals(lastTarget.getWorld())
                            || destination.distanceSquared(lastTarget) >= Math.max(0.25, follow.minDistance() * follow.minDistance())
                            || movement == null || movement.result().isDone()) {
                        lastTarget = destination.clone();
                        start(destination, follow.speed(), follow.navigation());
                    }
                }
            });
        } else if (movement == null) {
            if (waypoint >= waypoints.size()) {
                if (!patrol.repeat()) { finish(MovementResult.of(MovementResult.Status.ARRIVED)); return; }
                waypoint = 0;
            }
            start(waypoints.get(waypoint), patrol.speed(), patrol.navigation());
        }
    }

    private void start(Location destination, double speed, net.folianpc.api.NavigationOptions options) {
        stopMovement();
        MovementTask travel = manager.navigate(npc, destination, speed, options);
        movement = travel;
        travel.result().whenComplete((outcome, error) -> {
            synchronized (npc) {
                if (result.isDone() || movement != travel) return;
                movement = null;
                if (error != null) finish(MovementResult.failed(error));
                else if (outcome.status() == MovementResult.Status.ARRIVED) {
                    if (patrol != null) { waypoint++; ticksUntilNext = patrol.waitTicks(); }
                } else finish(outcome);
            }
        });
    }

    private void stopMovement() {
        MovementTask ending = movement;
        movement = null;
        if (ending != null) ending.cancel();
    }

    void finish(MovementResult outcome) {
        if (result.isDone()) return;
        npc.clearBehavior(this);
        stopMovement();
        result.complete(outcome);
        work.cancel();
    }

    @Override public CompletableFuture<MovementResult> result() { return result.copy(); }
    @Override public boolean isCancelled() { return result.isDone(); }
    @Override public void cancel() { synchronized (npc) { finish(MovementResult.of(MovementResult.Status.CANCELLED)); } }
}
