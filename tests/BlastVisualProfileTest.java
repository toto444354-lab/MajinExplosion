import tw.majin.visual.BlastGeometry;
import tw.majin.visual.BlastVisualProfile;

/** Run against real Java classes without the Minecraft/Fabric classpath. */
public final class BlastVisualProfileTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(BlastVisualProfile.flashAlpha(0) == 165, "release flash starts bright");
        check(BlastVisualProfile.flashAlpha(BlastVisualProfile.FLASH_TICKS) == 0,
                "release flash ends in 14 ticks");
        check(BlastVisualProfile.flashAlpha(-100) == 165, "negative flash age clamps");
        check(BlastVisualProfile.shockAlpha(BlastGeometry.DURATION_TICKS) == 0,
                "ring fades out at the end");
        check(BlastVisualProfile.haloAlpha(BlastGeometry.DURATION_TICKS) == 0,
                "halo fades out at the end");

        for (int cap : new int[]{2, 7, 20, 200}) {
            double previousRadius = -1.0;
            int previousFlash = Integer.MAX_VALUE;
            int previousShockAlpha = Integer.MAX_VALUE;
            int previousHaloAlpha = Integer.MAX_VALUE;
            for (int tick = 0; tick <= 300; tick++) {
                double shock = BlastVisualProfile.shockRadius(cap, tick);
                double flashRadius = BlastVisualProfile.flashRadius(cap, tick);
                int flash = BlastVisualProfile.flashAlpha(tick);
                int shockAlpha = BlastVisualProfile.shockAlpha(tick);
                int haloAlpha = BlastVisualProfile.haloAlpha(tick);
                check(shock >= previousRadius, "shock radius must expand");
                check(shock >= 0.0 && shock <= cap, "shock radius cap");
                check(flashRadius >= 0.0 && flashRadius <= cap, "flash radius cap");
                check(flash >= 0 && flash <= previousFlash, "flash fades monotonically");
                check(shockAlpha >= 0 && shockAlpha <= previousShockAlpha, "shock fades");
                check(haloAlpha >= 0 && haloAlpha <= previousHaloAlpha, "halo fades");
                previousRadius = shock;
                previousFlash = flash;
                previousShockAlpha = shockAlpha;
                previousHaloAlpha = haloAlpha;
            }
            check(Math.abs(previousRadius - cap) < 1.0e-9, "max radius reached");
        }

        double phase0 = BlastGeometry.counterclockwisePhase(10, 1);
        double phase1 = BlastGeometry.counterclockwisePhase(11, 1);
        check(phase1 < phase0, "bright lobes must turn counterclockwise");
        check(BlastVisualProfile.stripeAlpha(100, phase0, phase0) >
                        BlastVisualProfile.stripeAlpha(100, phase0 + Math.PI / 6, phase0),
                "lobe intensity uses changing angle");
        System.out.println("PASS: stage6 real Java visual profile, flash envelope, CCW shock rings and radius caps");
    }
}
