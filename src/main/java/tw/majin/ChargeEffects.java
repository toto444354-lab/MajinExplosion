package tw.majin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import tw.majin.visual.ChargeField;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Server-authoritative mob suction, damage and bounded flying block debris. */
public final class ChargeEffects {
    private static final int MAX_DEBRIS_LIFETIME_TICKS = 100;
    private static final int MAX_BLOCK_SAMPLE_ATTEMPTS = 24;

    private ChargeEffects() {}

    /** No movement/force is applied: touching the charging sphere is the only damage source. */
    public static void damageOnContact(ServerWorld world, Vec3d anchor, UUID excluded,
                                       EffectsState effects, double progress, int ageTicks,
                                       boolean damageEnabled) {
        if (!damageEnabled) return;
        double radius = ChargeField.sphereRadius(progress);
        Vec3d center = anchor.add(0.0, 1.0, 0.0);
        Box bounds = new Box(center.x - radius - 3, center.y - radius - 3,
                center.z - radius - 3, center.x + radius + 3,
                center.y + radius + 3, center.z + radius + 3);
        for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, bounds,
                e -> e.isAlive() && !e.isSpectator() && !e.getUuid().equals(excluded))) {
            Vec3d offset = mob.getBoundingBox().getCenter().subtract(center);
            if (!ChargeField.touchesShell(offset.x, offset.y, offset.z,
                    progress, mob.getWidth())) continue;
            Integer previous = effects.lastSphereDamageTick.get(mob.getUuid());
            if (previous == null || ageTicks - previous >= 20) {
                if (mob.damage(world.getDamageSources().magic(), 1.0F)) {
                    effects.lastSphereDamageTick.put(mob.getUuid(), ageTicks);
                }
            }
        }
        if (ageTicks % 80 == 0) {
            effects.lastSphereDamageTick.entrySet().removeIf(
                    e -> ageTicks - e.getValue() > 80);
        }
    }

    /**
     * Stage 13: natural campfire smoke around the ground-level charge field.
     * Emits a small, configurable burst (not every tick); vanilla campfire
     * smoke supplies its own long-lived curl/vertical motion. No suction.
     */
    public static void spawnChargingDust(ServerWorld world, Vec3d anchor,
                                         int ageTicks, double progress, int amount) {
        int count = Math.max(0, Math.min(10, amount));
        for (int i = 0; i < count; i++) {
            double[] sample = tw.majin.visual.ChargeDustPattern.sample(ageTicks, i, progress);
            // count=0 in the particle packet means one puff with directional
            // velocity. The upward drift is intentionally very gentle.
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    anchor.x + sample[0], anchor.y + sample[1], anchor.z + sample[2],
                    0, sample[3], sample[4], sample[5], 0.65);
        }
    }

    /**
     * Wind pulls creatures toward the OUTER surface of a physical spherical field.
     * Travel is harmless. The sphere is server-collidable even though its visual
     * surface is a client-rendered mesh rather than a vanilla Minecraft entity.
     */
    public static void pullMobs(ServerPlayerEntity player, EffectsState effects,
                                double progress, int ageTicks, boolean damageEnabled) {
        ServerWorld world = player.getServerWorld();
        double range = ChargeField.suctionRadius(progress);
        Vec3d center = player.getPos().add(0.0, 1.0, 0.0);
        Box area = player.getBoundingBox().expand(range);
        for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, area,
                entity -> entity.isAlive() && !(entity instanceof PlayerEntity))) {
            Vec3d mobCenter = mob.getPos().add(0.0, mob.getHeight() * 0.5, 0.0);
            Vec3d offset = mobCenter.subtract(center);
            double distanceSquared = offset.lengthSquared();
            if (distanceSquared > range * range) continue;
            double collisionRadius = ChargeField.mobContactRadius(progress, mob.getWidth());
            double distance = Math.sqrt(distanceSquared);
            if (ChargeField.touchesShell(offset.x, offset.y, offset.z,
                    progress, mob.getWidth())) {
                // Stop at the sphere, not at the player. A very small displacement
                // maintains the same height so ground mobs don't float upward.
                double flat = Math.hypot(offset.x, offset.z);
                double clearance = ChargeField.minimumHorizontalDistance(
                        collisionRadius, offset.y);
                if (flat < clearance && clearance > 0.001) {
                    double nx = flat > 0.001 ? offset.x / flat : 1.0;
                    double nz = flat > 0.001 ? offset.z / flat : 0.0;
                    mob.setPosition(center.x + nx * clearance, mob.getY(),
                            center.z + nz * clearance);
                }
                mob.setVelocity(Vec3d.ZERO);
                mob.velocityModified = true;
                mob.fallDistance = 0.0F;
                // A separate per-target cooldown: first contact then at most 1 HP
                // every 20 ticks while touching; never during the approach.
                Integer previous = effects.lastSphereDamageTick.get(mob.getUuid());
                if (damageEnabled && (previous == null || ageTicks - previous >= 20)) {
                    mob.damage(world.getDamageSources().magic(), 1.0F);
                    effects.lastSphereDamageTick.put(mob.getUuid(), ageTicks);
                }
            } else if (distance > 0.001) {
                effects.lastSphereDamageTick.remove(mob.getUuid());
                // Gradually steer inward, no overshoot into the player collider.
                double pull = 0.20 + 0.28 * progress;
                Vec3d inward = offset.multiply(-pull / distance);
                Vec3d velocity = mob.getVelocity().multiply(0.55).add(inward);
                double speed = velocity.length();
                if (speed > 0.9) velocity = velocity.multiply(0.9 / speed);
                // Cap the distance travelled so next tick cannot leap past the shell.
                double allowance = Math.max(0.0, distance - collisionRadius - 0.04);
                if (velocity.length() > allowance) {
                    velocity = allowance <= 0.001 ? Vec3d.ZERO
                            : velocity.normalize().multiply(allowance);
                }
                mob.setVelocity(velocity);
                mob.velocityModified = true;
                mob.fallDistance = 0.0F;
            }
        }
        if (ageTicks % 40 == 0) {
            effects.lastSphereDamageTick.entrySet().removeIf(
                    entry -> ageTicks - entry.getValue() > 80);
        }
    }

    /** Samples a few nearby *loaded* blocks. Returns the number converted into debris. */
    public static int liftBlocks(ServerPlayerEntity player, EffectsState effects, double progress,
                                 int spawnAllowance) {
        if (spawnAllowance <= 0) return 0;
        ServerWorld world = player.getServerWorld();
        int spawned = 0;
        for (int attempt = 0; attempt < MAX_BLOCK_SAMPLE_ATTEMPTS && spawned < spawnAllowance; attempt++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0;
            double radius = 3.7 + world.random.nextDouble() * (3.0 + 5.0 * progress);
            int x = MathHelper.floor(player.getX() + Math.cos(angle) * radius);
            int z = MathHelper.floor(player.getZ() + Math.sin(angle) * radius);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            // Probe surfaces around the caster, instead of iterating over entire chunks.
            int topY = MathHelper.floor(player.getY()) + 3;
            for (int y = topY; y >= MathHelper.floor(player.getY()) - 4; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!world.isInBuildLimit(pos)) continue;
                BlockState state = world.getBlockState(pos);
                if (!canLift(player, world, pos, state)) continue;
                // Vanilla method removes the actual source block and spawns a moving entity.
                FallingBlockEntity entity = FallingBlockEntity.spawnFromBlock(world, pos, state);
                entity.setNoGravity(true);
                // Public Yarn 1.20.1 API. The dropItem field is package-private
                // and cannot be set from an external mod; destroy on landing
                // and discard flying fragments before they can settle instead.
                entity.setDestroyedOnLanding();
                effects.debris.put(entity.getUuid(), new Debris(entity, state));
                spawned++;
                break;
            }
        }
        return spawned;
    }

    private static boolean canLift(ServerPlayerEntity player, ServerWorld world,
                                   BlockPos pos, BlockState state) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.hasBlockEntity()
                && state.isSolidBlock(world, pos)
                && state.getHardness(world, pos) >= 0.0F
                && world.canPlayerModifyAt(player, pos);
    }

    /** Move all owned debris toward the current caster position, then destroy it near the core. */
    public static void tickDebris(ServerPlayerEntity player, EffectsState effects, double progress) {
        ServerWorld world = player.getServerWorld();
        Vec3d target = player.getPos().add(0.0, 1.05, 0.0);
        Iterator<Map.Entry<UUID, Debris>> iterator = effects.debris.entrySet().iterator();
        while (iterator.hasNext()) {
            Debris debris = iterator.next().getValue();
            FallingBlockEntity entity = debris.entity;
            debris.age++;
            if (entity.isRemoved() || entity.getWorld() != world) {
                iterator.remove();
                continue;
            }
            Vec3d toCenter = target.subtract(entity.getPos());
            double distance = toCenter.length();
            if (distance <= ChargeField.sphereRadius(progress) + 0.25) {
                shatter(world, entity, debris.state);
                iterator.remove();
            } else if (debris.age >= MAX_DEBRIS_LIFETIME_TICKS || distance > 40.0) {
                entity.discard(); // Prevent orphaned blocks/items when they miss the target.
                iterator.remove();
            } else {
                Vec3d inward = toCenter.multiply((0.26 + 0.22 * progress) / distance);
                // Top-down clockwise tangential motion before the fragment shatters.
                double planar = Math.hypot(toCenter.x, toCenter.z);
                Vec3d swirl = planar > 0.001
                        ? new Vec3d(toCenter.z / planar * 0.08, 0.0, -toCenter.x / planar * 0.08)
                        : Vec3d.ZERO;
                entity.setVelocity(inward.add(swirl));
                entity.velocityModified = true;
            }
        }
    }

    private static void shatter(ServerWorld world, FallingBlockEntity entity, BlockState state) {
        Vec3d point = entity.getPos();
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                point.x, point.y, point.z, 20, 0.38, 0.38, 0.38, 0.14);
        world.spawnParticles(ParticleTypes.END_ROD, point.x, point.y, point.z,
                3, 0.2, 0.2, 0.2, 0.01);
        entity.discard();
    }

    public static void discardAll(EffectsState effects) {
        effects.debris.values().forEach(debris -> {
            if (!debris.entity.isRemoved()) debris.entity.discard();
        });
        effects.debris.clear();
        effects.lastSphereDamageTick.clear();
    }

    public static final class EffectsState {
        private final Map<UUID, Debris> debris = new HashMap<>();
        private final Map<UUID, Integer> lastSphereDamageTick = new HashMap<>();
        public int activeDebrisCount() { return debris.size(); }
    }

    private static final class Debris {
        private final FallingBlockEntity entity;
        private final BlockState state;
        private int age;
        private Debris(FallingBlockEntity entity, BlockState state) {
            this.entity = entity;
            this.state = state;
        }
    }
}
