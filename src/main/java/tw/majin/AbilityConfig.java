package tw.majin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Simple server-side configuration; reload by restarting the server/world. */
public final class AbilityConfig {
    public final boolean terrainAbsorptionEnabled;
    public final boolean mobDamageEnabled;
    public final int blocksPerSecond;
    public final int maxActiveDebrisPerPlayer;
    public final boolean explosionTerrainEnabled;
    public final boolean explosionDamageEnabled;
    public final int maxExplosionBlockRemovalsPerTick;

    private AbilityConfig(boolean terrainAbsorptionEnabled, boolean mobDamageEnabled,
                          int blocksPerSecond, int maxActiveDebrisPerPlayer,
                          boolean explosionTerrainEnabled, boolean explosionDamageEnabled,
                          int maxExplosionBlockRemovalsPerTick) {
        this.terrainAbsorptionEnabled = terrainAbsorptionEnabled;
        this.mobDamageEnabled = mobDamageEnabled;
        this.blocksPerSecond = blocksPerSecond;
        this.maxActiveDebrisPerPlayer = maxActiveDebrisPerPlayer;
        this.explosionTerrainEnabled = explosionTerrainEnabled;
        this.explosionDamageEnabled = explosionDamageEnabled;
        this.maxExplosionBlockRemovalsPerTick = maxExplosionBlockRemovalsPerTick;
    }

    public static AbilityConfig load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("majin_explosion.properties");
        Properties properties = new Properties();
        properties.setProperty("terrain_absorption_enabled", "false");
        properties.setProperty("mob_damage_enabled", "true");
        properties.setProperty("blocks_per_second", "8");
        properties.setProperty("max_active_debris_per_player", "24");
        properties.setProperty("explosion_terrain_enabled", "false");
        properties.setProperty("explosion_damage_enabled", "true");
        properties.setProperty("max_explosion_block_removals_per_tick", "512");
        try {
            Files.createDirectories(file.getParent());
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    properties.load(in);
                }
            } else {
                try (OutputStream out = Files.newOutputStream(file)) {
                    properties.store(out, "Majin Explosion stage 7 - restart to reload");
                }
            }
        } catch (IOException ex) {
            MajinExplosionMod.LOGGER.warn("Could not load/write {}. Using available defaults.", file, ex);
        }
        return new AbilityConfig(
                Boolean.parseBoolean(properties.getProperty("terrain_absorption_enabled", "false")),
                Boolean.parseBoolean(properties.getProperty("mob_damage_enabled", "true")),
                boundedInt(properties, "blocks_per_second", 8, 0, 20),
                boundedInt(properties, "max_active_debris_per_player", 24, 1, 48),
                Boolean.parseBoolean(properties.getProperty("explosion_terrain_enabled", "false")),
                Boolean.parseBoolean(properties.getProperty("explosion_damage_enabled", "true")),
                boundedInt(properties, "max_explosion_block_removals_per_tick", 512, 32, 2048));
    }

    private static int boundedInt(Properties properties, String key, int fallback, int low, int high) {
        try {
            return Math.max(low, Math.min(high, Integer.parseInt(properties.getProperty(key).trim())));
        } catch (NumberFormatException | NullPointerException ex) {
            MajinExplosionMod.LOGGER.warn("Invalid config '{}' - using {}", key, fallback);
            return fallback;
        }
    }
}
