package tw.majin.visual;

/**
 * Authoritative wavefront controller. A blast may visually advance at most one
 * normal tick each server tick, and it cannot overtake fully processed columns.
 * With terrain enabled the 12-second animation is a MINIMUM, not a deadline.
 */
public final class BlastProgress {
    private BlastProgress() {}

    public static int advance(int age, int maximumRadius,
                              double completedRadius, boolean terrainEnabled) {
        int candidate = Math.min(BlastGeometry.DURATION_TICKS, Math.max(0, age) + 1);
        if (!terrainEnabled) return candidate;
        if (maximumRadius <= 0 || !Double.isFinite(completedRadius)) return age;
        double safe = Math.min(maximumRadius, Math.max(0, completedRadius));
        int allowed = (int) Math.floor((safe / maximumRadius)
                * BlastGeometry.DURATION_TICKS + 1.0e-8);
        return Math.max(age, Math.min(candidate, allowed));
    }
}
