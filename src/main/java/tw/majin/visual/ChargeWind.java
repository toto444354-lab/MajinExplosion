package tw.majin.visual;

/** Pure-Java 3-D wind coordinates; no dependence on Minecraft rendering APIs. */
public final class ChargeWind {
    private ChargeWind() {}

    /** A staggered wisp traveling inward from a Fibonacci-sphere direction. */
    public static double[] point(double inward, int index, int count,
                                 double progress, double clockwisePhase) {
        if (count < 1 || index < 0 || index >= count) {
            throw new IllegalArgumentException("Invalid wind stream index");
        }
        double t = ChargeField.clamp(inward);
        double height = 1.0 - 2.0 * (index + 0.5) / count;
        double horizontal = Math.sqrt(Math.max(0.0, 1.0 - height * height));
        double longitude = index * 2.399963229728653;
        // A light clockwise curl, but the primary movement is radial inward.
        double theta = longitude + clockwisePhase * 0.07 + t * (0.14 + 0.23 * progress);
        double radius = ChargeField.windRadius(progress, t);
        return new double[]{Math.cos(theta) * horizontal * radius,
                height * radius, Math.sin(theta) * horizontal * radius};
    }
}
