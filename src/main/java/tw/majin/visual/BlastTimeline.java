package tw.majin.visual;

/**
 * Pure-Java client timeline math for reconciling periodic network snapshots.
 * An older snapshot may arrive after the renderer has advanced; never rewind
 * the blast and make the outward-growing shell visibly contract.
 */
public final class BlastTimeline {
    private BlastTimeline() {}

    public static int reconcileAge(int localAge, int serverAge) {
        int incoming = Math.max(0, Math.min(BlastGeometry.DURATION_TICKS, serverAge));
        return Math.max(Math.max(0, localAge), incoming);
    }

    public static int advanceAge(int age) {
        return Math.min(BlastGeometry.DURATION_TICKS + 2, Math.max(0, age) + 1);
    }
}
