package tw.majin.visual;

/**
 * Shared, dependency-free blast geometry. Coordinates are interpreted from above:
 * x = cos(angle), z = sin(angle). A decreasing angle moves counterclockwise.
 * Keeping this class free of Minecraft types makes its mathematics unit-testable.
 */
public final class BlastGeometry {
    public static final int DURATION_TICKS = 240;
    public static final double VERTICAL_SCALE = 0.52;
    public static final int MAX_RADIUS = 200;

    private BlastGeometry() {}

    public static double waveRadius(int maximumRadius, double ageTicks) {
        double progress = Math.max(0.0, Math.min(1.0, ageTicks / DURATION_TICKS));
        return Math.max(0, Math.min(MAX_RADIUS, maximumRadius)) * progress;
    }

    public static double verticalRadius(double horizontalRadius) {
        return horizontalRadius * VERTICAL_SCALE;
    }

    public static double counterclockwisePhase(double ageTicks, double charge) {
        return -ageTicks * (0.16 + 0.12 * Math.max(0.0, Math.min(1.0, charge)));
    }

    public static double[] shellPoint(double radius, double latitude, double longitude) {
        double circle = radius * Math.cos(latitude);
        return new double[]{circle * Math.cos(longitude),
                verticalRadius(radius) * Math.sin(latitude),
                circle * Math.sin(longitude)};
    }

    public static boolean inWave(double dx, double dy, double dz, double currentRadius) {
        double scaledY = dy / VERTICAL_SCALE;
        return dx * dx + dz * dz + scaledY * scaledY <= currentRadius * currentRadius;
    }
}
