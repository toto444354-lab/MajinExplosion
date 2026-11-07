import tw.majin.visual.ChargeDustPattern;

public class ChargeDustPatternTest {
    public static void main(String[] args) {
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int age = 0; age < 140; age += 4) {
            for (int index = 0; index < 10; index++) {
                double[] p = ChargeDustPattern.sample(age, index, 1.0);
                if (p.length != 6) throw new AssertionError("particle tuple");
                double radial = Math.hypot(p[0], p[2]);
                if (radial < 0.8 || radial > 3.6) throw new AssertionError("dust radius: " + radial);
                // Positive dot product means moving AWAY from center, never being sucked inward.
                if (p[0] * p[3] + p[2] * p[5] <= 0) throw new AssertionError("dust sucked inward");
                if (p[1] < 0.0 || p[4] <= 0.0) throw new AssertionError("no ascending dust");
                minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[2]); maxZ = Math.max(maxZ, p[2]);
            }
        }
        if (minX > -2.0 || maxX < 2.0 || minZ > -2.0 || maxZ < 2.0) {
            throw new AssertionError("dust not dispersed in four directions");
        }
        System.out.println("PASS: stage12 outward, dispersed, ground-level charging dust");
    }
}
