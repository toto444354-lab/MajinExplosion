import tw.majin.visual.CasterDeathRule;
import tw.majin.visual.ResidualSmokePattern;

public final class Stage10ExtraTest {
    public static void main(String[] args) {
        if (!CasterDeathRule.shouldKill(false, false) ||
            !CasterDeathRule.shouldKill(false, true) ||
            CasterDeathRule.shouldKill(true, false) ||
            !CasterDeathRule.shouldKill(true, true)) {
            throw new AssertionError("creative vs survival death rule incorrect");
        }
        for (int i = 0; i < 1000; i++) {
            double[] loc = ResidualSmokePattern.point(i, 200);
            if (!Double.isFinite(loc[0]) || !Double.isFinite(loc[1]) ||
                    Math.hypot(loc[0], loc[1]) > 200) {
                throw new AssertionError("smoke outside final crater");
            }
        }
        System.out.println("PASS: creative death switch, bounded residual smoke");
    }
}
