import com.projectenigma.graphics.EnemyArt;
import com.projectenigma.graphics.KeyedSpriteAtlas;
import com.projectenigma.model.EnemyType;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Export the exact runtime-imported enemy/chest frames as transparent PNG atlases. */
public final class InspectEnemySprites {
    public static void main(String[] args) throws Exception {
        var requested = java.util.Set.of(args);
        for (EnemyType type : EnemyType.values()) {
            var profile = EnemyArt.profile(type);
            if (!requested.isEmpty() && !requested.contains(profile.id())) continue;
            export(profile.id(), "world", profile.directory() + "world-keyed.png", EnemyArt.WORLD, profile.worldHeight(), true);
            export(profile.id(), "battle", profile.directory() + "battle-keyed.png", EnemyArt.BATTLE, profile.battleHeight(), false);
        }
        if (requested.isEmpty() || requested.contains("chest"))
            export("chest", "opening", "assets/props/chest/opening-keyed.png", EnemyArt.CHEST, EnemyArt.CHEST_CLOSED_HEIGHT, false);
    }

    private static void export(String name, String kind, String path, KeyedSpriteAtlas.Layout layout,
                               int idleHeight, boolean directional) throws Exception {
        var input = ImageIO.read(Path.of(path).toFile());
        int[] rgba = new int[input.getWidth() * input.getHeight()];
        for (int y = 0; y < input.getHeight(); y++) for (int x = 0; x < input.getWidth(); x++) {
            int argb = input.getRGB(x, y);
            rgba[y * input.getWidth() + x] = (argb << 8) | (argb >>> 24);
        }
        int[] result = KeyedSpriteAtlas.decode(rgba, input.getWidth(), input.getHeight(), layout, idleHeight, directional);
        var output = new BufferedImage(layout.width(), layout.height(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < layout.height(); y++) for (int x = 0; x < layout.width(); x++) {
            int pixel = result[y * layout.width() + x];
            output.setRGB(x, y, (pixel >>> 8) | (pixel << 24));
        }
        Path dir = Path.of("core/build/reports/enemy-sprites", name);
        Files.createDirectories(dir);
        ImageIO.write(output, "png", dir.resolve(kind + "-transparent.png").toFile());
        System.out.println(name + " " + kind + " source " + input.getWidth() + "x" + input.getHeight()
                + " corner-alpha=" + (input.getRGB(0, 0) >>> 24));
        for (int row = 0; row < layout.rows(); row++) {
            var line = new StringBuilder("row " + row + " WxH:");
            for (int col = 0; col < layout.columns(); col++) {
                int minX = layout.frameWidth(), minY = layout.frameHeight(), maxX = -1, maxY = -1;
                for (int y = 0; y < layout.frameHeight(); y++) for (int x = 0; x < layout.frameWidth(); x++) {
                    if ((output.getRGB(col * layout.frameWidth() + x, row * layout.frameHeight() + y) >>> 24) == 0) continue;
                    minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
                line.append(" ").append(maxX - minX + 1).append("x").append(maxY - minY + 1).append("@foot").append(maxY);
            }
            System.out.println(line);
        }
    }
}
