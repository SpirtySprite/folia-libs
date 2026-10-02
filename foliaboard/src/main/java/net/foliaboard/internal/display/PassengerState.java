package net.foliaboard.internal.display;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class PassengerState {
    private final Map<Integer, int[]> observed = new HashMap<>();

    int[] observe(int vehicle, int[] actual, Set<Integer> owned) {
        int[] external = Arrays.stream(actual).filter(id -> !owned.contains(id)).toArray();
        observed.put(vehicle, external);
        return external;
    }

    void seed(int vehicle, int[] sampled) {
        observed.putIfAbsent(vehicle, sampled.clone());
    }

    int[] get(int vehicle) {
        return observed.getOrDefault(vehicle, new int[0]);
    }

    void remove(int vehicle) {
        observed.remove(vehicle);
    }

    void clear() {
        observed.clear();
    }

    Map<Integer, int[]> snapshot() {
        return new HashMap<>(observed);
    }

    void restore(Map<Integer, int[]> snapshot) {
        observed.clear();
        observed.putAll(snapshot);
    }
}
