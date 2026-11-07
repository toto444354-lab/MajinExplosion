package tw.majin.visual;

import java.util.ArrayDeque;

/**
 * Bounded monotonic radius queue. Processing a radius shell is O(1) per entry
 * rather than O(log N) per voxel as with a large priority heap.
 * All entries inserted while processing a shell must be in that shell or later.
 * This is server-thread-only and does not load chunks or manipulate Minecraft.
 */
public final class RadiusBucketQueue<T> {
    private final ArrayDeque<T>[] buckets;
    private int current;
    private int count;

    @SuppressWarnings("unchecked")
    public RadiusBucketQueue(int maxRadius) {
        if (maxRadius < 0) throw new IllegalArgumentException("Negative radius");
        buckets = (ArrayDeque<T>[]) new ArrayDeque[maxRadius + 1];
        for (int i = 0; i < buckets.length; i++) buckets[i] = new ArrayDeque<>();
    }

    public static int shellFor(double squaredDistance) {
        if (!Double.isFinite(squaredDistance) || squaredDistance < 0) {
            throw new IllegalArgumentException("Bad distance");
        }
        return (int) Math.ceil(Math.sqrt(squaredDistance) - 1.0e-9);
    }

    public void add(int shell, T item) {
        if (shell < current || shell >= buckets.length || item == null) {
            throw new IllegalArgumentException("Nonmonotonic or out-of-range shell: " + shell);
        }
        buckets[shell].addLast(item);
        count++;
    }

    public boolean isEmpty() { return count == 0; }
    public int size() { return count; }

    public int firstShell() {
        while (current < buckets.length && buckets[current].isEmpty()) current++;
        return count == 0 ? buckets.length : current;
    }

    public T poll() {
        if (isEmpty()) return null;
        T item = buckets[firstShell()].removeFirst();
        count--;
        return item;
    }
}
