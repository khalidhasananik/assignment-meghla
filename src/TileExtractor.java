import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Utility to extract individual 16x16 tiles from the Pixel Adventure terrain sprite sheet.
 * Run once to generate tile images, then delete this file.
 */
public class TileExtractor {
    public static void main(String[] args) throws Exception {
        BufferedImage sheet = ImageIO.read(new File("../assets/images/Terrain/Terrain (16x16).png"));
        int tileSize = 16;
        int cols = sheet.getWidth() / tileSize;
        int rows = sheet.getHeight() / tileSize;

        File outDir = new File("../maps");
        outDir.mkdirs();

        System.out.println("Sheet: " + sheet.getWidth() + "x" + sheet.getHeight());
        System.out.println("Grid: " + cols + " cols x " + rows + " rows");

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                BufferedImage tile = sheet.getSubimage(c * tileSize, r * tileSize, tileSize, tileSize);
                String name = String.format("tile_%d_%d.png", r, c);
                ImageIO.write(tile, "png", new File(outDir, name));
            }
        }
        System.out.println("Done! Extracted " + (rows * cols) + " tiles to maps/");
    }
}
