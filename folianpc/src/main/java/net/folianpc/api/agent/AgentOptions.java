package net.folianpc.api.agent;

import net.folianpc.api.NavigationOptions;
import org.jetbrains.annotations.ApiStatus;
import java.util.Objects;

/** Immutable planning, execution and navigation budgets. All delays are game ticks; no operation blocks a region thread. */
@ApiStatus.Experimental
public record AgentOptions(int maxNodes, int maxPlanActions, int maxExecutedActions, int maxFailures,
                           long timeoutTicks, double speed, double reach, NavigationOptions navigation) {
    /** Rejects unbounded work, non-finite movement values and null navigation options. */
    public AgentOptions {
        Objects.requireNonNull(navigation, "navigation");
        if (maxNodes < 1 || maxNodes > 100_000 || maxPlanActions < 1 || maxPlanActions > 256
                || maxExecutedActions < 1 || maxExecutedActions > 4096 || maxFailures < 0 || maxFailures > 256
                || timeoutTicks < 1 || timeoutTicks > 72000 || !Double.isFinite(speed) || speed <= 0 || speed > 32
                || !Double.isFinite(reach) || reach <= 0 || reach > 8) throw new IllegalArgumentException("Invalid agent options");
    }

    /** Returns bounded defaults with hazard avoidance and ground-following navigation. */
    public static AgentOptions defaults() {
        return new AgentOptions(4000, 64, 256, 8, 6000, 4, 3,
                NavigationOptions.builder().groundFollowing(true).build());
    }
}
