package tw.majin.visual;

/** Deterministic, dispersed charge-dust puffs. Pure Java: no Minecraft dependencies. */
public final class ChargeDustPattern {
    private ChargeDustPattern() {}

    private static double fract(double n) { return n - Math.floor(n); }

    /** [offsetX, offsetY, offsetZ, velocityX, velocityY, velocityZ]. */
    public static double[] sample(int tick, int index, double progress) {
        double p = Math.max(0.0, Math.min(1.0, progress));
        // Golden-angle distribution covers the full horizon, not a spinning wind ring.
        double angle = (index + fract(tick * 0.067 + index * 0.4142) * 0.7)
                * 2.399963229728653;
        double radial = 0.8 + 1.8 * p + 0.9 * fract(index * 0.754877666 + tick * 0.011);
        double x = Math.cos(angle) * radial;
        double z = Math.sin(angle) * radial;
        double y = 0.12 + 0.32 * fract(index * 0.319 + tick * 0.013);
        double vx = Math.cos(angle) * (0.018 + 0.030 * p);
        double vz = Math.sin(angle) * (0.018 + 0.030 * p);
        double vy = 0.02 + 0.022 * fract(index * 0.457 + tick * 0.013);
        return new double[] {x, y, z, vx, vy, vz};
    }
}
