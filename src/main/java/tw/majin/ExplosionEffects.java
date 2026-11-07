package tw.majin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.registry.tag.FluidTags;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.BlockStateParticleEffect;
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
import tw.majin.visual.BlastProgress;
import tw.majin.visual.BlastCrater;
import tw.majin.visual.BlastDamageGate;
import tw.majin.visual.CasterDeathRule;
import tw.majin.visual.ResidualSmokePattern;
import tw.majin.visual.BlastRimSpray;
import net.minecraft.world.Heightmap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Server-authoritative expanding blast. When terrain destruction is enabled,
 * the visible wave never outruns excavated 3D ellipsoid voxels.
 * Short look-ahead prevents unnecessary animation stalls on small blasts.
 * No chunk is force-loaded; world edits have strict per-tick limits.
 */
public final class ExplosionEffects {
    public static final int VISUAL_TICKS = BlastGeometry.DURATION_TICKS;
    public static final int MAX_RADIUS = BlastGeometry.MAX_RADIUS;
    private static final int MAX_SIMULTANEOUS_JOBS = 2;
    private static final int MAX_BLOCK_SCANS_PER_JOB_TICK = 32768;
    private static final int MAX_COLUMNS_PER_JOB_TICK = 4096;
    private static final int CARVE_LOOKAHEAD_TICKS = 16;
    private static final List<Blast> BLASTS = new ArrayList<>();
    private static final List<SmokeCloud> SMOKE_CLOUDS = new ArrayList<>();
    private static final int MAX_SMOKE_CLOUDS = 4;
    private static final Set<UUID> PENDING_DEATHS = new HashSet<>();
    private static final DustParticleEffect GOLD =
            new DustParticleEffect(new Vector3f(1.0F, 0.72F, 0.1F), 2.0F);
    private static final DustParticleEffect WHITE =
            new DustParticleEffect(new Vector3f(1.0F, 0.98F, 0.8F), 2.3F);
    private static final DustParticleEffect EARTH_DUST =
            new DustParticleEffect(new Vector3f(0.65F, 0.53F, 0.40F), 1.7F);

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
        if (!config.explosionTerrainEnabled) {
            player.sendMessage(Text.literal("目前未開啟地形破壞：請關閉遊戲後將 config/majin_explosion.properties 的 explosion_terrain_enabled 改為 true。"), false);
        } else {
            player.sendMessage(Text.literal("已啟用地形同步：巨大爆炸會依挖洞進度放慢擴張，僅破壞已載入區塊。"), false);
        }
        return true;
    }

    public static void tick(MinecraftServer server, AbilityConfig config) {
        Iterator<SmokeCloud> smoke = SMOKE_CLOUDS.iterator();
        while (smoke.hasNext()) {
            if (smoke.next().tick()) smoke.remove();
        }
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
        SMOKE_CLOUDS.clear();
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
        private final boolean creativeAtStart;
        private final int radius;
        private final double charge;
        private final TerrainCarver carver;
        private final Map<UUID, Integer> lastDamageTick = new HashMap<>();
        private int age;
        private int elapsedTicks;
        private boolean visualFinished;
        private long rimSample;
        private double lastSprayedRadius = -1.0;

        private Blast(ServerPlayerEntity caster, int radius, double charge, boolean destroyTerrain) {
            this.casterId = caster.getUuid();
            this.world = caster.getServerWorld();
            this.anchor = caster.getPos();
            this.center = this.anchor.add(0.0, 1.0, 0.0);
            this.originalPlayer = caster;
            this.originallyInvulnerable = caster.isInvulnerable();
            this.originallyNoGravity = caster.hasNoGravity();
            this.creativeAtStart = caster.isCreative();
            this.radius = radius;
            this.charge = charge;
            int down = Math.max(3, (int) Math.ceil(56.0 * charge));
            int up = Math.max(2, (int) Math.ceil(35.0 * charge));
            this.carver = destroyTerrain ? new TerrainCarver(world, caster, center, radius, down, up) : null;
        }

        private void tick(MinecraftServer server, AbilityConfig config) {
            if (!visualFinished) {
                elapsedTicks++;
                if (carver != null) {
                    // Excavation is allowed a small look-ahead, but the visible
                    // shell cannot overtake completed 3D ellipsoid cells.
                    double plannedRadius = BlastGeometry.waveRadius(radius,
                            Math.min(VISUAL_TICKS, elapsedTicks + CARVE_LOOKAHEAD_TICKS));
                    carver.tick(plannedRadius, config.maxExplosionBlockRemovalsPerTick,
                            config.explosionEvaporatesWater);
                    age = BlastProgress.advanceToward(age, elapsedTicks, radius,
                            carver.completedRadius(), true);
                } else {
                    age = BlastProgress.advanceToward(age, elapsedTicks, radius, radius, false);
                }
                ServerPlayerEntity caster = server.getPlayerManager().getPlayer(casterId);
                if (caster != null && caster.isAlive() && caster.getServerWorld() == world) {
                    lockCaster(caster);
                    if (elapsedTicks % 20 == 0) {
                        caster.sendMessage(Text.literal("自爆擴張：" + (int) Math.floor(currentRadius()) +
                                " / " + radius + " 格"), true);
                    }
                }
                // Nearby vanilla particles are a fallback/accent; the large, continuous
                // shell is a client-rendered mesh synchronized via a small S2C packet.
                if (elapsedTicks % 6 == 0) renderCoreAccent();
                // A short-lived, outward-directed eruption follows the expanding
                // excavation edge; avoid continuously erupting at a stalled rim.
                if (carver != null && config.explosionRimDebrisEnabled
                        && elapsedTicks % 2 == 0
                        && currentRadius() > lastSprayedRadius + 0.15) {
                    sprayTerrainRim(config);
                    lastSprayedRadius = currentRadius();
                }
                if (carver != null) {
                    // Frequent authoritative snapshots are important when digging
                    // briefly stalls the advancing energy sphere.
                    if (elapsedTicks % 4 == 0) broadcastSnapshot();
                } else {
                    if (age % 20 == 0) broadcastSnapshot();
                }
                if (elapsedTicks % 4 == 0 && config.explosionDamageEnabled) hitVolume(caster);
                if (age >= VISUAL_TICKS) {
                    visualFinished = true;
                    if (caster != null) {
                        restoreCaster(caster);
                        if (CasterDeathRule.shouldKill(caster.isCreative(),
                                config.creativeCasterDeathEnabled)) {
                            killCaster(caster);
                        } else {
                            caster.sendMessage(Text.literal(
                                    "創造模式免除自爆死亡（可在設定檔開啟）。"), false);
                        }
                    } else if (CasterDeathRule.shouldKill(creativeAtStart,
                            config.creativeCasterDeathEnabled)) {
                        // In-memory obligation if a non-exempt caster logs out mid-blast.
                        PENDING_DEATHS.add(casterId);
                    }
                    if (config.residualSmokeEnabled && config.residualSmokeSeconds > 0 &&
                            config.smokeParticlesPerTick > 0) {
                        if (SMOKE_CLOUDS.size() >= MAX_SMOKE_CLOUDS) SMOKE_CLOUDS.remove(0);
                        SMOKE_CLOUDS.add(new SmokeCloud(world, center, radius,
                                config.residualSmokeSeconds * 20, config.smokeParticlesPerTick));
                    }
                    broadcastSnapshot(); // Final age tells clients to remove this blast.
                    world.spawnParticles(ParticleTypes.FLASH, center.x, center.y, center.z,
                            1, 0.0, 0.0, 0.0, 0.0);
                    world.playSound(null, caster == null ? originalPlayer.getBlockPos() : caster.getBlockPos(),
                            SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.1F, 0.55F);
                }
            } else if (carver != null && !carver.isDone()) {
                // Finish rounding/boundary cells even after the shell is gone.
                carver.tick(radius, config.maxExplosionBlockRemovalsPerTick,
                        config.explosionEvaporatesWater);
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
                    center.x, center.y, center.z, radius, age, (float) charge,
                    carver != null));
            ServerPlayNetworking.send(viewer, BlastSyncPayload.ID, buf);
        }

        /** Small server particles only; large shell is rendered by each client. */
        private void renderCoreAccent() {
            // Continue rotating even when terrain processing slows the radius.
            double phase = BlastGeometry.counterclockwisePhase(elapsedTicks, charge);
            double coreRadius = Math.min(currentRadius(),
                    1.4 + 1.5 * Math.sin(elapsedTicks * 0.10) * Math.sin(elapsedTicks * 0.10));
            for (int n = 0; n < 16; n++) {
                double a = phase + n * (Math.PI * 2.0 / 16.0);
                world.spawnParticles(n % 3 == 0 ? WHITE : GOLD,
                        center.x + coreRadius * Math.cos(a), center.y + (n % 5) * 0.28,
                        center.z + coreRadius * Math.sin(a), 1, 0.0, 0.0, 0.0, 0.0);
            }
            world.spawnParticles(ParticleTypes.END_ROD, center.x, center.y, center.z,
                    6, 1.5, 1.5, 1.5, 0.01);
        }

        /**
         * Erupting stone/sand and smoke at the MOVING ground-level perimeter.
         * Pure particles: no falling-block entities, no item drops or server-tick physics.
         * Each burst has a strict cap and never loads an unloaded chunk.
         * Particle count=0 is vanilla's directional-velocity packet convention.
         */
        private void sprayTerrainRim(AbilityConfig config) {
            double movingRadius = currentRadius();
            if (movingRadius < 1.2) return;
            int rubble = config.rimDebrisParticlesPerBurst;
            int smoke = config.rimSmokeParticlesPerBurst;
            int total = rubble + smoke;
            // Forced per-viewer delivery makes the wide rim visible even when
            // the observer is far from the blast perimeter (e.g. a 200-block ball).
            Collection<ServerPlayerEntity> viewers = PlayerLookup.around(
                    world, center, radius + 96.0);
            if (viewers.isEmpty()) return;
            for (int n = 0; n < total; n++) {
                double[] jet = BlastRimSpray.jet(rimSample++, movingRadius);
                double x = center.x + jet[0];
                double z = center.z + jet[1];
                int bx = (int) Math.floor(x);
                int bz = (int) Math.floor(z);
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
                int terrainTop = world.getTopY(Heightmap.Type.WORLD_SURFACE, bx, bz);
                if (terrainTop <= world.getBottomY() || terrainTop >= world.getTopY()) continue;
                // The carver may pre-clear blocks ahead of the mesh. Preserve a
                // ground-level rim at the caster's original surface instead of
                // making all debris erupt from the bottom of an already-dug pit.
                int surfaceY = Math.max(terrainTop, (int) Math.floor(anchor.y));
                if (Math.abs(surfaceY - center.y) >
                        Math.max(4.0, BlastGeometry.verticalRadius(movingRadius) + 2.0)) continue;
                double y = surfaceY + 0.08;
                if (n < rubble) {
                    BlockState material = world.getBlockState(new BlockPos(bx, surfaceY - 1, bz));
                    if (material.isAir() || !material.getFluidState().isEmpty()) {
                        // A just-excavated rim may be air; use ground-coloured fragments.
                        material = n % 3 == 0 ? Blocks.GRAVEL.getDefaultState()
                                : Blocks.DIRT.getDefaultState();
                    }
                    BlockStateParticleEffect fragments =
                            new BlockStateParticleEffect(ParticleTypes.BLOCK, material);
                    for (ServerPlayerEntity viewer : viewers) {
                        world.spawnParticles(viewer, fragments, true,
                                x, y + 0.1, z, 0, jet[2], jet[3], jet[4], 1.0);
                    }
                } else {
                    // Smoke trails lag rubble and rise more slowly, curling outward.
                    double vx = jet[2] * 0.4;
                    double vz = jet[4] * 0.4;
                    double vy = 0.055 + jet[3] * 0.23;
                    for (ServerPlayerEntity viewer : viewers) {
                        if (n % 3 == 0) {
                            world.spawnParticles(viewer, ParticleTypes.LARGE_SMOKE, true,
                                    x, y + 0.22, z, 0, vx, vy, vz, 1.0);
                        } else {
                            world.spawnParticles(viewer, EARTH_DUST, true,
                                    x, y + 0.22, z, 0, vx, vy, vz, 1.0);
                        }
                    }
                }
            }
        }

        /**
         * Every entity INSIDE the expanding solid ellipsoid takes 9 HP per
         * second (20 game ticks), including entities already hit by the wave.
         * A failed hit is retried; damage-cooldown and armour stay vanilla.
         */
        private void hitVolume(ServerPlayerEntity caster) {
            double distance = currentRadius();
            double vertical = BlastGeometry.verticalRadius(distance);
            if (distance <= 0.01) return;
            Box bounds = new Box(center.x - distance, center.y - vertical, center.z - distance,
                    center.x + distance, center.y + vertical, center.z + distance);
            for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, bounds,
                    e -> e.isAlive() && !e.getUuid().equals(casterId) && !e.isSpectator())) {
                Box body = entity.getBoundingBox();
                if (!BlastGeometry.overlapsBox(body.minX - center.x, body.minY - center.y,
                        body.minZ - center.z, body.maxX - center.x,
                        body.maxY - center.y, body.maxZ - center.z, distance)) continue;
                Integer lastTick = lastDamageTick.get(entity.getUuid());
                if (!BlastDamageGate.canHit(elapsedTicks, lastTick)) continue;
                boolean accepted = entity.damage(
                        world.getDamageSources().explosion(null,
                                caster == null ? originalPlayer : caster), 9.0F);
                if (!accepted) continue;
                lastDamageTick.put(entity.getUuid(), elapsedTicks);
                Vec3d offset = body.getCenter().subtract(center);
                double length = Math.max(0.01, offset.length());
                // A subtle outward force, not 1.6 blocks of knockback every second.
                entity.addVelocity(offset.x / length * 0.35, 0.1, offset.z / length * 0.35);
            }
        }
    }

    /**
     * Priority-ordered 3D ellipsoid excavation.
     *
     * The old algorithm emptied whole columns at their final depth, making a
     * cylinder with vertical walls while the wave was growing. This algorithm
     * orders each BLOCK by its ellipsoidal arrival distance: first near the
     * centre, then upward/downward and outward as the shell expands. Horizontal
     * seeds are generated lazily; all updates remain on the server tick thread.
     */
    static final class TerrainCarver {
        private final ServerWorld world;
        private final ServerPlayerEntity owner;
        private final Vec3d center;
        private final int radius;
        private final int down;
        private final int up;
        private final PriorityQueue<HorizontalSeed> frontier =
                new PriorityQueue<>(Comparator.comparingInt(c -> c.distanceSquared));
        private final PriorityQueue<VerticalCursor> cells =
                new PriorityQueue<>(Comparator.comparingDouble(c -> c.nextDistanceSquared));
        private VerticalCursor current;

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
                frontier.add(new HorizontalSeed(0, dz, +1, maxX));
                if (maxX >= 1) frontier.add(new HorizontalSeed(-1, dz, -1, maxX));
            }
        }

        boolean isDone() { return current == null && frontier.isEmpty() && cells.isEmpty(); }

        /** Lower bound of all pending voxel arrival radii, not just column radii. */
        double completedRadius() {
            if (isDone()) return radius;
            double next = Double.POSITIVE_INFINITY;
            if (current != null) next = Math.min(next, current.nextDistanceSquared);
            if (!frontier.isEmpty()) next = Math.min(next, frontier.peek().distanceSquared);
            if (!cells.isEmpty()) next = Math.min(next, cells.peek().nextDistanceSquared);
            return Math.max(0.0, Math.min(radius, Math.sqrt(next) - 1.0e-6));
        }

        void tick(double targetRadius, int maxRemovals, boolean evaporateWater) {
            int scans = 0;
            int removals = 0;
            int columns = 0;
            int vaporParticles = 0;
            double limitSquared = Math.min(radius, targetRadius) * Math.min(radius, targetRadius);
            while (scans < MAX_BLOCK_SCANS_PER_JOB_TICK &&
                    removals < maxRemovals && columns < MAX_COLUMNS_PER_JOB_TICK) {
                if (current == null) {
                    double firstSeed = frontier.isEmpty() ? Double.POSITIVE_INFINITY
                            : frontier.peek().distanceSquared;
                    double firstCell = cells.isEmpty() ? Double.POSITIVE_INFINITY
                            : cells.peek().nextDistanceSquared;
                    if (Math.min(firstSeed, firstCell) > limitSquared + 1.0e-8) break;
                    if (firstSeed <= firstCell) {
                        openColumn(frontier.poll());
                        columns++;
                        continue;
                    }
                    current = cells.poll();
                }
                int x = (int) Math.floor(center.x) + current.dx;
                int z = (int) Math.floor(center.z) + current.dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    current = null;
                    continue;
                }
                while (current != null && current.valid()
                        && current.nextDistanceSquared <= limitSquared + 1.0e-8
                        && scans < MAX_BLOCK_SCANS_PER_JOB_TICK && removals < maxRemovals) {
                    BlockPos pos = new BlockPos(x, current.y, z);
                    scans++;
                    BlockState state = world.getBlockState(pos);
                    boolean isWater = state.getFluidState().isIn(FluidTags.WATER);
                    if (!state.isAir() && !state.hasBlockEntity()
                            && state.getHardness(world, pos) >= 0.0F
                            && world.canPlayerModifyAt(owner, pos)
                            && (!isWater || evaporateWater)
                            && world.setBlockState(pos, Blocks.AIR.getDefaultState(),
                                    Block.NOTIFY_LISTENERS | Block.SKIP_DROPS |
                                            (isWater ? Block.FORCE_STATE : 0))) {
                        removals++;
                        // Water sources, flows and waterlogged blocks are removed.
                        // A tiny capped burst suggests instant evaporation.
                        if (isWater && vaporParticles++ < 12) {
                            world.spawnParticles(ParticleTypes.CLOUD,
                                    x + 0.5, current.y + 0.6, z + 0.5,
                                    1, 0.12, 0.12, 0.12, 0.01);
                        }
                    }
                    current.step();
                }
                if (current != null) {
                    if (current.valid()) cells.add(current);
                    current = null;
                }
            }
        }

        private void openColumn(HorizontalSeed seed) {
            int nextX = seed.dx + seed.direction;
            if (Math.abs(nextX) <= seed.maxX) {
                frontier.add(new HorizontalSeed(nextX, seed.dz, seed.direction, seed.maxX));
            }
            int x = (int) Math.floor(center.x) + seed.dx;
            int z = (int) Math.floor(center.z) + seed.dz;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return;

            // Avoid scanning dozens of AIR blocks above natural ground.
            int highestSolid = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
            int low = Math.max(world.getBottomY(),
                    BlastCrater.lowestY(seed.dx, seed.dz, center.y, radius, down));
            int high = Math.min(Math.min(world.getTopY() - 1, highestSolid),
                    BlastCrater.highestY(seed.dx, seed.dz, center.y, radius, up));
            if (low > high) return;
            int middle = (int) Math.floor(center.y - 0.5);
            int below = Math.min(middle, high);
            if (below >= low) cells.add(new VerticalCursor(seed.dx, seed.dz, below, low, -1));
            int above = Math.max(middle + 1, low);
            if (above <= high) cells.add(new VerticalCursor(seed.dx, seed.dz, above, high, +1));
        }

        private final class VerticalCursor {
            final int dx;
            final int dz;
            final int endY;
            final int direction;
            int y;
            double nextDistanceSquared;

            VerticalCursor(int dx, int dz, int y, int endY, int direction) {
                this.dx = dx;
                this.dz = dz;
                this.y = y;
                this.endY = endY;
                this.direction = direction;
                refreshDistance();
            }

            boolean valid() { return direction > 0 ? y <= endY : y >= endY; }
            void step() { y += direction; refreshDistance(); }
            private void refreshDistance() {
                nextDistanceSquared = valid()
                        ? BlastCrater.arrivalSquared(dx, dz, y, center.y, radius, down, up)
                        : Double.POSITIVE_INFINITY;
            }
        }

        private static final class HorizontalSeed {
            final int dx;
            final int dz;
            final int direction;
            final int maxX;
            final int distanceSquared;

            HorizontalSeed(int dx, int dz, int direction, int maxX) {
                this.dx = dx;
                this.dz = dz;
                this.direction = direction;
                this.maxX = maxX;
                this.distanceSquared = dx * dx + dz * dz;
            }
        }
    }

    /** Small, bounded after-effect independent of excavation and player state. */
    private static final class SmokeCloud {
        private final ServerWorld world;
        private final Vec3d center;
        private final double radius;
        private final int lifetimeTicks;
        private final int particlesPerTick;
        private long sample;
        private int age;

        SmokeCloud(ServerWorld world, Vec3d center, int radius,
                   int lifetimeTicks, int particlesPerTick) {
            this.world = world;
            this.center = center;
            this.radius = radius;
            this.lifetimeTicks = lifetimeTicks;
            this.particlesPerTick = Math.min(24, particlesPerTick);
        }

        boolean tick() {
            if (++age > lifetimeTicks) return true;
            // Fade the emissions over time; vanilla smoke particles then disperse.
            double fade = 1.0 - age / (double) lifetimeTicks;
            int quantity = (int) Math.ceil(particlesPerTick * fade);
            for (int i = 0; i < quantity; i++) {
                double[] disk = ResidualSmokePattern.point(sample++, radius);
                double x = center.x + disk[0];
                double z = center.z + disk[1];
                int bx = (int) Math.floor(x);
                int bz = (int) Math.floor(z);
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
                double y = world.getTopY(Heightmap.Type.WORLD_SURFACE, bx, bz)
                        + 0.25 + (sample % 5) * 0.24;
                world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        x, y, z, 1, 0.16, 0.10, 0.16, 0.008);
            }
            return false;
        }
    }
}
