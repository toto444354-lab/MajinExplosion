package tw.majin.visual;

/** Pure Java charging rules, separated from Minecraft for repeatable tests. */
public final class ChargeRules {
    public static final int MAX_TICKS = 200;

    private ChargeRules() {}

    /** Called only while G is held. At the cap, it stays full until H or G-up. */
    public static int advance(int ticks) {
        return Math.min(MAX_TICKS, Math.max(0, ticks) + 1);
    }

    public static boolean canRelease(int ticks) {
        return ticks > 0;
    }
}
