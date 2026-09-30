package net.foliabench.support;

/** The JMH defaults every benchmark shares are set with annotations; this only documents the conventions. */
public final class Settings {
    /** Fixed seed so every run builds the same worlds, players and NPCs. */
    public static final long SEED = 0xF011AL;

    private Settings() {
    }
}
