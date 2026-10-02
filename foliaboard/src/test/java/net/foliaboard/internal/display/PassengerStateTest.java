package net.foliaboard.internal.display;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class PassengerStateTest {
    @Test
    void firstMountUsesPacketsReceivedAfterTheOwnerSample() {
        var state = new PassengerState();
        int[] sampled = {10};
        state.observe(1, new int[]{10, 20}, Set.of());
        state.seed(1, sampled);
        assertArrayEquals(new int[]{10, 20, 30}, PassengerLists.merge(state.get(1), new int[]{30}));
        state.observe(1, new int[0], Set.of());
        state.seed(1, sampled);
        assertArrayEquals(new int[]{30}, PassengerLists.merge(state.get(1), new int[]{30}));
    }

    @Test
    void observationRemovesOnlyOwnedVirtualPassengersAndSurvivesRemounts() {
        var state = new PassengerState();
        state.observe(1, new int[]{20, 10, 40, 30}, Set.of(30));
        assertArrayEquals(new int[]{20, 10, 40}, state.get(1));
        state.seed(1, new int[]{10});
        assertArrayEquals(new int[]{20, 10, 40, 50}, PassengerLists.merge(state.get(1), new int[]{50}));
        state.remove(1);
        state.seed(1, new int[]{60});
        assertArrayEquals(new int[]{60}, state.get(1));
        state.clear();
        assertArrayEquals(new int[0], state.get(1));
    }

    @Test
    void abortedPresentationRestoresPacketObservations() {
        var state = new PassengerState();
        state.observe(1, new int[]{10, 20}, Set.of());
        var before = state.snapshot();
        state.seed(2, new int[]{30});
        state.restore(before);
        assertArrayEquals(new int[]{10, 20}, state.get(1));
        assertArrayEquals(new int[0], state.get(2));
    }
}
