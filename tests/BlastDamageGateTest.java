import tw.majin.visual.BlastDamageGate;
import tw.majin.visual.BlastProgress;
import tw.majin.visual.BlastGeometry;

public final class BlastDamageGateTest {
    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        check(BlastDamageGate.canHit(4, null), "new entity inside blast must get hit");
        check(!BlastDamageGate.canHit(8, 4), "damage repeated before a second");
        check(!BlastDamageGate.canHit(20, 4), "damage repeated before 20 ticks");
        check(BlastDamageGate.canHit(24, 4), "inside entity not hurt at next second");
        check(BlastDamageGate.canHit(44, 24), "damage must keep repeating");
        check(BlastProgress.advanceToward(0, 10, 200, 0, true) == 0,
                "visible sphere outran excavation");
        check(BlastProgress.advanceToward(0, 10, 200, 200, false) == 4,
                "rapid catch-up should remain smooth");
        check(BlastProgress.advanceToward(20, 60, 200, 40, true) == 24,
                "catch-up ignored crater completion radius");
        check(BlastGeometry.DURATION_TICKS == 120, "target visual time must be six seconds");
        System.out.println("PASS: damage every 20 ticks, bounded terrain-synced catch-up");
    }
}
