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

    /** Every server tick while G is actively held; ignores other players by design. */
    public static void pullMobs(ServerPlayerEntity player, double progress, boolean doDamage) {
        ServerWorld world = player.getServerWorld();
        double radius = 6.0 + 18.0 * progress; // 6 to 24 blocks
        Vec3d target = player.getPos().add(0.0, 0.95, 0.0);
        Box area = player.getBoundingBox().expand(radius);
        for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, area,
                entity -> entity.isAlive() && !(entity instanceof PlayerEntity))) {
            Vec3d delta = target.subtract(mob.getPos().add(0.0, mob.getHeight() * 0.5, 0.0));
            double distanceSq = delta.lengthSquared();
            if (distanceSq > radius * radius || distanceSq < 0.0001) continue;
            double distance = Math.sqrt(distanceSq);
            // Stronger suction with charge, but velocity is capped to avoid launching mobs.
            if (distance > 1.8) {
                double pull = 0.055 + 0.115 * progress;
                Vec3d next = mob.getVelocity().multiply(0.78).add(delta.multiply(pull / distance));
                double speed = next.length();
                if (speed > 0.75) next = next.multiply(0.75 / speed);
                mob.setVelocity(next);
                mob.velocityModified = true;
                mob.fallDistance = 0.0F;
            }
            // Fixed 9 HP attempt every 20 server ticks, rather than every frame.
            if (doDamage) mob.damage(player.getDamageSources().magic(), 9.0F);
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
            if (distance <= 1.45) {
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
        world.spawnParticles(ParticleTypes.FLAME, point.x, point.y, point.z,
                5, 0.27, 0.27, 0.27, 0.01);
        entity.discard();
    }

    public static void discardAll(EffectsState effects) {
        effects.debris.values().forEach(debris -> {
            if (!debris.entity.isRemoved()) debris.entity.discard();
        });
        effects.debris.clear();
    }

    public static final class EffectsState {
        private final Map<UUID, Debris> debris = new HashMap<>();
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
