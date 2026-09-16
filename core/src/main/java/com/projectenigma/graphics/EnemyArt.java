package com.projectenigma.graphics;

import com.projectenigma.model.EnemyType;

/** Stable save IDs map to presentation assets without changing enemy statistics. */
public final class EnemyArt {
    private EnemyArt() { }

    public static final KeyedSpriteAtlas.Layout WORLD = new KeyedSpriteAtlas.Layout(4, 4, 96, 96, 1, 12);
    public static final KeyedSpriteAtlas.Layout BATTLE = new KeyedSpriteAtlas.Layout(6, 4, 256, 192, 1, 16);
    public static final KeyedSpriteAtlas.Layout CHEST = new KeyedSpriteAtlas.Layout(2, 2, 64, 96, 1, 12);
    public static final int CHEST_CLOSED_HEIGHT = 32;
    public static final float CHEST_FRAME_DURATION = .12f;

    public record Profile(String id, int worldHeight, int battleHeight) {
        public String directory() { return "assets/adversaries/" + id + "/"; }
    }

    public static Profile profile(EnemyType type) {
        return switch (type) {
            case CAVE_SLIME -> new Profile("recon-drone", 34, 72);
            case BONE_SENTINEL -> new Profile("aegis-robot", 73, 145);
            case ABYSS_MAGE -> new Profile("helix-cyborg", 70, 141);
            case FLOOR_WARDEN -> new Profile("enhanced-warden", 78, 150);
        };
    }

    /** A loaded, already-open chest passes positive infinity and stays on the final frame. */
    public static int chestFrame(boolean opened, float elapsed) {
        if (!opened) return 0;
        return Math.min(3, Math.max(0, (int) (elapsed / CHEST_FRAME_DURATION)));
    }
}
