import tw.majin.visual.ChargeField;

public final class ChargeFieldTest {
    public static void main(String[] args) {
        eq(0.7, ChargeField.sphereRadius(0));
        eq(2.25, ChargeField.sphereRadius(1));
        eq(6.0, ChargeField.suctionRadius(0));
        eq(24.0, ChargeField.suctionRadius(1));
        eq(24.0, ChargeField.windRadius(1, 0));
        eq(2.25, ChargeField.windRadius(1, 1));
        if (!(ChargeField.windRadius(1, 0.3) > ChargeField.windRadius(1, 0.8)))
            throw new AssertionError("Wind must move inwards toward the sphere");
        if (ChargeField.clockwisePhase(50, 0.5) <= 0)
            throw new AssertionError("Charge must rotate clockwise from above");
        double edge = ChargeField.mobContactRadius(1.0, 0.6);
        if (!ChargeField.touchesShell(edge, 0, 0, 1.0, 0.6))
            throw new AssertionError("Mob must collide with outer surface");
        if (ChargeField.touchesShell(edge + 1.0, 0, 0, 1.0, 0.6))
            throw new AssertionError("Approach must be harmless");
        eq(edge, ChargeField.minimumHorizontalDistance(edge, 0));
        eq(0, ChargeField.minimumHorizontalDistance(edge, edge + 1));
        System.out.println("PASS: spherical charge collision + clockwise direction");
    }
    static void eq(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.000001)
            throw new AssertionError(expected + " != " + actual);
    }
}
