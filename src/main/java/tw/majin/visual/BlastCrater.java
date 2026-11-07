package tw.majin.visual;

/**
 * One consistent 3D ellipsoid for crater removal. The vertical axes are
 * deliberately asymmetric: a crater is deeper below its centre than above.
 * Block centre y+0.5 is tested, never a whole vertical column at once.
 * No Minecraft classes are used here, so the shape can be regression tested.
 */
public final class BlastCrater {
    private BlastCrater() {}

    public static double arrivalSquared(int dx, int dz, int blockY,
                                        double centreY, int radius, int down, int up) {
        double dy = blockY + 0.5 - centreY;
        double axis = dy < 0 ? down : up;
        double scaledY = dy * radius / Math.max(1.0, axis);
        return (double) dx * dx + (double) dz * dz + scaledY * scaledY;
    }

    /** Lowest affected block Y; high levels approach the centre at the edge. */
    public static int lowestY(int dx, int dz, double centreY,
                              int radius, int down) {
        double profile = profile(dx, dz, radius);
        return (int) Math.ceil(centreY - down * profile - 0.5);
    }

    /** Highest affected block Y; high levels approach the centre at the edge. */
    public static int highestY(int dx, int dz, double centreY,
                               int radius, int up) {
        double profile = profile(dx, dz, radius);
        return (int) Math.floor(centreY + up * profile - 0.5);
    }

    public static boolean contains(int dx, int dz, int blockY, double centreY,
                                   int radius, int down, int up, double waveRadius) {
        return arrivalSquared(dx, dz, blockY, centreY, radius, down, up)
                <= waveRadius * waveRadius + 1.0e-9;
    }

    private static double profile(int dx, int dz, int radius) {
        if (radius <= 0) return 0;
        double h2 = (double) dx * dx + (double) dz * dz;
        return Math.sqrt(Math.max(0.0, 1.0 - h2 / ((double) radius * radius)));
    }
}
