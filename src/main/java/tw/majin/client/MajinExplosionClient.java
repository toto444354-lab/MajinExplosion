package tw.majin.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import org.lwjgl.glfw.GLFW;
import tw.majin.network.AbilityPayload;
import tw.majin.network.BlastSyncPayload;
import tw.majin.network.ChargeSyncPayload;

public final class MajinExplosionClient implements ClientModInitializer {
    private static KeyBinding chargeKey;
    private static KeyBinding explodeKey;
    private static boolean wasCharging;

    @Override
    public void onInitializeClient() {
        chargeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.majin_explosion.charge",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.majin_explosion"
        ));
        explodeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.majin_explosion.explode",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.majin_explosion"
        ));

        BlastSphereRenderer.register();
        ClientPlayNetworking.registerGlobalReceiver(BlastSyncPayload.ID,
                (client, handler, buf, responseSender) -> {
                    BlastSyncPayload.Snapshot snapshot = BlastSyncPayload.read(buf);
                    client.execute(() -> BlastSphereRenderer.accept(snapshot));
                });

        ClientPlayNetworking.registerGlobalReceiver(ChargeSyncPayload.ID,
                (client, handler, buf, responseSender) -> {
                    ChargeSyncPayload.Snapshot snapshot = ChargeSyncPayload.read(buf);
                    client.execute(() -> BlastSphereRenderer.acceptCharge(snapshot));
                });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BlastSphereRenderer.tick(client);
            // Avoid retaining a key-down state after disconnect/server switch.
            if (client.player == null || client.getNetworkHandler() == null ||
                    !ClientPlayNetworking.canSend(AbilityPayload.ID)) {
                wasCharging = false;
                while (explodeKey.wasPressed()) { /* drain stale presses */ }
                return;
            }
            boolean charging = client.currentScreen == null && chargeKey.isPressed();
            if (charging != wasCharging) {
                send(charging ? AbilityPayload.Action.START_CHARGE : AbilityPayload.Action.STOP_CHARGE);
                wasCharging = charging;
            }
            while (explodeKey.wasPressed()) {
                // H must be sent while the G-held session exists on the server.
                send(AbilityPayload.Action.RELEASE_EXPLOSION);
            }
            if (charging) {
                // Client-side input suppression for responsive locking; the server
                // also pins the player so spoofed movement packets cannot bypass it.
                client.player.input.movementForward = 0.0F;
                client.player.input.movementSideways = 0.0F;
                client.player.input.pressingForward = false;
                client.player.input.pressingBack = false;
                client.player.input.pressingLeft = false;
                client.player.input.pressingRight = false;
                client.player.input.jumping = false;
                client.player.input.sneaking = false;
                client.player.setVelocity(Vec3d.ZERO);
            }
        });
    }

    private static void send(AbilityPayload.Action action) {
        if (ClientPlayNetworking.canSend(AbilityPayload.ID)) {
            PacketByteBuf buf = PacketByteBufs.create();
            AbilityPayload.write(buf, action);
            ClientPlayNetworking.send(AbilityPayload.ID, buf);
        }
    }
}
