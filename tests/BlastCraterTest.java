import tw.majin.visual.BlastCrater;

public final class BlastCraterTest {
    private static void test(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        int r = 40, down = 16, up = 12;
        double cy = 70.0;
        test(BlastCrater.lowestY(0, 0, cy, r, down) == 54, "centre deepest");
        test(BlastCrater.highestY(0, 0, cy, r, up) == 81, "centre highest");
        test(BlastCrater.lowestY(30, 0, cy, r, down) > 54, "edges must be shallower");
        test(BlastCrater.highestY(30, 0, cy, r, up) < 81, "edges must be shorter");
        test(BlastCrater.lowestY(40, 0, cy, r, down) >
                BlastCrater.highestY(40, 0, cy, r, up), "ellipsoid tapers to point");

        // At halfway expansion, even the centre must NOT be excavated down to
        // its final depth. This specifically rejects the old column/cylinder.
        test(BlastCrater.contains(0, 0, 69, cy, r, down, up, 20), "inner layer should vanish");
        test(!BlastCrater.contains(0, 0, 54, cy, r, down, up, 20),
                "full-depth cylindrical cut during early expansion");
        test(!BlastCrater.contains(22, 0, 69, cy, r, down, up, 20),
                "wave jumped past its horizontal radius");
        test(BlastCrater.contains(0, 0, 54, cy, r, down, up, 40),
                "deep central block should be reached by final wave");

        // Every individual voxel has its own deterministic arrival distance.
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dz * dz > r * r) continue;
            int low = BlastCrater.lowestY(dx, dz, cy, r, down);
            int high = BlastCrater.highestY(dx, dz, cy, r, up);
            for (int y = low; y <= high; y++) {
                test(BlastCrater.arrivalSquared(dx, dz, y, cy, r, down, up)
                        <= r * r + 1.0e-6, "final crater exceeded ellipsoid");
            }
        }
        System.out.println("PASS: 3D elliptical crater, no premature full-depth columns");
    }
}
