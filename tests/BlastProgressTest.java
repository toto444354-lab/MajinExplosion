import tw.majin.visual.BlastProgress;
import tw.majin.visual.BlastGeometry;

public final class BlastProgressTest {
    private static void verify(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        verify(BlastProgress.advance(0, 200, 0.0, true) == 0, "uncarved blast expanded");
        verify(BlastProgress.advance(0, 200, 30.0, true) == 1, "normal step skipped");
        int age = 18;
        for (int i = 0; i < 100; i++) {
            age = BlastProgress.advance(age, 200, 30.0, true);
        }
        verify(age == 18, "visual overtook completed crater radius");
        verify(BlastGeometry.waveRadius(200, age) <= 30.0, "wavefront ahead of terrain");
        int withRoom = BlastProgress.advance(age, 200, 38.0, true);
        verify(withRoom == age + 1, "did not resume when terrain completed");
        verify(BlastProgress.advance(BlastGeometry.DURATION_TICKS - 1, 200, 200.0, true) == BlastGeometry.DURATION_TICKS, "did not finish at radius");
        verify(BlastProgress.advance(0, 200, 0.0, false) == 1, "non-terrain wave stalled");
        verify(BlastProgress.advance(BlastGeometry.DURATION_TICKS - 1, 200, 0.0, false) == BlastGeometry.DURATION_TICKS, "non-terrain wave incomplete");
        verify(BlastProgress.advance(BlastGeometry.DURATION_TICKS, 200, 200.0, true) == BlastGeometry.DURATION_TICKS, "overrun");
        System.out.println("PASS: stage9 terrain-linked progress stalls, resumes and completes");
    }
}
