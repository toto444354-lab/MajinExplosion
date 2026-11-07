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
    public final boolean explosionEvaporatesWater;
    public final boolean residualSmokeEnabled;
    public final int residualSmokeSeconds;
    public final int smokeParticlesPerTick;
    public final boolean creativeCasterDeathEnabled;
    public final boolean explosionRimDebrisEnabled;
    public final int rimDebrisParticlesPerBurst;
    public final int rimSmokeParticlesPerBurst;

    private AbilityConfig(boolean terrainAbsorptionEnabled, boolean mobDamageEnabled,
                          int blocksPerSecond, int maxActiveDebrisPerPlayer,
                          boolean explosionTerrainEnabled, boolean explosionDamageEnabled,
                          int maxExplosionBlockRemovalsPerTick,
                          boolean explosionEvaporatesWater, boolean residualSmokeEnabled,
                          int residualSmokeSeconds, int smokeParticlesPerTick,
                          boolean creativeCasterDeathEnabled,
                          boolean explosionRimDebrisEnabled, int rimDebrisParticlesPerBurst,
                          int rimSmokeParticlesPerBurst) {
        this.terrainAbsorptionEnabled = terrainAbsorptionEnabled;
        this.mobDamageEnabled = mobDamageEnabled;
        this.blocksPerSecond = blocksPerSecond;
        this.maxActiveDebrisPerPlayer = maxActiveDebrisPerPlayer;
        this.explosionTerrainEnabled = explosionTerrainEnabled;
        this.explosionDamageEnabled = explosionDamageEnabled;
        this.maxExplosionBlockRemovalsPerTick = maxExplosionBlockRemovalsPerTick;
        this.explosionEvaporatesWater = explosionEvaporatesWater;
        this.residualSmokeEnabled = residualSmokeEnabled;
        this.residualSmokeSeconds = residualSmokeSeconds;
        this.smokeParticlesPerTick = smokeParticlesPerTick;
        this.creativeCasterDeathEnabled = creativeCasterDeathEnabled;
        this.explosionRimDebrisEnabled = explosionRimDebrisEnabled;
        this.rimDebrisParticlesPerBurst = rimDebrisParticlesPerBurst;
        this.rimSmokeParticlesPerBurst = rimSmokeParticlesPerBurst;
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
        properties.setProperty("max_explosion_block_removals_per_tick", "4096");
        properties.setProperty("explosion_evaporates_water", "true");
        properties.setProperty("residual_smoke_enabled", "true");
        properties.setProperty("residual_smoke_seconds", "20");
        properties.setProperty("smoke_particles_per_tick", "12");
        properties.setProperty("creative_caster_death_enabled", "false");
        properties.setProperty("explosion_rim_debris_enabled", "true");
        properties.setProperty("rim_debris_particles_per_burst", "18");
        properties.setProperty("rim_smoke_particles_per_burst", "14");
        try {
            Files.createDirectories(file.getParent());
            boolean shouldWrite = !Files.isRegularFile(file);
            if (!shouldWrite) {
                Properties existing = new Properties();
                try (InputStream in = Files.newInputStream(file)) {
                    existing.load(in);
                }
                for (String key : existing.stringPropertyNames()) {
                    properties.setProperty(key, existing.getProperty(key));
                }
                // Add new stage-10 options to existing configs without resetting choices.
                String[] newKeys = {"explosion_evaporates_water", "residual_smoke_enabled",
                        "residual_smoke_seconds", "smoke_particles_per_tick",
                        "creative_caster_death_enabled", "explosion_rim_debris_enabled",
                        "rim_debris_particles_per_burst", "rim_smoke_particles_per_burst"};
                for (String key : newKeys) if (!existing.containsKey(key)) shouldWrite = true;
            }
            if (shouldWrite) {
                try (OutputStream out = Files.newOutputStream(file)) {
                    properties.store(out, "Majin Explosion 0.10.0 - restart to reload");
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
                boundedInt(properties, "max_explosion_block_removals_per_tick", 4096, 64, 8192),
                Boolean.parseBoolean(properties.getProperty("explosion_evaporates_water", "true")),
                Boolean.parseBoolean(properties.getProperty("residual_smoke_enabled", "true")),
                boundedInt(properties, "residual_smoke_seconds", 20, 0, 60),
                boundedInt(properties, "smoke_particles_per_tick", 12, 0, 24),
                Boolean.parseBoolean(properties.getProperty("creative_caster_death_enabled", "false")),
                Boolean.parseBoolean(properties.getProperty("explosion_rim_debris_enabled", "true")),
                boundedInt(properties, "rim_debris_particles_per_burst", 18, 0, 48),
                boundedInt(properties, "rim_smoke_particles_per_burst", 14, 0, 48));
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
