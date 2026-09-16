package com.projectenigma.graphics;

/** Imports a chroma-key export into uniform, transparent, foot-aligned game frames. */
public final class KeyedSpriteAtlas {
    private KeyedSpriteAtlas() { }

    public record Layout(int columns, int rows, int frameWidth, int frameHeight, float scale, int gutter) {
        public Layout {
            if (columns < 1 || rows < 1 || frameWidth < 8 || frameHeight < 8 || !Float.isFinite(scale)
                    || scale <= 0 || gutter < 0) throw new IllegalArgumentException("Invalid sprite layout");
        }
        public int width() { return columns * frameWidth; }
        public int height() { return rows * frameHeight; }
    }

    public static final Layout SENTINEL_BATTLE = new Layout(8, 6, 256, 192, 1f, 8);
    public static final Layout SENTINEL_WORLD = new Layout(6, 4, 64, 96, .29f, 0);
    public static final Layout OPERATIVE_BATTLE = new Layout(8, 6, 256, 192, 1f, 12);
    public static final Layout OPERATIVE_WORLD = new Layout(6, 4, 64, 96, 1f, 8);

    // Visible idle heights in the approved Sentinel's decoded frames, before display scaling.
    public static final int BATTLE_IDLE_HEIGHT = 141;
    public static final int WORLD_IDLE_HEIGHT = 70;

    /** RGBA8888. Blue energy, red insignia and neutral armour are deliberately preserved. */
    public static int removeKey(int rgba) {
        // Generated transparent PNGs can contain near-invisible matte noise outside the sprite.
        // Exclude it before measuring frames so it cannot shift feet or change the import scale.
        if ((rgba & 255) < 128) return 0;
        int r = rgba >>> 24, g = (rgba >>> 16) & 255, b = (rgba >>> 8) & 255;
        return Math.min(r, b) > g * 1.6f + 12 ? 0 : rgba;
    }

    public static int[] decode(int[] source, int width, int height, Layout layout) {
        return decode(source, width, height, layout, 0, false);
    }

    /** Calibrate against idle once, preserving crouch, recoil and defeat proportions throughout a clip. */
    public static int[] decode(int[] source, int width, int height, Layout layout,
                               int idleHeight, boolean directional) {
        if (width < layout.columns || height < layout.rows || source.length != width * height)
            throw new IllegalArgumentException("Invalid source dimensions");
        int[] pixels = new int[source.length];
        for (int i = 0; i < source.length; i++) pixels[i] = removeKey(source[i]);
        int[] rowBoundaries = new int[layout.rows + 1];
        rowBoundaries[layout.rows] = height;
        for (int row = 1; row < layout.rows; row++) {
            rowBoundaries[row] = rowSeparator(pixels, width, height,
                    Math.round(row * (float) height / layout.rows), layout.gutter * 3);
        }
        int[] atlas = new int[layout.width() * layout.height()];
        for (int row = 0; row < layout.rows; row++) {
            int top = rowBoundaries[row];
            int end = rowBoundaries[row + 1];
            float scale = layout.scale;
            if (idleHeight > 0) {
                int idleRow = directional ? row : 0;
                int idleTop = rowBoundaries[idleRow];
                int idleEnd = rowBoundaries[idleRow + 1];
                int idleRight = separator(pixels, width, idleTop, idleEnd,
                        Math.round((float) width / layout.columns), layout.gutter * 3);
                int first = idleEnd, last = -1;
                for (int y = idleTop; y < idleEnd; y++) for (int x = 0; x < idleRight; x++) {
                    if ((pixels[y * width + x] & 255) == 0) continue;
                    first = Math.min(first, y);
                    last = y;
                }
                if (last < first) throw new IllegalArgumentException("Missing calibration idle at row " + idleRow);
                scale = idleHeight / (float) (last - first + 1);
            }
            int[] boundaries = new int[layout.columns + 1];
            boundaries[layout.columns] = width;
            for (int col = 1; col < layout.columns; col++) {
                int nominal = Math.round(col * (float) width / layout.columns);
                boundaries[col] = separator(pixels, width, top, end, nominal, layout.gutter * 3);
            }
            for (int col = 0; col < layout.columns; col++) {
                float center = (col + .5f) * width / layout.columns;
                int left = boundaries[col];
                int right = boundaries[col + 1];
                int bottom = -1;
                for (int y = top; y < end; y++) for (int x = left; x < right; x++)
                    if ((pixels[y * width + x] & 255) != 0) bottom = y;
                if (bottom < 0) throw new IllegalArgumentException("Empty sprite at " + row + "," + col);
                for (int dy = 0; dy < layout.frameHeight; dy++) {
                    int sy = (int) Math.floor(bottom + 1 - (layout.frameHeight - 4 - dy - .5f) / scale);
                    if (sy < top || sy >= end) continue;
                    for (int dx = 0; dx < layout.frameWidth; dx++) {
                        int sx = (int) Math.floor(center + (dx - layout.frameWidth / 2f + .5f) / scale);
                        if (sx < left || sx >= right) continue;
                        atlas[(row * layout.frameHeight + dy) * layout.width() + col * layout.frameWidth + dx]
                                = pixels[sy * width + sx];
                    }
                }
            }
        }
        return atlas;
    }

    /** Share a transparent cut between frames so extended effects never leak into their neighbour. */
    private static int separator(int[] pixels, int width, int top, int end, int nominal, int radius) {
        for (int offset = 0; offset <= radius; offset++) {
            for (int x : new int[]{nominal + offset, nominal - offset}) {
                if (x <= 0 || x >= width) continue;
                boolean clear = true;
                for (int y = top; y < end; y++) if ((pixels[y * width + x] & 255) != 0) {
                    clear = false;
                    break;
                }
                if (clear) return x;
            }
        }
        return nominal;
    }

    private static int rowSeparator(int[] pixels, int width, int height, int nominal, int radius) {
        for (int offset = 0; offset <= radius; offset++) {
            for (int y : new int[]{nominal + offset, nominal - offset}) {
                if (y <= 0 || y >= height) continue;
                boolean clear = true;
                for (int x = 0; x < width; x++) if ((pixels[y * width + x] & 255) != 0) {
                    clear = false;
                    break;
                }
                if (clear) return y;
            }
        }
        return nominal;
    }
}
