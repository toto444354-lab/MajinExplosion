package tw.majin.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import tw.majin.MajinExplosionMod;

/**
 * Small 1.20.1-compatible packet codec.
 * The packet contains only an action enum ordinal; the server validates all gameplay state.
 */
public final class AbilityPayload {
    public static final Identifier ID = new Identifier(MajinExplosionMod.MOD_ID, "ability");

    private AbilityPayload() {}

    public static void write(PacketByteBuf buf, Action action) {
        buf.writeVarInt(action.ordinal());
    }

    public static Action read(PacketByteBuf buf) {
        int ordinal = buf.readVarInt();
        Action[] values = Action.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return Action.INVALID;
        }
        return values[ordinal];
    }

    public enum Action {
        START_CHARGE,
        RELEASE_EXPLOSION,
        STOP_CHARGE,
        INVALID
    }
}
