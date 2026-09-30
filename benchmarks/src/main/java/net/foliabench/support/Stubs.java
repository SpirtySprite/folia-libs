package net.foliabench.support;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Cheap stand-ins for {@link Player} and {@link Plugin}.
 *
 * <p>Mockito mocks record every call, which costs far more than the code being measured and leaks memory
 * over millions of invocations. These proxies answer a few getters and return defaults for the rest.
 */
public final class Stubs {
    private static final Logger LOGGER = Logger.getLogger("folia-bench");

    private Stubs() {
    }

    public static Player player(String name) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes());
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getName" -> name;
            case "getUniqueId" -> id;
            case "isOnline" -> true;
            case "getPing" -> 42;
            case "getHealth" -> 18.5D;
            case "getLevel" -> 30;
            case "hashCode" -> id.hashCode();
            case "equals" -> proxy == args[0];
            case "toString" -> "BenchPlayer[" + name + "]";
            default -> defaultFor(method.getReturnType());
        };
        return (Player) Proxy.newProxyInstance(Stubs.class.getClassLoader(), new Class<?>[]{Player.class}, handler);
    }

    public static Plugin plugin(String name) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "isEnabled" -> true;
            case "getName" -> name;
            case "getLogger" -> LOGGER;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "BenchPlugin[" + name + "]";
            default -> defaultFor(method.getReturnType());
        };
        return (Plugin) Proxy.newProxyInstance(Stubs.class.getClassLoader(), new Class<?>[]{Plugin.class}, handler);
    }

    private static Object defaultFor(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0D;
    }
}
