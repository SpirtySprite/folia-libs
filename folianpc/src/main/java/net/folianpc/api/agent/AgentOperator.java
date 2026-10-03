package net.folianpc.api.agent;

import org.jetbrains.annotations.ApiStatus;
import java.util.Map;
import java.util.Objects;

/** A developer-defined planning action: minimum required facts, signed changes, positive cost and its asynchronous executor. */
@ApiStatus.Experimental
public record AgentOperator(String name, Map<String, Long> requires, Map<String, Long> changes,
                            double cost, AgentAction action) {
    /** Copies facts and rejects empty names, negative requirements, zero effects and non-finite or nonpositive cost. */
    public AgentOperator {
        Objects.requireNonNull(name, "name");
        requires = Map.copyOf(requires);
        changes = Map.copyOf(changes);
        Objects.requireNonNull(action, "action");
        if (name.isBlank() || !Double.isFinite(cost) || cost <= 0 || changes.isEmpty()
                || requires.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getValue() < 0)
                || changes.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getValue() == 0)) {
            throw new IllegalArgumentException("Invalid planning operator");
        }
    }
}
