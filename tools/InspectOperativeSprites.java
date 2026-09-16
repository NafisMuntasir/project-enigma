import com.projectenigma.graphics.KeyedSpriteAtlas;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Exports the exact runtime-imported atlases for inspection or use in another engine. */
public final class InspectOperativeSprites {
    public static void main(String[] roles) throws Exception {
        for (String role : roles) for (String kind : new String[]{"world", "battle"}) {
            if (!java.util.Set.of("sentinel", "hacker", "sniper", "enforcer", "bio-medic").contains(role))
                throw new IllegalArgumentException("Unknown operative: " + role);
            boolean world = kind.equals("world"), sentinel = role.equals("sentinel");
            var layout = world ? (sentinel ? KeyedSpriteAtlas.SENTINEL_WORLD : KeyedSpriteAtlas.OPERATIVE_WORLD)
                    : (sentinel ? KeyedSpriteAtlas.SENTINEL_BATTLE : KeyedSpriteAtlas.OPERATIVE_BATTLE);
            var input = ImageIO.read(Path.of("assets/operatives", role, kind + "-keyed.png").toFile());
            int[] rgba = new int[input.getWidth() * input.getHeight()];
            for (int y = 0; y < input.getHeight(); y++) for (int x = 0; x < input.getWidth(); x++) {
                int argb = input.getRGB(x, y);
                rgba[y * input.getWidth() + x] = (argb << 8) | (argb >>> 24);
            }
            int[] result = KeyedSpriteAtlas.decode(rgba, input.getWidth(), input.getHeight(), layout,
                    sentinel ? 0 : world ? KeyedSpriteAtlas.WORLD_IDLE_HEIGHT : KeyedSpriteAtlas.BATTLE_IDLE_HEIGHT, world);
            var output = new BufferedImage(layout.width(), layout.height(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < layout.height(); y++) for (int x = 0; x < layout.width(); x++) {
                int pixel = result[y * layout.width() + x];
                output.setRGB(x, y, (pixel >>> 8) | (pixel << 24));
            }
            Path dir = Path.of("core/build/reports/operative-sprites", role);
            Files.createDirectories(dir);
            ImageIO.write(output, "png", dir.resolve(kind + "-transparent.png").toFile());
            System.out.println(role + " " + kind + " source " + input.getWidth() + "x" + input.getHeight());
            for (int row = 0; row < layout.rows(); row++) {
                StringBuilder bounds = new StringBuilder("  row " + row + " WxH:");
                for (int col = 0; col < layout.columns(); col++) {
                    int minX = layout.frameWidth(), maxX = -1, minY = layout.frameHeight(), maxY = -1;
                    for (int y = 0; y < layout.frameHeight(); y++) for (int x = 0; x < layout.frameWidth(); x++) {
                        if ((output.getRGB(col * layout.frameWidth() + x, row * layout.frameHeight() + y) >>> 24) == 0) continue;
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    }
                    bounds.append(" ").append(maxX - minX + 1).append("x").append(maxY - minY + 1)
                            .append("@foot").append(maxY);
                }
                System.out.println(bounds);
            }
        }
    }
}
