package tw.majin.visual;

/** Server-authoritative progression. No client may outrun completed 3D terrain. */
public final class BlastProgress {
    private BlastProgress() {}

    /**
     * Advance to elapsed time if terrain work keeps up; otherwise cap at the
     * completely excavated ellipsoid. Limited catch-up avoids sudden jumps.
     */
    public static int advanceToward(int age, int elapsed, int maximumRadius,
                                    double completedRadius, boolean terrainEnabled) {
        int candidate = Math.max(age, Math.min(BlastGeometry.DURATION_TICKS, elapsed));
        if (terrainEnabled) {
            if (maximumRadius <= 0 || !Double.isFinite(completedRadius)) return age;
            double safe = Math.max(0.0, Math.min(maximumRadius, completedRadius));
            int allowed = (int) Math.floor(safe / maximumRadius
                    * BlastGeometry.DURATION_TICKS + 1.0e-8);
            candidate = Math.min(candidate, allowed);
        }
        return Math.max(age, Math.min(candidate, age + 4));
    }

    /** Legacy one-tick method retained for earlier tests. */
    public static int advance(int age, int maximumRadius,
                              double completedRadius, boolean terrainEnabled) {
        return advanceToward(age, age + 1, maximumRadius, completedRadius, terrainEnabled);
    }
}
