import tw.majin.visual.BlastGeometry;

public final class BlastVolumeDamageTest {
    static void verify(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        verify(BlastGeometry.overlapsBox(-1, -1, -1, 1, 1, 1, 10), "inside creature ignored");
        verify(BlastGeometry.overlapsBox(9.8, -0.4, 0, 10.5, 1.0, 1.0, 10), "surface creature ignored");
        verify(!BlastGeometry.overlapsBox(11, -0.4, 0, 12, 1, 1, 10), "outside creature hit");
        verify(BlastGeometry.overlapsBox(-0.5, 4.9, -0.3, 0.5, 5.9, 0.3, 10),
                "body crossing vertical ellipsoid cap was ignored");
        verify(!BlastGeometry.overlapsBox(-0.5, 5.8, -0.5, 0.5, 6.1, 0.5, 10),
                "fully outside vertical ellipsoid was hit");
        verify(BlastGeometry.overlapsBox(-2, -2, -2, 2, 2, 2, 0.1),
                "ball center should damage creature even at tiny radius");
        System.out.println("PASS: stage9 whole-volume entity bounding-box intersection");
    }
}
