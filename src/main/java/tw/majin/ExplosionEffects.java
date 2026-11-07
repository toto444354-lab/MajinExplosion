package tw.majin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import tw.majin.network.BlastSyncPayload;
import tw.majin.visual.BlastGeometry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Server-authoritative expanding blast. The visual wave and block-carving job are
 * intentionally separate: the former finishes promptly; the latter is bounded
 * and can continue after the caster dies. Never force-load a chunk.
 */
public final class ExplosionEffects {
    public static final int VISUAL_TICKS = BlastGeometry.DURATION_TICKS;
    public static final int MAX_RADIUS = BlastGeometry.MAX_RADIUS;
    private static final int MAX_SIMULTANEOUS_JOBS = 2;
    private static final int MAX_BLOCK_SCANS_PER_JOB_TICK = 4096;
    private static final int MAX_COLUMNS_PER_JOB_TICK = 256;
    private static final List<Blast> BLASTS = new ArrayList<>();
    private static final Set<UUID> PENDING_DEATHS = new HashSet<>();
    private static final DustParticleEffect GOLD =
            new DustParticleEffect(new Vector3f(1.0F, 0.72F, 0.1F), 2.0F);
    private static final DustParticleEffect WHITE =
            new DustParticleEffect(new Vector3f(1.0F, 0.98F, 0.8F), 2.3F);

    private ExplosionEffects() {}

    public static boolean isDetonating(UUID uuid) {
        for (Blast blast : BLASTS) {
            if (!blast.visualFinished && blast.casterId.equals(uuid)) return true;
        }
        return false;
    }

    /** Called on the server thread. Does not consume charge if capacity is full. */
    public static boolean start(ServerPlayerEntity player, int chargeTicks, AbilityConfig config) {
        if (!player.isAlive() || isDetonating(player.getUuid()) || BLASTS.size() >= MAX_SIMULTANEOUS_JOBS) {
            return false;
        }
        double charge = Math.min(1.0, chargeTicks / 200.0);
        int radius = Math.max(2, (int) Math.ceil(MAX_RADIUS * charge));
        Blast blast = new Blast(player, radius, charge, config.explosionTerrainEnabled);
        BLASTS.add(blast);
        blast.broadcastSnapshot();
        // One server-side sound event reaches nearby players without extra packets.
        blast.world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.PLAYERS, 1.8F, 0.7F);
        player.sendMessage(Text.literal("魔人自爆發動！逆時針能量向外擴張，半徑 " + radius + " 格。"), false);
        return true;
    }

    public static void tick(MinecraftServer server, AbilityConfig config) {
        Iterator<Blast> iterator = BLASTS.iterator();
        while (iterator.hasNext()) {
            Blast blast = iterator.next();
            blast.tick(server, config);
            if (blast.visualFinished && (blast.carver == null || blast.carver.isDone())) {
                iterator.remove();
            }
        }
    }

    /** Provide snapshots for late joiners; repeated snapshots also cover dimension changes. */
    public static void syncToPlayer(ServerPlayerEntity player) {
        for (Blast blast : BLASTS) {
            if (!blast.visualFinished && player.getServerWorld() == blast.world) {
                blast.sendSnapshot(player);
            }
        }
    }

    /** Never persist invulnerability or no-gravity state on disconnect. */
    public static void onDisconnect(ServerPlayerEntity player) {
        for (Blast blast : BLASTS) {
            if (blast.casterId.equals(player.getUuid()) && !blast.visualFinished) {
                blast.restoreCaster(player);
            }
        }
    }

    public static void onJoin(ServerPlayerEntity player) {
        if (PENDING_DEATHS.remove(player.getUuid())) {
            killCaster(player);
        }
    }

    public static void shutdown(MinecraftServer server) {
        for (Blast blast : BLASTS) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(blast.casterId);
            if (player != null && !blast.visualFinished) blast.restoreCaster(player);
        }
        BLASTS.clear();
        PENDING_DEATHS.clear();
    }

    private static void killCaster(ServerPlayerEntity player) {
        if (!player.isAlive()) return;
        player.damage(player.getDamageSources().genericKill(), Float.MAX_VALUE);
        if (player.isAlive()) player.kill();
    }

    private static final class Blast {
        private final UUID casterId;
        private final UUID blastId = UUID.randomUUID();
        private final ServerWorld world;
        private final Vec3d anchor;
        private final Vec3d center;
        private final ServerPlayerEntity originalPlayer;
        private final boolean originallyInvulnerable;
        private final boolean originallyNoGravity;
        private final int radius;
        private final double charge;
        private final TerrainCarver carver;
        private final Set<UUID> damagedEntities = new HashSet<>();
        private int age;
        private boolean visualFinished;

        private Blast(ServerPlayerEntity caster, int radius, double charge, boolean destroyTerrain) {
            this.casterId = caster.getUuid();
            this.world = caster.getServerWorld();
            this.anchor = caster.getPos();
            this.center = this.anchor.add(0.0, 1.0, 0.0);
            this.originalPlayer = caster;
            this.originallyInvulnerable = caster.isInvulnerable();
            this.originallyNoGravity = caster.hasNoGravity();
            this.radius = radius;
            this.charge = charge;
            int down = Math.max(3, (int) Math.ceil(56.0 * charge));
            int up = Math.max(2, (int) Math.ceil(35.0 * charge));
            this.carver = destroyTerrain ? new TerrainCarver(world, caster, center, radius, down, up) : null;
        }

        private void tick(MinecraftServer server, AbilityConfig config) {
            if (!visualFinished) {
                age++;
                ServerPlayerEntity caster = server.getPlayerManager().getPlayer(casterId);
                if (caster != null && caster.isAlive() && caster.getServerWorld() == world) {
                    lockCaster(caster);
                    if (age % 20 == 0) {
                        caster.sendMessage(Text.literal("自爆擴張：" + (int) Math.floor(currentRadius()) +
                                " / " + radius + " 格"), true);
                    }
                }
                // Nearby vanilla particles are a fallback/accent; the large, continuous
                // shell is a client-rendered mesh synchronized via a small S2C packet.
                if (age % 6 == 0) renderCoreAccent();
                if (age % 20 == 0) broadcastSnapshot();
                if (age % 4 == 0 && config.explosionDamageEnabled) hitWave(caster);
                if (age >= VISUAL_TICKS) {
                    visualFinished = true;
                    if (caster != null) {
                        restoreCaster(caster);
                        killCaster(caster);
                    } else {
                        // In-memory obligation when the caster logged out mid-explosion.
                        // It is cleared if the server is restarted (documented limitation).
                        PENDING_DEATHS.add(casterId);
                    }
                    broadcastSnapshot(); // Final age tells clients to remove this blast.
                    world.spawnParticles(ParticleTypes.FLASH, center.x, center.y, center.z,
                            1, 0.0, 0.0, 0.0, 0.0);
                    world.playSound(null, caster == null ? originalPlayer.getBlockPos() : caster.getBlockPos(),
                            SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.1F, 0.55F);
                }
            }
            if (carver != null) {
                carver.tick(currentRadius(), config.maxExplosionBlockRemovalsPerTick);
            }
        }

        private double currentRadius() {
            return BlastGeometry.waveRadius(radius, age);
        }

        private void lockCaster(ServerPlayerEntity caster) {
            caster.setInvulnerable(true);
            caster.setNoGravity(true);
            caster.setVelocity(Vec3d.ZERO);
            caster.velocityModified = true;
            caster.fallDistance = 0.0F;
            // Position belongs to the server, regardless of client movement packets.
            if (caster.getPos().squaredDistanceTo(anchor) > 0.0004) {
                caster.requestTeleport(anchor.x, anchor.y, anchor.z);
            }
        }

        private void restoreCaster(ServerPlayerEntity caster) {
            caster.setInvulnerable(originallyInvulnerable);
            caster.setNoGravity(originallyNoGravity);
            caster.setVelocity(Vec3d.ZERO);
            caster.velocityModified = true;
        }

        private void broadcastSnapshot() {
            // Do not broadcast dozens of particles per tick to every player;
            // the client reconstructs the shell from these low-frequency snapshots.
            for (ServerPlayerEntity viewer : PlayerLookup.around(world, center, radius + 96.0)) {
                sendSnapshot(viewer);
            }
        }

        private void sendSnapshot(ServerPlayerEntity viewer) {
            if (!ServerPlayNetworking.canSend(viewer, BlastSyncPayload.ID)) return;
            PacketByteBuf buf = PacketByteBufs.create();
            BlastSyncPayload.write(buf, new BlastSyncPayload.Snapshot(blastId, world.getRegistryKey().getValue(),
                    center.x, center.y, center.z, radius, age, (float) charge));
            ServerPlayNetworking.send(viewer, BlastSyncPayload.ID, buf);
        }

        /** Small server particles only; large shell is rendered by each client. */
        private void renderCoreAccent() {
            double phase = BlastGeometry.counterclockwisePhase(age, charge);
            double coreRadius = 1.4 + 1.5 * Math.sin(age * 0.10) * Math.sin(age * 0.10);
            for (int n = 0; n < 16; n++) {
                double a = phase + n * (Math.PI * 2.0 / 16.0);
                world.spawnParticles(n % 3 == 0 ? WHITE : GOLD,
                        center.x + coreRadius * Math.cos(a), center.y + (n % 5) * 0.28,
                        center.z + coreRadius * Math.sin(a), 1, 0.0, 0.0, 0.0, 0.0);
            }
            world.spawnParticles(ParticleTypes.END_ROD, center.x, center.y, center.z,
                    6, 1.5, 1.5, 1.5, 0.01);
        }

        private void hitWave(ServerPlayerEntity caster) {
            double distance = currentRadius();
            double vertical = BlastGeometry.verticalRadius(distance);
            if (distance <= 0.01) return;
            Box bounds = new Box(center.x - distance, center.y - vertical, center.z - distance,
                    center.x + distance, center.y + vertical, center.z + distance);
            for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, bounds,
                    e -> e.isAlive() && !e.getUuid().equals(casterId) && !e.isSpectator())) {
                Vec3d offset = entity.getPos().add(0.0, entity.getHeight() * 0.5, 0.0).subtract(center);
                if (!BlastGeometry.inWave(offset.x, offset.y, offset.z, distance)) continue;
                if (!damagedEntities.add(entity.getUuid())) continue;
                entity.damage(world.getDamageSources().explosion(null, caster == null ? originalPlayer : caster),
                        9.0F);
                double length = Math.max(0.01, offset.length());
                entity.addVelocity(offset.x / length * 1.6, 0.5, offset.z / length * 1.6);
            }
        }
    }

    /**
     * Radial-priority traversal of 2D columns, using O(radius) queue entries,
     * not a radius^3 block list. Each horizontal column is visited once.
     * A pending column can continue on the next tick without exceeding budgets.
     */
    static final class TerrainCarver {
        private final ServerWorld world;
        private final ServerPlayerEntity owner;
        private final Vec3d center;
        private final int radius;
        private final int down;
        private final int up;
        private final PriorityQueue<Column> frontier =
                new PriorityQueue<>(Comparator.comparingInt(c -> c.distanceSquared));
        private Column current;
        private int cursorY;
        private int endY;

        TerrainCarver(ServerWorld world, ServerPlayerEntity owner, Vec3d center,
                      int radius, int down, int up) {
            this.world = world;
            this.owner = owner;
            this.center = center;
            this.radius = radius;
            this.down = down;
            this.up = up;
            for (int dz = -radius; dz <= radius; dz++) {
                int maxX = (int) Math.floor(Math.sqrt((double) radius * radius - dz * dz));
                frontier.add(new Column(0, dz, +1, maxX));
                if (maxX >= 1) frontier.add(new Column(-1, dz, -1, maxX));
            }
        }

        boolean isDone() { return current == null && frontier.isEmpty(); }

        void tick(double waveRadius, int maxRemovals) {
            int scans = 0;
            int removals = 0;
            int columns = 0;
            while (scans < MAX_BLOCK_SCANS_PER_JOB_TICK &&
                    removals < maxRemovals && columns < MAX_COLUMNS_PER_JOB_TICK) {
                if (current == null) {
                    Column candidate = frontier.peek();
                    if (candidate == null || candidate.distanceSquared > waveRadius * waveRadius) break;
                    frontier.poll();
                    int nextX = candidate.dx + candidate.direction;
                    if (Math.abs(nextX) <= candidate.maxX) {
                        frontier.add(new Column(nextX, candidate.dz,
                                candidate.direction, candidate.maxX));
                    }
                    columns++;
                    int x = (int) Math.floor(center.x) + candidate.dx;
                    int z = (int) Math.floor(center.z) + candidate.dz;
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    double profile = Math.sqrt(Math.max(0.0, 1.0 -
                            candidate.distanceSquared / (double) (radius * radius)));
                    int low = (int) Math.ceil(center.y - down * profile);
                    int high = (int) Math.floor(center.y + up * profile);
                    cursorY = Math.max(world.getBottomY(), low);
                    endY = Math.min(world.getTopY() - 1, high);
                    if (cursorY > endY) continue;
                    current = candidate;
                }

                int x = (int) Math.floor(center.x) + current.dx;
                int z = (int) Math.floor(center.z) + current.dz;
                // The chunk may have unloaded between ticks while the column was paused.
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    current = null;
                    continue;
                }
                while (cursorY <= endY && scans < MAX_BLOCK_SCANS_PER_JOB_TICK &&
                        removals < maxRemovals) {
                    BlockPos pos = new BlockPos(x, cursorY++, z);
                    scans++;
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || state.hasBlockEntity() ||
                            state.getHardness(world, pos) < 0.0F ||
                            !world.canPlayerModifyAt(owner, pos)) continue;
                    // Notify clients, skip drops. Omit cascaded neighbor updates for safety.
                    if (world.setBlockState(pos, Blocks.AIR.getDefaultState(),
                            Block.NOTIFY_LISTENERS | Block.SKIP_DROPS)) {
                        removals++;
                    }
                }
                if (cursorY > endY) current = null;
            }
        }

        private static final class Column {
            private final int dx;
            private final int dz;
            private final int direction;
            private final int maxX;
            private final int distanceSquared;

            private Column(int dx, int dz, int direction, int maxX) {
                this.dx = dx;
                this.dz = dz;
                this.direction = direction;
                this.maxX = maxX;
                this.distanceSquared = dx * dx + dz * dz;
            }
        }
    }
}
