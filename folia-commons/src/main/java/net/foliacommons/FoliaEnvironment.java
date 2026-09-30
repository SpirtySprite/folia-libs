package net.foliacommons;

/** Facts about the server the library is running on. */
public final class FoliaEnvironment {
    private static final boolean FOLIA = detectFolia();

    private FoliaEnvironment() {
    }

    /** True on Folia (regionised threading), false on plain Paper and other servers. */
    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException notFolia) {
            return false;
        }
    }
}
