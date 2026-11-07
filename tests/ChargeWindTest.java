import tw.majin.visual.ChargeField;
import tw.majin.visual.ChargeWind;

public final class ChargeWindTest {
    static void verify(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }
    static double length(double[] p) {
        return Math.sqrt(p[0]*p[0] + p[1]*p[1] + p[2]*p[2]);
    }
    public static void main(String[] args) {
        int[] signs = new int[6];
        final int count = 88;
        for (int n = 0; n < count; n++) {
            double[] far = ChargeWind.point(0, n, count, 1, 5);
            double[] middle = ChargeWind.point(0.5, n, count, 1, 5);
            double[] near = ChargeWind.point(1, n, count, 1, 5);
            verify(length(far) > length(middle) && length(middle) > length(near),
                    "wind does not flow inward");
            verify(Math.abs(length(near) - ChargeField.sphereRadius(1)) < 1e-7,
                    "wind passes through charge sphere");
            for (int axis = 0; axis < 3; axis++) {
                signs[axis * 2 + (far[axis] < 0 ? 0 : 1)]++;
            }
        }
        for (int face : signs) verify(face >= 12, "wind missing a 3D direction");
        System.out.println("PASS: stage9 distributed 3D suction wisps");
    }
}
