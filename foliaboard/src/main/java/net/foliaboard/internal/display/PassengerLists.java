package net.foliaboard.internal.display;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PassengerLists {
    private PassengerLists() {
    }

    public static int[] merge(int[] actual, int[] virtual) {
        Set<Integer> ids = new LinkedHashSet<>();
        Arrays.stream(actual).forEach(ids::add);
        Arrays.stream(virtual).forEach(ids::add);
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }
}
