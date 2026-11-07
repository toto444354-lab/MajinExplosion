import tw.majin.visual.BlastRimSpray;

public final class BlastRimSprayTest {
    private static void verify(boolean good, String message) {
        if (!good) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int count = 384;
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            double[] jet = BlastRimSpray.jet(i, 30.0);
            double x = jet[0], z = jet[1], vx = jet[2], vy = jet[3], vz = jet[4];
            verify(Double.isFinite(x + z + vx + vy + vz), "nonfinite rim debris trajectory");
            verify(Math.hypot(x, z) >= 29.5 && Math.hypot(x, z) <= 30.5,
                    "eruption outside current shockfront");
            verify(x * vx + z * vz > 0.0, "debris must fly outward, not inward");
            verify(vy >= 0.24, "debris must be launched upwards");
            verify(Math.hypot(vx, vz) >= 0.22, "debris has no radial velocity");
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }
        verify(minX < -28 && maxX > 28 && minZ < -28 && maxZ > 28,
                "rim emission doesn't cover all directions");
        System.out.println("PASS: radially-outward/upward, distributed boundary debris jets");
    }
}
