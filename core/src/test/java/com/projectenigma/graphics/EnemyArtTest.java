package com.projectenigma.graphics;

import com.projectenigma.model.EnemyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EnemyArtTest {
    @ParameterizedTest @EnumSource(EnemyType.class)
    void allEnemyFramesImportWithoutClippingAndMatchTheirScale(EnemyType type) throws Exception {
        var profile = EnemyArt.profile(type);
        verify(profile.directory() + "world-keyed.png", EnemyArt.WORLD, profile.worldHeight(), true);
        verify(profile.directory() + "battle-keyed.png", EnemyArt.BATTLE, profile.battleHeight(), false);
    }

    @Test void chestHasFourOpeningFramesAtOneScale() throws Exception {
        int[][] heights = verify("assets/props/chest/opening-keyed.png", EnemyArt.CHEST, EnemyArt.CHEST_CLOSED_HEIGHT, false);
        assertTrue(heights[1][1] > heights[0][0] + 5, "The open lid must rise above the closed chest");
    }

    @Test void chestPlaybackClampsAndSavedOpenChestsDoNotReplay() {
        assertEquals(0, EnemyArt.chestFrame(false, Float.POSITIVE_INFINITY));
        assertEquals(0, EnemyArt.chestFrame(true, 0));
        assertEquals(1, EnemyArt.chestFrame(true, .13f));
        assertEquals(2, EnemyArt.chestFrame(true, .25f));
        assertEquals(3, EnemyArt.chestFrame(true, .37f));
        assertEquals(3, EnemyArt.chestFrame(true, 300));
        assertEquals(3, EnemyArt.chestFrame(true, Float.POSITIVE_INFINITY));
    }

    private int[][] verify(String path, KeyedSpriteAtlas.Layout layout, int idleHeight, boolean directional) throws Exception {
        var input = ImageIO.read(Path.of("..", path).toFile());
        int[] rgba = new int[input.getWidth() * input.getHeight()];
        for (int y = 0; y < input.getHeight(); y++) for (int x = 0; x < input.getWidth(); x++) {
            int pixel = input.getRGB(x, y);
            rgba[y * input.getWidth() + x] = (pixel << 8) | (pixel >>> 24);
        }
        int[] result = KeyedSpriteAtlas.decode(rgba, input.getWidth(), input.getHeight(), layout, idleHeight, directional);
        int[][] heights = new int[layout.rows()][layout.columns()];
        for (int row = 0; row < layout.rows(); row++) for (int col = 0; col < layout.columns(); col++) {
            int top = layout.frameHeight(), bottom = -1, count = 0;
            for (int y = 0; y < layout.frameHeight(); y++) for (int x = 0; x < layout.frameWidth(); x++) {
                if ((result[(row * layout.frameHeight() + y) * layout.width() + col * layout.frameWidth() + x] & 255) == 0) continue;
                top = Math.min(top, y); bottom = y; count++;
                assertTrue(x > 0 && x < layout.frameWidth() - 1 && y > 0 && y < layout.frameHeight() - 1,
                        path + " touches frame edge " + row + "," + col);
            }
            assertTrue(count > 35, path + " empty frame " + row + "," + col);
            assertTrue(bottom >= layout.frameHeight() - 7 && bottom <= layout.frameHeight() - 4,
                    path + " incorrect base anchor at " + row + "," + col + ": " + bottom);
            heights[row][col] = bottom - top + 1;
            if (col == 0 && (row == 0 || directional))
                assertEquals(idleHeight, heights[row][col], 1, path + " inconsistent calibrated size");
        }
        return heights;
    }
}
