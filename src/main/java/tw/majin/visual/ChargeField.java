package tw.majin.visual;

/** Shared pure-Java gameplay/visual boundary for the spherical charge field. */
public final class ChargeField {
    private ChargeField() {}

    public static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Spherical (not ellipsoidal) collision and visible surface radius. */
    public static double sphereRadius(double progress) {
        return 0.7 + 1.55 * clamp(progress);
    }

    public static double suctionRadius(double progress) {
        return 6.0 + 18.0 * clamp(progress);
    }

    /** Wind streamlines travel from the outer suction edge to the sphere surface. */
    public static double windRadius(double progress, double inwards) {
        return sphereRadius(progress) +
                (suctionRadius(progress) - sphereRadius(progress)) * (1.0 - clamp(inwards));
    }

    /** x=cos(a), z=sin(a); increasing angle is clockwise when seen from above. */
    public static double clockwisePhase(double ageTicks, double progress) {
        return ageTicks * (0.075 + 0.15 * clamp(progress));
    }

    /** Mob midpoint must stay OUTSIDE the shell, including its own half-width. */
    public static double mobContactRadius(double progress, double mobWidth) {
        return sphereRadius(progress) + Math.max(0.25, mobWidth * 0.5) + 0.07;
    }

    /** Horizontal clearance from the shell at a given relative height. */
    public static double minimumHorizontalDistance(double contactRadius, double verticalOffset) {
        return Math.sqrt(Math.max(0.0, contactRadius * contactRadius - verticalOffset * verticalOffset));
    }

    /** Closeness to the actual spherical shell, not distance to the player's hitbox. */
    public static boolean touchesShell(double dx, double dy, double dz,
                                       double progress, double mobWidth) {
        double boundary = mobContactRadius(progress, mobWidth);
        return dx * dx + dy * dy + dz * dz <= (boundary + 0.16) * (boundary + 0.16);
    }
}
