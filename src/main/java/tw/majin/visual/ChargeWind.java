package tw.majin.visual;

/** Pure-Java 3-D wind coordinates; no dependence on Minecraft rendering APIs. */
public final class ChargeWind {
    private ChargeWind() {}

    /** Legacy inward coordinate used only by historical mathematical tests. */
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

    /**
     * Stage 13: true outward-flowing gust. t=0 begins just beyond the charge
     * sphere; t=1 is far away. Fibonacci sphere samples spread gusts in 3-D.
     * Phase adds a light clockwise curl, but it never reverses radial travel.
     * All math is client-side, with no entity movement or world edits.
     */
    public static double[] outwardPoint(double t, int index, int count,
                                        double progress, double clockwisePhase) {
        if (count < 1 || index < 0 || index >= count) {
            throw new IllegalArgumentException("Invalid outward wind stream index");
        }
        double outward = ChargeField.clamp(t);
        double p = ChargeField.clamp(progress);
        double height = 1.0 - 2.0 * (index + 0.5) / count;
        double horizontal = Math.sqrt(Math.max(0.0, 1.0 - height * height));
        double longitude = index * 2.399963229728653;
        double angle = longitude + clockwisePhase * 0.035 + outward * 0.10;
        double radius = ChargeField.sphereRadius(p) + 0.20 + outward * (3.5 + 2.5 * p);
        return new double[] {
                Math.cos(angle) * horizontal * radius,
                height * radius,
                Math.sin(angle) * horizontal * radius
        };
    }

}
