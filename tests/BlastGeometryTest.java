import tw.majin.visual.BlastGeometry;

/** Standalone main-style test; no Fabric classes or Gradle dependency required. */
public final class BlastGeometryTest {
    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        for (int radius : new int[]{2, 40, 200}) {
            double previous = 0;
            for (int tick = 0; tick <= BlastGeometry.DURATION_TICKS; tick++) {
                double current = BlastGeometry.waveRadius(radius, tick);
                check(current + 1e-9 >= previous, "wave must never shrink");
                check(current <= radius, "radius cap");
                previous = current;
            }
            check(Math.abs(previous - radius) < 1e-9, "radius must hit the configured maximum");
        }
        check(BlastGeometry.waveRadius(999, 240) == 200, "hard cap at 200");
        check(BlastGeometry.waveRadius(200, -12) == 0, "negative ages clamp to zero");
        check(BlastGeometry.waveRadius(200, 300) == 200, "old ages clamp to maximum");

        for (int t = 1; t <= 240; t++) {
            double phase0 = BlastGeometry.counterclockwisePhase(t - 1, 1.0);
            double phase1 = BlastGeometry.counterclockwisePhase(t, 1.0);
            check(phase1 < phase0, "counterclockwise phase must decrease");
            double dx0 = Math.cos(phase0), dz0 = Math.sin(phase0);
            double dx1 = Math.cos(phase1), dz1 = Math.sin(phase1);
            // Cross product (looking down on x/z) is negative for decreasing angles.
            check(dx0 * dz1 - dz0 * dx1 < 0, "wrong top-down rotation");
        }

        double[] p = BlastGeometry.shellPoint(200, Math.PI * 0.5, 0);
        check(Math.abs(p[1] - 104) < 1e-8, "top of ellipsoid at 0.52 R");
        check(BlastGeometry.inWave(100, 0, 0, 100), "boundary included");
        check(!BlastGeometry.inWave(100.01, 0, 0, 100), "outside rejected");
        check(BlastGeometry.inWave(0, 52, 0, 100), "vertical boundary included");
        check(!BlastGeometry.inWave(0, 52.01, 0, 100), "vertical outside rejected");
        System.out.println("PASS: actual Java-compiled blast geometry, CCW orientation and bounds");
    }
}
