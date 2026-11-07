package tw.majin.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import tw.majin.MajinExplosionMod;
import tw.majin.visual.ChargeRules;

/** Server->client: an anchored 3-D charging sphere and its running wind animation. */
public final class ChargeSyncPayload {
    public static final Identifier ID = new Identifier(MajinExplosionMod.MOD_ID, "charge_sync");

    private ChargeSyncPayload() {}

    public static void write(PacketByteBuf buffer, Snapshot snapshot) {
        buffer.writeUuid(snapshot.casterId());
        buffer.writeIdentifier(snapshot.dimension());
        buffer.writeDouble(snapshot.x());
        buffer.writeDouble(snapshot.y());
        buffer.writeDouble(snapshot.z());
        buffer.writeVarInt(snapshot.chargeTicks());
        buffer.writeVarInt(snapshot.visualTicks());
        buffer.writeBoolean(snapshot.active());
    }

    public static Snapshot read(PacketByteBuf buffer) {
        UUID id = buffer.readUuid();
        Identifier dimension = buffer.readIdentifier();
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        int chargeTicks = buffer.readVarInt();
        int visualTicks = buffer.readVarInt();
        boolean active = buffer.readBoolean();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30000000 || Math.abs(z) > 30000000
                || chargeTicks < 0 || chargeTicks > ChargeRules.MAX_TICKS
                || visualTicks < 0 || visualTicks > 10000000) {
            throw new IllegalArgumentException("Invalid charge snapshot");
        }
        return new Snapshot(id, dimension, x, y, z, chargeTicks, visualTicks, active);
    }

    public record Snapshot(UUID casterId, Identifier dimension,
                           double x, double y, double z,
                           int chargeTicks, int visualTicks, boolean active) {}
}
