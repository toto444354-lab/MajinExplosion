import tw.majin.visual.BlastTimeline;
import tw.majin.visual.BlastGeometry;

public final class BlastTimelineTest {
    public static void main(String[] args) {
        require(BlastTimeline.reconcileAge(87, 80) == 87, "late snapshot rewinds animation");
        require(BlastTimeline.reconcileAge(80, 87) == 87, "newer snapshot not applied");
        require(BlastTimeline.reconcileAge(5, -20) == 5, "invalid negative age");
        require(BlastTimeline.reconcileAge(239, 999) == 240, "age cap");
        require(BlastTimeline.advanceAge(240) == 241, "one tick grace period");
        require(BlastTimeline.advanceAge(241) == 242, "terminal age");
        require(BlastTimeline.advanceAge(242) == 242, "terminal cap");
        int age = 0;
        double last = 0;
        for (int i = 0; i < 240; i++) {
            age = BlastTimeline.advanceAge(age);
            if (i % 20 == 0) age = BlastTimeline.reconcileAge(age, i);
            double radius = BlastGeometry.waveRadius(200, age);
            require(radius >= last, "sphere radius shrank");
            last = radius;
        }
        require(last == 200.0, "did not reach maximum radius");
        System.out.println("PASS: stage7 real Java timeline reconciliation and monotone wave growth");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
