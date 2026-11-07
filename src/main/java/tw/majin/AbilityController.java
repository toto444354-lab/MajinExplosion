package tw.majin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** Server-owned charge state, clockwise visuals, and stage-4 explosion activation. */
public final class AbilityController {
    private static final int MAX_TICKS = 200;
    private static final int MAX_GLOBAL_ACTIVE_DEBRIS = 128;
    private static final int MAX_NEW_DEBRIS_PER_SERVER_TICK = 6;
    private static final Map<UUID, ChargeState> STATES = new HashMap<>();
    private static AbilityConfig config;

    private AbilityController() {}

    public static void registerTickEvents() {
        config = AbilityConfig.load();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ExplosionEffects.tick(server, config);
            int activeDebris = STATES.values().stream()
                    .mapToInt(state -> state.effects.activeDebrisCount()).sum();
            int spawnBudget = Math.min(MAX_NEW_DEBRIS_PER_SERVER_TICK,
                    Math.max(0, MAX_GLOBAL_ACTIVE_DEBRIS - activeDebris));
            Iterator<Map.Entry<UUID, ChargeState>> iterator = STATES.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, ChargeState> entry = iterator.next();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                ChargeState state = entry.getValue();
                if (player == null || !player.isAlive()) {
                    ChargeEffects.discardAll(state.effects);
                    iterator.remove();
                    continue;
                }
                if (state.charging && state.ticks < MAX_TICKS) {
                    state.ticks++;
                    if (state.ticks == MAX_TICKS) state.charging = false;
                }
                state.visualTime++;
                double progress = MathHelper.clamp(state.ticks / (double) MAX_TICKS, 0.0, 1.0);
                if (state.visualTime % 2 == 0) drawClockwiseCharge(player, state, progress);

                ChargeEffects.tickDebris(player, state.effects, progress);
                if (state.charging) {
                    ChargeEffects.pullMobs(player, progress,
                            config.mobDamageEnabled && state.visualTime % 20 == 0);
                    // Token bucket: exactly up to N spawn opportunities / 20 ticks,
                    // not the rounded-up rate from integer tick intervals.
                    if (config.terrainAbsorptionEnabled && config.blocksPerSecond > 0) {
                        state.blockSpawnRemainder += config.blocksPerSecond;
                        if (state.blockSpawnRemainder >= 20) {
                            state.blockSpawnRemainder -= 20;
                            int allowance = Math.min(1, Math.min(spawnBudget,
                                    Math.max(0, config.maxActiveDebrisPerPlayer
                                            - state.effects.activeDebrisCount())));
                            int spawned = ChargeEffects.liftBlocks(player, state.effects, progress, allowance);
                            spawnBudget -= spawned;
                        }
                    }
                }
                if (state.visualTime % 10 == 0) {
                    player.sendMessage(Text.literal(String.format("魔人自爆蓄力：%.1f / 10.0 秒%s",
                            state.ticks / 20.0, state.charging ? "（蓄力中）" : "（已暫停）")), true);
                }
            }
        });
    }

    public static void startCharge(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator() ||
                ExplosionEffects.isDetonating(player.getUuid())) return;
        ChargeState state = STATES.computeIfAbsent(player.getUuid(), ignored -> new ChargeState());
        state.charging = state.ticks < MAX_TICKS;
    }

    public static void stopCharge(ServerPlayerEntity player) {
        ChargeState state = STATES.get(player.getUuid());
        if (state != null) state.charging = false;
    }

    public static void releaseExplosion(ServerPlayerEntity player) {
        if (ExplosionEffects.isDetonating(player.getUuid())) return;
        ChargeState state = STATES.get(player.getUuid());
        if (state == null || state.ticks == 0) {
            player.sendMessage(Text.literal("請先按住 G 蓄力。"), true);
            return;
        }
        if (!ExplosionEffects.start(player, state.ticks, config)) {
            player.sendMessage(Text.literal("目前爆炸工作已達上限，稍後再按 H。"), true);
            return; // Preserve accumulated charge if the blast cannot start.
        }
        STATES.remove(player.getUuid());
        ChargeEffects.discardAll(state.effects);
    }

    public static void clear(UUID uuid) {
        ChargeState state = STATES.remove(uuid);
        if (state != null) ChargeEffects.discardAll(state.effects);
    }

    /** Integrated servers may restart inside the same Java process. Never retain old worlds. */
    public static void shutdown() {
        for (ChargeState state : STATES.values()) {
            ChargeEffects.discardAll(state.effects);
        }
        STATES.clear();
    }

    private static void drawClockwiseCharge(ServerPlayerEntity player, ChargeState state, double progress) {
        double radius = 0.7 + 1.55 * progress;
        double centerX = player.getX();
        double centerY = player.getY() + 1.0;
        double centerZ = player.getZ();
        // From above, increasing angle in (x=cos(a), z=sin(a)) moves clockwise.
        double phase = state.visualTime * (0.075 + 0.15 * progress);
        for (int ring = 0; ring < 7; ring++) {
            double latitude = -Math.PI / 2.0 + Math.PI * (ring + 0.5) / 7.0;
            double ringRadius = radius * Math.cos(latitude);
            double y = centerY + radius * Math.sin(latitude);
            for (int dot = 0; dot < 14; dot++) {
                double angle = phase + dot * (Math.PI * 2.0 / 14.0) + ring * 0.24;
                double x = centerX + ringRadius * Math.cos(angle);
                double z = centerZ + ringRadius * Math.sin(angle);
                player.getServerWorld().spawnParticles(ParticleTypes.FLAME, x, y, z, 1, 0, 0, 0, 0);
                if (dot % 7 == 0) {
                    player.getServerWorld().spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }
        for (int i = 0; i < 14; i++) {
            double angle = phase * 1.35 + i * 0.62;
            double windRadius = radius + 0.45 + 0.12 * Math.sin(i * 0.8);
            double y = player.getY() + 0.25 + (i % 7) * (2.0 / 7.0);
            player.getServerWorld().spawnParticles(ParticleTypes.FLAME,
                    centerX + windRadius * Math.cos(angle), y,
                    centerZ + windRadius * Math.sin(angle), 1, 0, 0, 0, 0);
        }
    }

    private static final class ChargeState {
        private int ticks;
        private int visualTime;
        private int blockSpawnRemainder;
        private boolean charging;
        private final ChargeEffects.EffectsState effects = new ChargeEffects.EffectsState();
    }
}
