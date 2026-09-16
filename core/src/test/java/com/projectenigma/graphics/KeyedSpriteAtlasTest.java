package com.projectenigma.graphics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class KeyedSpriteAtlasTest {
    @Test void removesOnlyTheExportKeyIncludingDarkFringes() {
        assertEquals(0, KeyedSpriteAtlas.removeKey(0xffffff01));
        assertEquals(0, KeyedSpriteAtlas.removeKey(0x33aaff7f));
        assertEquals(0x33aaff80, KeyedSpriteAtlas.removeKey(0x33aaff80));
        assertEquals(0, KeyedSpriteAtlas.removeKey(0xff00ffff));
        assertEquals(0, KeyedSpriteAtlas.removeKey(0x640664ff));
        for (int color : new int[]{0x33aaffff, 0xee3322ff, 0xeeeeeeff, 0x181c20ff, 0x704632ff})
            assertEquals(color, KeyedSpriteAtlas.removeKey(color));
    }

    @Test void rejectsInvalidAndEmptySheets() {
        assertThrows(IllegalArgumentException.class, () -> new KeyedSpriteAtlas.Layout(0, 1, 8, 8, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> KeyedSpriteAtlas.decode(new int[4], 2, 2,
                new KeyedSpriteAtlas.Layout(1, 1, 8, 8, 1, 0)));
    }

    @Test void calibratingBattleScalePreservesShorterCollapsedPoses() {
        int[] source = new int[8 * 40];
        java.util.Arrays.fill(source, 0xff00ffff);
        for (int y = 5; y < 15; y++) for (int x = 2; x < 6; x++) source[y * 8 + x] = 0xeeeeeeff;
        for (int y = 30; y < 35; y++) for (int x = 2; x < 6; x++) source[y * 8 + x] = 0xeeeeeeff;
        var layout = new KeyedSpriteAtlas.Layout(1, 2, 16, 24, 1, 0);
        int[] result = KeyedSpriteAtlas.decode(source, 8, 40, layout, 12, false);
        int[] heights = new int[2];
        for (int row = 0; row < 2; row++) for (int y = 0; y < 24; y++) {
            if ((result[(row * 24 + y) * 16 + 8] & 255) != 0) heights[row]++;
        }
        assertArrayEquals(new int[]{12, 6}, heights);
    }

    @Test void neighbouringRowCannotChangeTheFootAnchor() {
        int[] source = new int[8 * 40];
        java.util.Arrays.fill(source, 0xff00ffff);
        for (int y = 5; y < 15; y++) for (int x = 2; x < 6; x++) source[y * 8 + x] = 0xeeeeeeff;
        // The second row's head begins above the nominal y=20 cut.
        for (int y = 19; y < 35; y++) for (int x = 2; x < 6; x++) source[y * 8 + x] = 0x33aaffff;
        int[] result = KeyedSpriteAtlas.decode(source, 8, 40,
                new KeyedSpriteAtlas.Layout(1, 2, 16, 24, 1, 2));
        assertEquals(0xeeeeeeff, result[19 * 16 + 8]);
        for (int i = 0; i < 16 * 24; i++) assertNotEquals(0x33aaffff, result[i], "Next row leaked into first frame");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sentinel", "hacker", "sniper", "enforcer", "bio-medic"})
    void actualOperativeSheetsHaveEveryFrameAndTransparentMargins(String role) throws Exception {
        boolean sentinel = role.equals("sentinel");
        verify(role, "battle", sentinel ? KeyedSpriteAtlas.SENTINEL_BATTLE : KeyedSpriteAtlas.OPERATIVE_BATTLE,
                sentinel ? 0 : KeyedSpriteAtlas.BATTLE_IDLE_HEIGHT);
        verify(role, "world", sentinel ? KeyedSpriteAtlas.SENTINEL_WORLD : KeyedSpriteAtlas.OPERATIVE_WORLD,
                sentinel ? 0 : KeyedSpriteAtlas.WORLD_IDLE_HEIGHT);
    }

    private void verify(String role, String name, KeyedSpriteAtlas.Layout layout, int idleHeight) throws Exception {
        BufferedImage image = ImageIO.read(Path.of("../assets/operatives/" + role + "/" + name + "-keyed.png").toFile());
        int[] rgba = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            rgba[y * image.getWidth() + x] = (argb << 8) | (argb >>> 24);
        }
        int[] decoded = KeyedSpriteAtlas.decode(rgba, image.getWidth(), image.getHeight(), layout, idleHeight, name.equals("world"));
        for (int row = 0; row < layout.rows(); row++) for (int col = 0; col < layout.columns(); col++) {
            int opaque = 0, bottom = -1, top = layout.frameHeight();
            for (int y = 0; y < layout.frameHeight(); y++) for (int x = 0; x < layout.frameWidth(); x++) {
                int pixel = decoded[(row * layout.frameHeight() + y) * layout.width() + col * layout.frameWidth() + x];
                if ((pixel & 255) == 0) continue;
                opaque++; bottom = y; top = Math.min(top, y);
                assertTrue(x > 0 && x < layout.frameWidth() - 1 && y > 0 && y < layout.frameHeight() - 1,
                        role + " " + name + " touches cell edge at " + row + "," + col);
            }
            assertTrue(opaque > 100, role + " " + name + " missing frame " + row + "," + col);
            assertTrue(bottom >= layout.frameHeight() - 6 && bottom <= layout.frameHeight() - 4,
                    role + " " + name + " foot alignment at " + row + "," + col + ": " + bottom);
            if (idleHeight > 0 && col == 0 && (row == 0 || name.equals("world"))) {
                assertEquals(idleHeight, bottom - top + 1, 1, role + " " + name + " must match Sentinel height");
            }
            if (name.equals("battle") && row == 5 && col == 5) {
                assertTrue(bottom - top + 1 < KeyedSpriteAtlas.BATTLE_IDLE_HEIGHT * .7f,
                        role + " defeated pose must stay collapsed rather than being stretched to standing height");
            }
        }
        // Inspection artifact from the exact runtime decoder, also useful for other engines.
        BufferedImage output = new BufferedImage(layout.width(), layout.height(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < layout.height(); y++) for (int x = 0; x < layout.width(); x++) {
            int pixel = decoded[y * layout.width() + x];
            output.setRGB(x, y, (pixel >>> 8) | (pixel << 24));
        }
        Path dir = Path.of("build/reports/operative-sprites", role); Files.createDirectories(dir);
        ImageIO.write(output, "png", dir.resolve(name + "-transparent.png").toFile());
    }
}
