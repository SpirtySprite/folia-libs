package net.foliaboard.internal.tab;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class TabOrder {
    private static final Method METHOD = resolve();
    private static final Method GETTER = resolveGetter();
    private static volatile boolean warned = false;

    private TabOrder() {
    }

    private static Method resolve() {
        for (String name : new String[]{"playerListOrder", "setPlayerListOrder"}) {
            try {
                Method m = Player.class.getMethod(name, int.class);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    public static boolean supported() {
        return METHOD != null;
    }

    private static Method resolveGetter() {
        for (String name : new String[]{"playerListOrder", "getPlayerListOrder"}) {
            try {
                return Player.class.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    public static java.util.OptionalInt current(Player player) {
        if (GETTER != null) {
            try {
                return java.util.OptionalInt.of((Integer) GETTER.invoke(player));
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return java.util.OptionalInt.empty();
    }

    public static void set(Player player, int order) {
        if (METHOD == null) {
            if (!warned) {
                warned = true;
                Logger.getLogger("FoliaBoard").info(
                        "Tab-list ordering (Player#playerListOrder) isn't available on this server "
                                + "(needs 1.21.2+); use nametag tabSort for ordering instead.");
            }
            return;
        }
        try {
            METHOD.invoke(player, order);
        } catch (Throwable ignored) {
        }
    }
}
