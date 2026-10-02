package net.foliaboard.internal.display;

import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;

import java.util.List;

public interface DisplayTransport {
    boolean supported();

    Connection connect(Player viewer);

    float mountCorrection(Entity entity);

    default void release(Entity entity) {}

    interface Connection {
        void present(List<DisplayFrame> frames);

        void close();

        int entities();

        default boolean isClosed() {
            return false;
        }
    }
}
