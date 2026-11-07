package tw.majin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import tw.majin.network.ChargeSyncPayload;
import tw.majin.visual.ChargeRules;
import tw.majin.visual.ChargeField;

/** Server-owned, hold-to-charge ability with an anchored caster. */
public final class AbilityController {
    private static final Map<UUID, ChargeState> STATES = new HashMap<>();
    private static AbilityConfig config;

    private AbilityController() {}

    public static void registerTickEvents() {
        config = AbilityConfig.load();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ExplosionEffects.tick(server, config);
            Iterator<Map.Entry<UUID, ChargeState>> iterator = STATES.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, ChargeState> entry = iterator.next();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                ChargeState state = entry.getValue();
                if (player == null || !player.isAlive() ||
                        player.getServerWorld() != state.world) {
                    broadcastCharge(entry.getKey(), state, false);
                    if (player != null) state.restoreGravity(player);
                    ChargeEffects.discardAll(state.effects);
                    iterator.remove();
                    continue;
                }
                // Always stay anchored while G is held, including at 10 seconds.
                state.lockPosition(player);
                state.ticks = ChargeRules.advance(state.ticks);
                state.visualTime++;
                double progress = ChargeField.clamp(state.ticks / (double) ChargeRules.MAX_TICKS);
                // Mesh and outward wind are client-drawn; ground smoke is throttled below.
                if (state.visualTime % 5 == 0) broadcastCharge(entry.getKey(), state, true);

                // Stage 12: NO suction of mobs and NO lifted blocks during charging.
                // Creatures only take contact damage if they voluntarily touch the sphere.
                ChargeEffects.damageOnContact(state.world, state.anchor,
                        player.getUuid(), state.effects, progress,
                        state.visualTime, config.mobDamageEnabled);
                if (config.chargingSmokeParticlesPerBurst > 0 &&
                        state.visualTime % config.chargingSmokeIntervalTicks == 0) {
                    ChargeEffects.spawnChargingDust(state.world, state.anchor,
                            state.visualTime, progress, config.chargingSmokeParticlesPerBurst);
                }
                if (state.visualTime % 10 == 0) {
                    player.sendMessage(Text.literal(String.format(
                            "魔人自爆：%.1f / 10.0 秒｜放開 G 取消｜按 H 自爆",
                            state.ticks / 20.0)), true);
                }
            }
        });
    }

    public static AbilityConfig getConfig() { return config; }

    public static void startCharge(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator() ||
                ExplosionEffects.isDetonating(player.getUuid()) ||
                STATES.containsKey(player.getUuid())) return;
        ChargeState state = new ChargeState(player);
        STATES.put(player.getUuid(), state);
        broadcastCharge(player.getUuid(), state, true);
        player.sendMessage(Text.literal("魔人自爆開始蓄力！放開 G 立即取消；按住 G 並按 H 釋放。"), true);
        if (!config.explosionTerrainEnabled) {
            player.sendMessage(Text.literal("提醒：爆炸挖洞目前在設定檔中關閉。"), false);
        }
    }

    /** G-up discards the entire charge; unlike stage 7, nothing is banked. */
    public static void stopCharge(ServerPlayerEntity player) {
        ChargeState state = STATES.remove(player.getUuid());
        if (state == null) return;
        broadcastCharge(player.getUuid(), state, false);
        state.restoreGravity(player);
        ChargeEffects.discardAll(state.effects);
        player.sendMessage(Text.literal("已取消蓄力（進度歸零）。"), true);
    }

    /** H is valid only while G is still held (even when fully charged). */
    public static void releaseExplosion(ServerPlayerEntity player) {
        if (ExplosionEffects.isDetonating(player.getUuid())) return;
        ChargeState state = STATES.get(player.getUuid());
        if (state == null || !ChargeRules.canRelease(state.ticks)) {
            player.sendMessage(Text.literal("請按住 G 蓄力，並在放開 G 之前按 H。"), true);
            return;
        }
        // ExplosionEffects snapshots the original no-gravity flag in its constructor.
        // Restore it BEFORE calling start(), so the blast won't keep charging's flag.
        state.restoreGravity(player);
        if (!ExplosionEffects.start(player, state.ticks, config)) {
            state.enableLock(player);
            player.sendMessage(Text.literal("爆炸工作已達上限，請繼續按住 G 或放開取消。"), true);
            return;
        }
        STATES.remove(player.getUuid());
        broadcastCharge(player.getUuid(), state, false);
        ChargeEffects.discardAll(state.effects);
    }

    /** The caller has the live player (important to restore the gravity flag). */
    public static void clear(ServerPlayerEntity player) {
        ChargeState state = STATES.remove(player.getUuid());
        if (state != null) {
            broadcastCharge(player.getUuid(), state, false);
            state.restoreGravity(player);
            ChargeEffects.discardAll(state.effects);
        }
    }

    /** Send current charge states to players joining midway through an ability. */
    public static void syncToPlayer(ServerPlayerEntity viewer) {
        for (Map.Entry<UUID, ChargeState> entry : STATES.entrySet()) {
            ChargeState state = entry.getValue();
            if (state.world == viewer.getServerWorld() &&
                    state.anchor.squaredDistanceTo(viewer.getPos()) < 100.0 * 100.0) {
                sendCharge(viewer, entry.getKey(), state, true);
            }
        }
    }

    private static void broadcastCharge(UUID caster, ChargeState state,
                                        boolean active) {
        for (ServerPlayerEntity viewer : PlayerLookup.around(state.world, state.anchor, 100.0)) {
            sendCharge(viewer, caster, state, active);
        }
    }

    private static void sendCharge(ServerPlayerEntity viewer, UUID caster, ChargeState state,
                                   boolean active) {
        if (!ServerPlayNetworking.canSend(viewer, ChargeSyncPayload.ID)) return;
        PacketByteBuf buffer = PacketByteBufs.create();
        ChargeSyncPayload.write(buffer, new ChargeSyncPayload.Snapshot(
                caster, state.world.getRegistryKey().getValue(),
                state.anchor.x, state.anchor.y + 1.0, state.anchor.z,
                state.ticks, state.visualTime, active));
        ServerPlayNetworking.send(viewer, ChargeSyncPayload.ID, buffer);
    }

    /** Integrated servers may restart inside the same Java process. */
    public static void shutdown(MinecraftServer server) {
        for (Map.Entry<UUID, ChargeState> entry : STATES.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) entry.getValue().restoreGravity(player);
            ChargeEffects.discardAll(entry.getValue().effects);
        }
        STATES.clear();
    }

    private static final class ChargeState {
        private final Vec3d anchor;
        private final net.minecraft.server.world.ServerWorld world;
        private final boolean originallyNoGravity;
        private int ticks;
        private int visualTime;
        private final ChargeEffects.EffectsState effects = new ChargeEffects.EffectsState();

        private ChargeState(ServerPlayerEntity player) {
            this.anchor = player.getPos();
            this.world = player.getServerWorld();
            this.originallyNoGravity = player.hasNoGravity();
            enableLock(player);
        }

        private void enableLock(ServerPlayerEntity player) {
            player.setNoGravity(true);
            lockPosition(player);
        }

        private void lockPosition(ServerPlayerEntity player) {
            player.setVelocity(Vec3d.ZERO);
            player.velocityModified = true;
            player.fallDistance = 0.0F;
            if (player.getPos().squaredDistanceTo(anchor) > 0.0004) {
                player.requestTeleport(anchor.x, anchor.y, anchor.z);
            }
        }

        private void restoreGravity(ServerPlayerEntity player) {
            player.setNoGravity(originallyNoGravity);
            player.setVelocity(Vec3d.ZERO);
            player.velocityModified = true;
            player.fallDistance = 0.0F;
        }
    }
}
