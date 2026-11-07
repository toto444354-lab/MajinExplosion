package tw.majin.visual;

/**
 * Dependency-free visual timing curves. These are presentation-only:
 * changing them must never change block removal, blast damage, or caster death.
 */
public final class BlastVisualProfile {
    public static final double FLASH_TICKS = 14.0;

    private BlastVisualProfile() {}

    /** A bright, short-lived white core rather than an invasive full-screen flash. */
    public static int flashAlpha(double ageTicks) {
        double remaining = 1.0 - clamp(ageTicks / FLASH_TICKS);
        return (int) Math.round(165.0 * remaining * remaining);
    }

    public static double flashRadius(int maximumRadius, double ageTicks) {
        return Math.min(Math.max(0, maximumRadius),
                1.2 + Math.max(0.0, Math.min(FLASH_TICKS, ageTicks)) * 1.3);
    }

    /** The shockfront never extends past the charge-limited horizontal radius. */
    public static double shockRadius(int maximumRadius, double ageTicks) {
        return Math.min(Math.max(0, maximumRadius),
                BlastGeometry.waveRadius(maximumRadius, ageTicks) * 1.04);
    }

    public static double shockWidth(double radius) {
        return Math.max(0.18, Math.min(2.8, radius * 0.025));
    }

    /** Both the ring and its six bright lobes fade as the blast expands. */
    public static int shockAlpha(double ageTicks) {
        double remaining = 1.0 - clamp(ageTicks / BlastGeometry.DURATION_TICKS);
        return (int) Math.round(114.0 * Math.sqrt(remaining));
    }

    public static int haloAlpha(double ageTicks) {
        double remaining = 1.0 - clamp(ageTicks / BlastGeometry.DURATION_TICKS);
        return (int) Math.round(16.0 * remaining);
    }

    public static int stripeAlpha(int baseAlpha, double theta, double phase) {
        double ridge = Math.max(0.0, Math.cos(6.0 * (theta - phase)));
        return Math.min(255, baseAlpha + (int) (baseAlpha * 0.8 * ridge * ridge * ridge));
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
