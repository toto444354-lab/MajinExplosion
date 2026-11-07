import tw.majin.visual.ChargeRules;

public final class ChargeRulesTest {
    public static void main(String[] args) {
        assertEquals(200, ChargeRules.MAX_TICKS);
        assertEquals(1, ChargeRules.advance(0));
        assertEquals(200, ChargeRules.advance(199));
        assertEquals(200, ChargeRules.advance(200));
        assertEquals(200, ChargeRules.advance(1000));
        if (ChargeRules.canRelease(0)) throw new AssertionError("Uncharged blast");
        if (!ChargeRules.canRelease(1)) throw new AssertionError("Valid held blast");
        if (!ChargeRules.canRelease(200)) throw new AssertionError("Full held blast");
        System.out.println("PASS: charge cap and H-release conditions");
    }
    private static void assertEquals(int wanted, int actual) {
        if (wanted != actual) throw new AssertionError(wanted + " != " + actual);
    }
}
