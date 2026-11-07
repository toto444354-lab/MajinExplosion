package tw.majin.visual;

/** Survival always self-destructs; creative mode depends on a config toggle. */
public final class CasterDeathRule {
    private CasterDeathRule() {}
    public static boolean shouldKill(boolean creative, boolean creativeDeathEnabled) {
        return !creative || creativeDeathEnabled;
    }
}
