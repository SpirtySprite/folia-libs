package net.foliaboard.internal.display;

import org.bukkit.entity.Player;

import java.util.List;

public interface DisplayTransport {
    boolean supported();

    Connection connect(Player viewer);

    float mountCorrection(Player player);

    interface Connection {
        void present(List<DisplayFrame> frames);

        void close();

        int entities();
    }
}
