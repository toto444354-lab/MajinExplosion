package tw.majin.visual;

/** Deterministic low-discrepancy distribution across the finished blast disk. */
public final class ResidualSmokePattern {
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private ResidualSmokePattern() {}

    public static double[] point(long index, double blastRadius) {
        double unit = fractional((index + 1L) * 0.6180339887498949);
        double distance = Math.max(0.0, blastRadius) * Math.sqrt(unit);
        double theta = index * GOLDEN_ANGLE;
        return new double[] {distance * Math.cos(theta), distance * Math.sin(theta)};
    }
    private static double fractional(double value) { return value - Math.floor(value); }
}
