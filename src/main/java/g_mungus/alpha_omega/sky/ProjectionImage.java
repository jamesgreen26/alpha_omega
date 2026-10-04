package g_mungus.alpha_omega.sky;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Writes {@link PlanetProjection#project} as a square texture: one pixel per sample over a lap in x (left to right)
 * and z (top to bottom, so -Z is up), from -half a lap to +half a lap with spawn (0, 0) in the centre. Red is longitude (0 at -0.5 turns, 255 just under +0.5), green
 * is latitude (0 at the south pole, 255 at the north pole) and blue is the heading as a fraction of a full turn.
 * The image is 2×2 tiles of that square: all three channels together (top left), then red (top right), green (bottom
 * left) and blue (bottom right) alone. {@code size} is the size of one tile.
 *
 * <p>The file is named after the class of {@link PlanetProjection#CURRENT}, e.g.
 * {@code planet_projection_MeridianLoopProjection.png}, so images of different projections sit side by side.
 *
 * <p>Run after compiling:
 * {@code java -cp build/classes/java/main g_mungus.alpha_omega.sky.ProjectionImage [directory] [size]}.
 */
public final class ProjectionImage {

    private ProjectionImage() {
    }

    public static void main(String[] args) throws IOException {
        File file = new File(args.length > 0 ? args[0] : ".", "planet_projection_" + name(PlanetProjection.CURRENT) + ".png");
        int size = args.length > 1 ? Integer.parseInt(args[1]) : 512;
        BufferedImage image = new BufferedImage(2 * size, 2 * size, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                PlanetProjection.Position position = PlanetProjection.project(lapFraction(column, size), lapFraction(row, size));
                int red = channel(position.longitude() + 0.5) << 16;
                int green = channel(position.latitude() / Math.PI + 0.5) << 8;
                double turns = position.heading() / (2.0 * Math.PI);
                int blue = channel(turns - Math.floor(turns));
                image.setRGB(column, row, red | green | blue);
                image.setRGB(size + column, row, red);
                image.setRGB(column, size + row, green);
                image.setRGB(size + column, size + row, blue);
            }
        }
        ImageIO.write(image, "png", file);
        System.out.println("Wrote " + file.getAbsolutePath());
    }

    /** The projection's class name; a lambda's hidden class has no usable name, so it is just "Lambda". */
    private static String name(PlanetProjection.Projection projection) {
        Class<?> type = projection.getClass();
        return type.isHidden() ? "Lambda" : type.getSimpleName();
    }

    /** The lap fraction in [0, 1) at a pixel, with the tile's centre at 0 (spawn). */
    private static double lapFraction(int pixel, int size) {
        double fraction = (pixel + 0.5) / size - 0.5;
        return fraction - Math.floor(fraction);
    }

    private static int channel(double fraction) {
        return Math.max(0, Math.min(255, (int) Math.floor(fraction * 256.0)));
    }
}
