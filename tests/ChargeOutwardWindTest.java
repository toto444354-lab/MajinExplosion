import tw.majin.visual.ChargeWind;
import tw.majin.visual.ChargeField;

public final class ChargeOutwardWindTest {
    static double distance(double[] v) {
        return Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
    }
    static void ensure(boolean ok, String explanation) {
        if (!ok) throw new AssertionError(explanation);
    }
    public static void main(String[] args) {
        int[] sides = new int[6];
        for (int i = 0; i < 56; i++) {
            double[] atBall = ChargeWind.outwardPoint(0.0, i, 56, 1.0, 15.0);
            double[] middle = ChargeWind.outwardPoint(0.5, i, 56, 1.0, 15.0);
            double[] outside = ChargeWind.outwardPoint(1.0, i, 56, 1.0, 15.0);
            ensure(distance(atBall) < distance(middle) && distance(middle) < distance(outside),
                "a gust moves inward, not outward");
            ensure(distance(atBall) > ChargeField.sphereRadius(1.0),
                "gust begins inside solid charge ball");
            for (int axis = 0; axis < 3; axis++) {
                sides[axis * 2 + (outside[axis] >= 0 ? 1 : 0)]++;
            }
        }
        for (int side : sides) ensure(side >= 8, "missing dispersed gusts in one 3-D direction");
        ensure(distance(ChargeWind.outwardPoint(-1, 0, 56, 0, 0)) <
                distance(ChargeWind.outwardPoint(2, 0, 56, 0, 0)), "clamping is wrong");
        System.out.println("PASS: stage13 dispersed 3-D wind travels from ball outward");
    }
}
