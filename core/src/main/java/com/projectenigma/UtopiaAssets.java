package com.projectenigma;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.projectenigma.graphics.KeyedSpriteAtlas;
import com.projectenigma.graphics.EnemyArt;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.projectenigma.model.EnemyType;
import com.projectenigma.model.HeroClass;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Owns every texture used by the utopian pixel-art presentation layer.
 *
 * <p>The gameplay screens ask this class for frames instead of loading files
 * themselves. That keeps texture ownership in one place, prevents duplicate
 * GPU allocations when switching screens, and guarantees nearest-neighbour
 * filtering for every pixel-art image.</p>
 */
public final class UtopiaAssets {
    private static final String ROOT = "assets/utopia/";
    private static final int TILE_SIZE = 64;
    private static final int TILE_COLUMNS = 8;
    private static final float WORLD_FRAME_DURATION = 0.13f;

    public enum Direction {
        DOWN(0), LEFT(1), RIGHT(2), UP(3);

        private final int row;

        Direction(int row) {
            this.row = row;
        }
    }

    public enum BattlePose {
        IDLE(0, 4, 0.12f, true),
        ATTACK(1, 6, 0.12f, false),
        SKILL(2, 8, 0.12f, false),
        GUARD(3, 4, 0.12f, false),
        HURT(4, 3, 0.12f, false),
        DEFEAT(5, 6, 0.16f, false);

        private final int row;
        private final int frameCount;
        private final float frameDuration;
        private final boolean looping;

        BattlePose(int row, int frameCount, float frameDuration, boolean looping) {
            this.row = row;
            this.frameCount = frameCount;
            this.frameDuration = frameDuration;
            this.looping = looping;
        }
    }

    private final List<Texture> ownedTextures = new ArrayList<>();
    private final EnumMap<HeroClass, TextureRegion[][]> heroWorldFrames = new EnumMap<>(HeroClass.class);
    private final EnumMap<HeroClass, TextureRegion[][]> heroBattleFrames = new EnumMap<>(HeroClass.class);
    private final EnumMap<HeroClass, TextureRegion[][]> mirroredHeroBattleFrames = new EnumMap<>(HeroClass.class);
    private final EnumMap<EnemyType, TextureRegion[][]> enemyWorldFrames = new EnumMap<>(EnemyType.class);
    private final EnumMap<EnemyType, TextureRegion[][]> enemyBattleFrames = new EnumMap<>(EnemyType.class);
    private final EnumMap<EnemyType, TextureRegion[][]> mirroredEnemyBattleFrames = new EnumMap<>(EnemyType.class);

    private final Texture menuBackground;
    private final Texture battleBackground;
    private final TextureRegion[][] tiles;
    private final TextureRegion[][] chestFrames;

    public UtopiaAssets() {
        menuBackground = loadTexture(ROOT + "backgrounds/menu_rooftop_1280x720.png");
        battleBackground = loadTexture(ROOT + "backgrounds/battle_atrium_1280x720.png");

        Texture tileTexture = loadTexture(ROOT + "tiles/utopia_tileset_64.png");
        tiles = TextureRegion.split(tileTexture, TILE_SIZE, TILE_SIZE);
        requireGrid(tiles, 8, 8, "utopia tileset");
        Texture chestTexture = loadKeyedTexture("assets/props/chest/opening-keyed.png", EnemyArt.CHEST,
                EnemyArt.CHEST_CLOSED_HEIGHT, false);
        chestFrames = TextureRegion.split(chestTexture, 64, 96);
        requireGrid(chestFrames, 2, 2, "supply chest");

        for (HeroClass heroClass : HeroClass.values()) {
            String id = operativeAssetId(heroClass);
            String path = "assets/operatives/" + id + "/";
            boolean sentinel = heroClass == HeroClass.WARRIOR;
            Texture worldTexture = loadKeyedTexture(path + "world-keyed.png",
                    sentinel ? KeyedSpriteAtlas.SENTINEL_WORLD : KeyedSpriteAtlas.OPERATIVE_WORLD,
                    sentinel ? 0 : KeyedSpriteAtlas.WORLD_IDLE_HEIGHT, true);
            TextureRegion[][] world = TextureRegion.split(worldTexture, 64, 96);
            requireGrid(world, 4, 6, id + " top-down sheet");
            heroWorldFrames.put(heroClass, world);

            Texture battleTexture = loadKeyedTexture(path + "battle-keyed.png",
                    sentinel ? KeyedSpriteAtlas.SENTINEL_BATTLE : KeyedSpriteAtlas.OPERATIVE_BATTLE,
                    sentinel ? 0 : KeyedSpriteAtlas.BATTLE_IDLE_HEIGHT, false);
            TextureRegion[][] battle = TextureRegion.split(battleTexture, 256, 192);
            requireGrid(battle, 6, 8, id + " battle sheet");
            heroBattleFrames.put(heroClass, battle);
            mirroredHeroBattleFrames.put(heroClass, mirroredCopy(battle));
        }

        for (EnemyType enemyType : EnemyType.values()) {
            EnemyArt.Profile profile = EnemyArt.profile(enemyType);
            String id = profile.id();
            Texture worldTexture = loadKeyedTexture(profile.directory() + "world-keyed.png", EnemyArt.WORLD,
                    profile.worldHeight(), true);
            TextureRegion[][] world = TextureRegion.split(worldTexture, 96, 96);
            requireGrid(world, 4, 4, id + " top-down sheet");
            enemyWorldFrames.put(enemyType, world);

            Texture battleTexture = loadKeyedTexture(profile.directory() + "battle-keyed.png", EnemyArt.BATTLE,
                    profile.battleHeight(), false);
            TextureRegion[][] battle = TextureRegion.split(battleTexture, 256, 192);
            requireGrid(battle, 4, 6, id + " battle sheet");
            enemyBattleFrames.put(enemyType, battle);
            mirroredEnemyBattleFrames.put(enemyType, mirroredCopy(battle));
        }
    }

    public Texture menuBackground() {
        return menuBackground;
    }

    public Texture battleBackground() {
        return battleBackground;
    }

    public TextureRegion floorTile(int x, int y) {
        int pattern = Math.floorMod(x * 37 + y * 19, 47);
        if (pattern == 0) {
            return tile(7); // restrained red cross accent
        }
        if (pattern == 1) {
            return tile(12); // restrained blue cross accent
        }
        if (pattern < 5) {
            return tile(2); // inset white floor panel
        }
        if (pattern == 5) {
            return tile(14); // occasional grate
        }
        return tile(((x + y) & 1) == 0 ? 0 : 1);
    }

    public TextureRegion wallTile(int x, int y, boolean floorNorth, boolean floorSouth,
                                  boolean floorEast, boolean floorWest) {
        if (floorNorth && floorEast) {
            return tile(21);
        }
        if (floorNorth && floorWest) {
            return tile(22);
        }
        if (floorSouth && floorEast) {
            return tile(23);
        }
        if (floorSouth && floorWest) {
            return tile(24);
        }
        if (floorNorth) {
            return tile(18);
        }
        if (floorSouth) {
            return tile(17);
        }
        if (floorEast) {
            return tile(19);
        }
        if (floorWest) {
            return tile(20);
        }
        int pattern = Math.floorMod(x * 23 + y * 31, 29);
        return tile(pattern == 0 ? 31 : 16);
    }

    public TextureRegion stairsDownTile() {
        return tile(33);
    }

    public TextureRegion chestTile(boolean opened) {
        return chestFrame(opened, Float.POSITIVE_INFINITY);
    }

    public TextureRegion chestFrame(boolean opened, float elapsed) {
        int index = EnemyArt.chestFrame(opened, elapsed);
        return chestFrames[index / 2][index % 2];
    }

    public TextureRegion worldHeroFrame(HeroClass heroClass, Direction direction,
                                        boolean moving, float stateTime) {
        return worldFrame(heroWorldFrames.get(heroClass), direction, moving, stateTime);
    }

    public TextureRegion worldEnemyFrame(EnemyType enemyType, float stateTime) {
        return worldEnemyFrame(enemyType, Direction.DOWN, stateTime);
    }

    public TextureRegion worldEnemyFrame(EnemyType enemyType, Direction direction, float stateTime) {
        int frame = Math.max(0, (int) (stateTime / WORLD_FRAME_DURATION)) % 4;
        return enemyWorldFrames.get(enemyType)[direction.row][frame];
    }

    public void drawWorldEnemy(SpriteBatch batch, EnemyType type, Direction direction, float stateTime,
                               float centerX, float bottomY, float height) {
        TextureRegion frame = worldEnemyFrame(type, direction, stateTime);
        float scaledHeight = height * 1.30f;
        float width = scaledHeight * frame.getRegionWidth() / frame.getRegionHeight();
        float anchoredBottom = bottomY - (scaledHeight - height) * 4f / frame.getRegionHeight();
        if (type == EnemyType.CAVE_SLIME) anchoredBottom += height * .12f;
        batch.draw(frame, centerX - width / 2, anchoredBottom, width, scaledHeight);
    }

    public void drawBattleEnemy(SpriteBatch batch, EnemyType type, BattlePose pose, float stateTime,
                                boolean mirrored, float centerX, float bottomY, float height) {
        TextureRegion frame = battleEnemyFrame(type, pose, stateTime, mirrored);
        float scaledHeight = height * 1.12f;
        float width = scaledHeight * frame.getRegionWidth() / frame.getRegionHeight();
        float anchoredBottom = bottomY - (scaledHeight - height) * 4f / frame.getRegionHeight();
        if (type == EnemyType.CAVE_SLIME) {
            float hovering = pose == BattlePose.DEFEAT ? Math.max(0, 1 - stateTime / .48f) : 1;
            anchoredBottom += height * .17f * hovering;
        }
        batch.draw(frame, centerX - width / 2, anchoredBottom, width, scaledHeight);
    }

    public static String operativeAssetId(HeroClass heroClass) {
        return switch (heroClass) {
            case WARRIOR -> "sentinel";
            case MAGE -> "hacker";
            case THIEF -> "sniper";
            case GRAPPLER -> "enforcer";
            case CLERIC -> "bio-medic";
        };
    }

    public void drawWorldHero(SpriteBatch batch, HeroClass heroClass, Direction direction, boolean moving,
                              float stateTime, float centerX, float bottomY, float height) {
        TextureRegion frame = worldHeroFrame(heroClass, direction, moving, stateTime);
        float scaledHeight = height * 1.30f;
        float width = scaledHeight * frame.getRegionWidth() / frame.getRegionHeight();
        // Imported frames have four transparent pixels below the feet.
        float anchoredBottom = bottomY - (scaledHeight - height) * 4f / frame.getRegionHeight();
        batch.draw(frame, centerX - width / 2f, anchoredBottom, width, scaledHeight);
    }

    public TextureRegion battleHeroFrame(HeroClass heroClass, BattlePose pose,
                                         float stateTime, boolean mirrored) {
        TextureRegion[][] frames = mirrored
                ? mirroredHeroBattleFrames.get(heroClass)
                : heroBattleFrames.get(heroClass);
        return battleFrame(frames, pose, stateTime);
    }

    /** Preserve frame aspect ratio and the approved Sentinel scale for every operative. */
    public void drawBattleHero(SpriteBatch batch, HeroClass heroClass, BattlePose pose, float stateTime,
                               boolean mirrored, float centerX, float bottomY, float height) {
        TextureRegion frame = battleHeroFrame(heroClass, pose, stateTime, mirrored);
        float scaledHeight = height * 1.12f;
        float width = scaledHeight * frame.getRegionWidth() / frame.getRegionHeight();
        float anchoredBottom = bottomY - (scaledHeight - height) * 4f / frame.getRegionHeight();
        batch.draw(frame, centerX - width / 2f, anchoredBottom, width, scaledHeight);
    }

    private Texture loadKeyedTexture(String path, KeyedSpriteAtlas.Layout layout, int idleHeight, boolean directional) {
        Pixmap source = new Pixmap(Gdx.files.internal(path));
        Pixmap atlas = null;
        try {
            int width = source.getWidth(), height = source.getHeight();
            int[] rgba = new int[width * height];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) rgba[y * width + x] = source.getPixel(x, y);
            int[] decoded = KeyedSpriteAtlas.decode(rgba, width, height, layout, idleHeight, directional);
            atlas = new Pixmap(layout.width(), layout.height(), Pixmap.Format.RGBA8888);
            atlas.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < layout.height(); y++) for (int x = 0; x < layout.width(); x++)
                atlas.drawPixel(x, y, decoded[y * layout.width() + x]);
            Texture texture = new Texture(atlas);
            texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            ownedTextures.add(texture);
            return texture;
        } finally {
            source.dispose();
            if (atlas != null) atlas.dispose();
        }
    }

    public TextureRegion battleEnemyFrame(EnemyType enemyType, BattlePose pose,
                                          float stateTime, boolean mirrored) {
        TextureRegion[][] frames = mirrored
                ? mirroredEnemyBattleFrames.get(enemyType)
                : enemyBattleFrames.get(enemyType);
        return enemyBattleFrame(frames, pose, stateTime);
    }

    public void dispose() {
        for (Texture texture : ownedTextures) {
            texture.dispose();
        }
        ownedTextures.clear();
    }

    private TextureRegion tile(int index) {
        int row = index / TILE_COLUMNS;
        int column = index % TILE_COLUMNS;
        return tiles[row][column];
    }

    private static TextureRegion worldFrame(TextureRegion[][] frames, Direction direction,
                                            boolean moving, float stateTime) {
        int frame = Math.max(0, (int) (stateTime / WORLD_FRAME_DURATION));
        int column = moving ? 2 + frame % 4 : frame % 2;
        return frames[direction.row][column];
    }

    private static TextureRegion battleFrame(TextureRegion[][] frames, BattlePose pose, float stateTime) {
        int rawFrame = Math.max(0, (int) (stateTime / pose.frameDuration));
        int column = pose.looping
                ? rawFrame % pose.frameCount
                : Math.min(rawFrame, pose.frameCount - 1);
        return frames[pose.row][column];
    }

    private static TextureRegion enemyBattleFrame(TextureRegion[][] frames, BattlePose pose, float stateTime) {
        int row;
        int frameCount;
        float frameDuration = 0.12f;
        boolean looping = false;
        switch (pose) {
            case IDLE -> {
                row = 0;
                frameCount = 4;
                looping = true;
            }
            case ATTACK, SKILL -> {
                row = 1;
                frameCount = 6;
            }
            case HURT -> {
                row = 2;
                frameCount = 3;
            }
            case DEFEAT -> {
                row = 3;
                frameCount = 6;
                frameDuration = 0.16f;
            }
            case GUARD -> {
                row = 0;
                frameCount = 4;
            }
            default -> throw new IllegalStateException("Unhandled enemy pose " + pose);
        }
        int rawFrame = Math.max(0, (int) (stateTime / frameDuration));
        int column = looping ? rawFrame % frameCount : Math.min(rawFrame, frameCount - 1);
        return frames[row][column];
    }

    private Texture loadTexture(String path) {
        FileHandle file = Gdx.files.internal(path);
        if (!file.exists()) {
            throw new IllegalStateException("Required game asset is missing: " + path);
        }
        Texture texture = new Texture(file);
        texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        ownedTextures.add(texture);
        return texture;
    }

    private static TextureRegion[][] mirroredCopy(TextureRegion[][] source) {
        TextureRegion[][] copy = new TextureRegion[source.length][];
        for (int row = 0; row < source.length; row++) {
            copy[row] = new TextureRegion[source[row].length];
            for (int column = 0; column < source[row].length; column++) {
                TextureRegion region = new TextureRegion(source[row][column]);
                region.flip(true, false);
                copy[row][column] = region;
            }
        }
        return copy;
    }

    private static void requireGrid(TextureRegion[][] frames, int rows, int columns, String label) {
        if (frames.length < rows) {
            throw new IllegalStateException("Invalid " + label + ": expected at least " + rows + " rows");
        }
        for (int row = 0; row < rows; row++) {
            if (frames[row].length < columns) {
                throw new IllegalStateException("Invalid " + label + ": expected at least "
                        + columns + " columns in row " + row);
            }
        }
    }
}
