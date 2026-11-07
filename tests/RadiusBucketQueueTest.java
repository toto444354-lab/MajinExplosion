import tw.majin.visual.RadiusBucketQueue;

public final class RadiusBucketQueueTest {
    private static void expect(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        RadiusBucketQueue<String> q = new RadiusBucketQueue<>(200);
        q.add(0, "core");
        q.add(4, "outer");
        expect(q.firstShell() == 0, "first shell should be zero");
        expect("core".equals(q.poll()), "core order");
        q.add(1, "inner");
        expect(q.firstShell() == 1, "inner shell available");
        expect("inner".equals(q.poll()), "inner before outer");
        expect(q.firstShell() == 4, "outer shell next");
        q.add(4, "another");
        expect("outer".equals(q.poll()), "FIFO per shell 1");
        expect("another".equals(q.poll()), "FIFO per shell 2");
        expect(q.isEmpty(), "empty after queue drained");
        boolean rejected = false;
        try { q.add(3, "backtrack"); } catch (IllegalArgumentException expected) { rejected = true; }
        expect(rejected, "must reject backtracking shells");
        expect(RadiusBucketQueue.shellFor(16.0) == 4, "shell boundary");
        expect(RadiusBucketQueue.shellFor(16.1) == 5, "shell interior");
        System.out.println("PASS RadiusBucketQueueTest");
    }
}
