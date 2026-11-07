package tw.majin;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.majin.network.AbilityPayload;

public final class MajinExplosionMod implements ModInitializer {
    public static final String MOD_ID = "majin_explosion";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        AbilityController.registerTickEvents();
        DummyController.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            AbilityController.clear(handler.player);
            DummyController.clear(handler.player);
            ExplosionEffects.onDisconnect(handler.player);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    ExplosionEffects.onJoin(handler.player);
                    ExplosionEffects.syncToPlayer(handler.player);
                    AbilityController.syncToPlayer(handler.player);
                    DummyController.syncToPlayer(handler.player);
                }));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AbilityController.shutdown(server);
            DummyController.shutdown();
            ExplosionEffects.shutdown(server);
        });
        ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.ID,
                (server, player, handler, buf, responseSender) -> {
                    AbilityPayload.Action action = AbilityPayload.read(buf);
                    server.execute(() -> {
                        switch (action) {
                            case START_CHARGE -> AbilityController.startCharge(player);
                            case STOP_CHARGE -> AbilityController.stopCharge(player);
                            case RELEASE_EXPLOSION -> AbilityController.releaseExplosion(player);
                            case INVALID -> LOGGER.warn("Ignored invalid ability packet from {}", player.getName().getString());
                        }
                    });
                });
        LOGGER.info("Majin Explosion initialized.");
    }
}
