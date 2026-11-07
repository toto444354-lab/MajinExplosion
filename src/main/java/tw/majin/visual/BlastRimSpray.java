package tw.majin.visual;

/** Deterministic, outward-and-upward rim eruptions; no Minecraft dependencies. */
public final class BlastRimSpray {
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private BlastRimSpray() {}

    /** [dx, dz, vx, vy, vz] relative to blast centre; velocity always points outward/up. */
    public static double[] jet(long index, double waveRadius) {
        double angle = index * GOLDEN_ANGLE;
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        double radialJitter = 0.8 * (fraction((index + 1) * 0.6180339887498949) - 0.5);
        double distance = Math.max(0.4, waveRadius + radialJitter);
        double horizontalSpeed = 0.22 + 0.28 * fraction((index + 3) * 0.7548776662466927);
        double upwardSpeed = 0.24 + 0.34 * fraction((index + 7) * 0.414213562373095);
        return new double[] {cosine * distance, sine * distance,
                cosine * horizontalSpeed, upwardSpeed, sine * horizontalSpeed};
    }

    private static double fraction(double value) { return value - Math.floor(value); }
}
