package net.folianpc.internal.protocol.nms;

public final class ServerVersion {

    private static final net.foliacommons.version.ServerVersion CURRENT = detect();

    private ServerVersion() {
    }

    private static net.foliacommons.version.ServerVersion detect() {
        try {
            net.foliacommons.version.ServerVersion detected = net.foliacommons.version.ServerVersion.current();
            if (detected.major() > 0) {
                return detected;
            }
        } catch (RuntimeException noServer) {
        }
        return net.foliacommons.version.ServerVersion.of(1, 21, 0);
    }

    public static int minor() {
        return CURRENT.minor();
    }

    public static boolean atLeast(int minMinor, int minPatch) {
        return CURRENT.isAtLeast(minMinor, minPatch);
    }

    public static void requireSupported() {
        if (!atLeast(20, 6)) {
            throw new IllegalStateException("FoliaNPC requires Paper/Folia 1.20.6 or newer (found " + describe() + ")");
        }
    }

    private static String describe() {
        return CURRENT.isCalendarScheme()
                ? CURRENT.major() + "." + CURRENT.minor() + (CURRENT.patch() > 0 ? "." + CURRENT.patch() : "")
                : CURRENT.toString();
    }
}
