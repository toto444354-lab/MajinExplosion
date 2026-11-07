package tw.majin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import tw.majin.network.ChargeSyncPayload;
import tw.majin.visual.ChargeField;
import tw.majin.visual.ChargeRules;

/** One independently controlled, server-authoritative demonstration dummy per operator. */
public final class DummyController {
    private static final Map<UUID, DummyState> DUMMIES = new HashMap<>();
    private static final int VIEW_RADIUS = 112;

    private DummyController() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerCommands(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Map.Entry<UUID, DummyState>> iter = DUMMIES.entrySet().iterator();
            while (iter.hasNext()) {
                DummyState state = iter.next().getValue();
                if (state.dummy.isRemoved() || state.dummy.getWorld() != state.world) {
                    broadcastCharge(state, false);
                    iter.remove();
                    continue;
                }
                if (!state.charging) continue;
                state.dummy.setVelocity(Vec3d.ZERO);
                state.dummy.velocityModified = true;
                if (state.dummy.getPos().squaredDistanceTo(state.anchor) > 0.0004) {
                    state.dummy.setPosition(state.anchor.x, state.anchor.y, state.anchor.z);
                }
                state.ticks = ChargeRules.advance(state.ticks);
                state.visualTicks++;
                double progress = ChargeField.clamp(state.ticks / (double) ChargeRules.MAX_TICKS);
                AbilityConfig config = AbilityController.getConfig();
                if (config != null && config.chargingSmokeParticlesPerBurst > 0 &&
                        state.visualTicks % config.chargingSmokeIntervalTicks == 0) {
                    ChargeEffects.spawnChargingDust(state.world, state.anchor,
                            state.visualTicks, progress, config.chargingSmokeParticlesPerBurst);
                }
                if (state.visualTicks % 5 == 0) broadcastCharge(state, true);
                // At 200 ticks it remains charged and visually active until release/cancel.
            }
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("majin")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("dummy")
                        .then(CommandManager.literal("spawn").executes(ctx -> spawn(ctx.getSource())))
                        .then(CommandManager.literal("charge").executes(ctx -> charge(ctx.getSource())))
                        .then(CommandManager.literal("cancel").executes(ctx -> cancel(ctx.getSource())))
                        .then(CommandManager.literal("release").executes(ctx -> release(ctx.getSource())))
                        .then(CommandManager.literal("remove").executes(ctx -> remove(ctx.getSource())))
                        .then(CommandManager.literal("status").executes(ctx -> status(ctx.getSource())))));
    }

    private static int spawn(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        DummyState previous = DUMMIES.get(player.getUuid());
        if (previous != null && !previous.dummy.isRemoved()) {
            source.sendError(Text.literal("你已經有一個假人，先用 /majin dummy remove 移除。"));
            return 0;
        }
        // Four blocks in front of the operator at foot level; use a clear test area.
        double yaw = Math.toRadians(player.getYaw());
        double x = player.getX() - Math.sin(yaw) * 4.0;
        double z = player.getZ() + Math.cos(yaw) * 4.0;
        ServerWorld world = player.getServerWorld();
        ArmorStandEntity dummy = new ArmorStandEntity(world, x, player.getY(), z);
        dummy.setCustomName(Text.literal("魔人自爆測試假人"));
        dummy.setCustomNameVisible(true);
        dummy.setShowArms(true);
        dummy.setHideBasePlate(true);
        dummy.setSilent(true);
        dummy.setNoGravity(true);
        dummy.setInvulnerable(true);
        dummy.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.PLAYER_HEAD));
        dummy.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
        dummy.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
        if (!world.spawnEntity(dummy)) {
            source.sendError(Text.literal("假人生成失敗，請換個位置重試。"));
            return 0;
        }
        DUMMIES.put(player.getUuid(), new DummyState(world, dummy));
        source.sendFeedback(() -> Text.literal(
                "假人已生成！/majin dummy charge 開始蓄力；你可以自由走動觀察。"), false);
        return 1;
    }

    private static DummyState lookup(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        DummyState state = DUMMIES.get(player.getUuid());
        if (state == null || state.dummy.isRemoved()) {
            source.sendError(Text.literal("沒有可控制的假人，先輸入 /majin dummy spawn。"));
            return null;
        }
        if (state.world != player.getServerWorld()) {
            source.sendError(Text.literal("請回到假人所在維度再操作。"));
            return null;
        }
        return state;
    }

    private static int charge(ServerCommandSource source) throws CommandSyntaxException {
        DummyState state = lookup(source);
        if (state == null) return 0;
        if (state.charging) {
            source.sendFeedback(() -> Text.literal("假人正在蓄力中。"), false);
            return 1;
        }
        state.ticks = 0;
        state.visualTicks = 0;
        state.charging = true;
        broadcastCharge(state, true);
        source.sendFeedback(() -> Text.literal(
                "假人開始蓄力。10 秒後維持滿蓄力；可遠離觀察，再輸入 /majin dummy release。"), false);
        return 1;
    }

    private static int cancel(ServerCommandSource source) throws CommandSyntaxException {
        DummyState state = lookup(source);
        if (state == null) return 0;
        if (state.charging) broadcastCharge(state, false);
        state.charging = false;
        state.ticks = 0;
        state.visualTicks = 0;
        source.sendFeedback(() -> Text.literal("假人蓄力已取消，進度歸零。"), false);
        return 1;
    }

    private static int release(ServerCommandSource source) throws CommandSyntaxException {
        DummyState state = lookup(source);
        if (state == null) return 0;
        if (!state.charging || state.ticks <= 0) {
            source.sendError(Text.literal("假人尚未蓄力，先使用 /majin dummy charge。"));
            return 0;
        }
        ServerPlayerEntity owner = source.getPlayerOrThrow();
        if (!ExplosionEffects.startDummy(owner, state.dummy, state.ticks,
                AbilityController.getConfig())) {
            source.sendError(Text.literal("爆炸工作已滿或假人無效；請稍後重試。"));
            return 0;
        }
        broadcastCharge(state, false);
        DUMMIES.remove(owner.getUuid()); // Explosion now owns the dummy until it vanishes.
        source.sendFeedback(() -> Text.literal(
                "假人已釋放自爆！你不會因施放者死亡規則而死亡，可從旁邊觀察。"), false);
        return 1;
    }

    private static int remove(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        DummyState state = DUMMIES.remove(player.getUuid());
        if (state == null) {
            source.sendError(Text.literal("沒有待命的假人。"));
            return 0;
        }
        broadcastCharge(state, false);
        if (!state.dummy.isRemoved()) state.dummy.discard();
        source.sendFeedback(() -> Text.literal("測試假人已移除。"), false);
        return 1;
    }

    private static int status(ServerCommandSource source) throws CommandSyntaxException {
        DummyState state = lookup(source);
        if (state == null) return 0;
        String message = state.charging
                ? String.format("假人正在蓄力：%.1f / 10.0 秒。", state.ticks / 20.0)
                : "假人待命中。";
        source.sendFeedback(() -> Text.literal(message), false);
        return 1;
    }

    public static void syncToPlayer(ServerPlayerEntity viewer) {
        for (DummyState state : DUMMIES.values()) {
            if (state.charging && state.world == viewer.getServerWorld() &&
                    state.anchor.squaredDistanceTo(viewer.getPos()) <= VIEW_RADIUS * VIEW_RADIUS) {
                sendCharge(viewer, state, true);
            }
        }
    }

    private static void broadcastCharge(DummyState state, boolean active) {
        for (ServerPlayerEntity viewer : PlayerLookup.around(state.world, state.anchor, VIEW_RADIUS)) {
            sendCharge(viewer, state, active);
        }
    }

    private static void sendCharge(ServerPlayerEntity viewer, DummyState state, boolean active) {
        if (!ServerPlayNetworking.canSend(viewer, ChargeSyncPayload.ID)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        ChargeSyncPayload.write(buf, new ChargeSyncPayload.Snapshot(
                state.dummy.getUuid(), state.world.getRegistryKey().getValue(),
                state.anchor.x, state.anchor.y + 1.0, state.anchor.z,
                state.ticks, state.visualTicks, active));
        ServerPlayNetworking.send(viewer, ChargeSyncPayload.ID, buf);
    }

    public static void clear(ServerPlayerEntity owner) {
        DummyState state = DUMMIES.remove(owner.getUuid());
        if (state != null) {
            broadcastCharge(state, false);
            if (!state.dummy.isRemoved()) state.dummy.discard();
        }
    }

    public static void shutdown() {
        for (DummyState state : DUMMIES.values()) {
            if (!state.dummy.isRemoved()) state.dummy.discard();
        }
        DUMMIES.clear();
    }

    private static final class DummyState {
        final ServerWorld world;
        final ArmorStandEntity dummy;
        final Vec3d anchor;
        int ticks;
        int visualTicks;
        boolean charging;
        DummyState(ServerWorld world, ArmorStandEntity dummy) {
            this.world = world;
            this.dummy = dummy;
            this.anchor = dummy.getPos();
        }
    }
}
