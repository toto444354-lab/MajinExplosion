package tw.majin.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import tw.majin.MajinExplosionMod;
import tw.majin.visual.BlastGeometry;

/** S2C snapshot; gameplay remains entirely server-authoritative. */
public final class BlastSyncPayload {
    public static final Identifier ID = new Identifier(MajinExplosionMod.MOD_ID, "blast_sync");

    private BlastSyncPayload() {}

    public static void write(PacketByteBuf buf, Snapshot snapshot) {
        buf.writeUuid(snapshot.id());
        buf.writeIdentifier(snapshot.dimension());
        buf.writeDouble(snapshot.x());
        buf.writeDouble(snapshot.y());
        buf.writeDouble(snapshot.z());
        buf.writeVarInt(snapshot.radius());
        buf.writeVarInt(snapshot.age());
        buf.writeFloat(snapshot.charge());
    }

    public static Snapshot read(PacketByteBuf buf) {
        UUID id = buf.readUuid();
        Identifier dimension = buf.readIdentifier();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        int radius = buf.readVarInt();
        int age = buf.readVarInt();
        float charge = buf.readFloat();
        // Reject invalid server data before it reaches a render call.
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) ||
                Math.abs(x) > 30000000 || Math.abs(z) > 30000000 ||
                radius < 2 || radius > BlastGeometry.MAX_RADIUS ||
                age < 0 || age > BlastGeometry.DURATION_TICKS ||
                !Float.isFinite(charge) || charge < 0 || charge > 1) {
            throw new IllegalArgumentException("Invalid blast snapshot");
        }
        return new Snapshot(id, dimension, x, y, z, radius, age, charge);
    }

    public record Snapshot(UUID id, Identifier dimension, double x, double y, double z,
                           int radius, int age, float charge) {}
}
