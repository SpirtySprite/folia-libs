package net.folianpc.api.agent;

import org.jetbrains.annotations.ApiStatus;
import java.util.List;
import java.util.Objects;

/** A bounded planning result. FOUND contains an executable action sequence; other statuses distinguish budget exhaustion and impossibility. */
@ApiStatus.Experimental
public record AgentPlan(Status status, List<AgentOperator> actions, int explored, double cost) {
    /** FOUND includes an already-satisfied goal with no actions. CANCELLED stops a superseded search. */
    public enum Status { FOUND, UNREACHABLE, BUDGET_EXHAUSTED, CANCELLED }
    /** Copies actions and validates nonnegative diagnostics. */
    public AgentPlan {
        Objects.requireNonNull(status, "status");
        actions = List.copyOf(actions);
        if (explored < 0 || !Double.isFinite(cost) || cost < 0) throw new IllegalArgumentException("Invalid plan diagnostics");
    }
}
