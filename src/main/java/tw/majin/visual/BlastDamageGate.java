package tw.majin.visual;

/** Testable cadence: 9 HP once per second per entity while inside the blast. */
public final class BlastDamageGate {
    public static final int INTERVAL_TICKS = 20;
    private BlastDamageGate() {}
    public static boolean canHit(int elapsedTicks, Integer previousAcceptedHitTick) {
        return previousAcceptedHitTick == null
                || elapsedTicks - previousAcceptedHitTick >= INTERVAL_TICKS;
    }
}
