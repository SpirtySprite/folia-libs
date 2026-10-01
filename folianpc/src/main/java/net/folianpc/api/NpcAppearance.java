package net.folianpc.api;

import net.kyori.adventure.text.format.NamedTextColor;

public record NpcAppearance(boolean glowing, boolean invisible, boolean skinLayers, double scale,
                            NamedTextColor glowColor, boolean collidable, boolean nametagVisible) {

    /** Rejects non-finite scale values and preserves the minimum supported scale. */
    public NpcAppearance {
        if (!Double.isFinite(scale)) {
            throw new IllegalArgumentException("scale must be finite");
        }
        scale = Math.max(0.0625, scale);
    }

    public static NpcAppearance defaults() {
        return new NpcAppearance(false, false, true, 1.0, null, true, true);
    }
}
