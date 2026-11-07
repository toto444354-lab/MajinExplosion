package tw.majin.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import tw.majin.network.BlastSyncPayload;
import tw.majin.visual.BlastGeometry;
import tw.majin.visual.BlastTimeline;
import tw.majin.visual.BlastVisualProfile;

/**
 * Client-only emissive-looking ellipsoid. This is additive geometry, NOT a dynamic
 * light engine, volumetric shader, or post-processing bloom.
 * Gameplay and terrain damage remain server-authoritative.
 */
public final class BlastSphereRenderer {
    private static final Map<UUID, ActiveBlast> BLASTS = new HashMap<>();
    private static final int LATITUDES = 12;
    private static final int LONGITUDES = 32;
    private static final int SHOCK_SEGMENTS = 96;
    private static ClientWorld rememberedWorld;

    private BlastSphereRenderer() {}

    public static void register() {
        WorldRenderEvents.LAST.register(BlastSphereRenderer::render);
    }

    /** Invoked on the client main thread, never directly on a network thread. */
    public static void accept(BlastSyncPayload.Snapshot snapshot) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || !world.getRegistryKey().getValue().equals(snapshot.dimension())) return;
        if (world != rememberedWorld) {
            rememberedWorld = world;
            BLASTS.clear();
        }
        if (snapshot.age() >= BlastGeometry.DURATION_TICKS) {
            BLASTS.remove(snapshot.id());
        } else {
            ActiveBlast existing = BLASTS.get(snapshot.id());
            if (existing == null) {
                BLASTS.put(snapshot.id(), new ActiveBlast(snapshot));
            } else {
                existing.update(snapshot);
            }
        }
    }

    public static void tick(MinecraftClient client) {
        if (client.world != rememberedWorld) {
            rememberedWorld = client.world;
            BLASTS.clear(); // Prevent stale lightballs when changing dimension/world.
        }
        if (client.world == null) return;
        Iterator<ActiveBlast> iterator = BLASTS.values().iterator();
        while (iterator.hasNext()) {
            ActiveBlast blast = iterator.next();
            blast.age = BlastTimeline.advanceAge(blast.age);
            if (blast.age >= BlastGeometry.DURATION_TICKS + 2) iterator.remove();
        }
    }

    private static void render(WorldRenderContext context) {
        if (BLASTS.isEmpty() || context.matrixStack() == null) return;
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        try {
            for (ActiveBlast blast : BLASTS.values()) {
                double age = Math.min(BlastGeometry.DURATION_TICKS,
                        blast.age + (double) context.tickDelta());
                double radius = BlastGeometry.waveRadius(blast.snapshot.radius(), age);
                if (radius < 0.15 && age > BlastVisualProfile.FLASH_TICKS) continue;
                Vec3d center = new Vec3d(blast.snapshot.x(), blast.snapshot.y(), blast.snapshot.z());
                if (center.squaredDistanceTo(camera) > 420.0 * 420.0) continue;
                double phase = BlastGeometry.counterclockwisePhase(age, blast.snapshot.charge());
                matrices.push();
                matrices.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
                Matrix4f transform = matrices.peek().getPositionMatrix();
                // Layered color gradients have rotating five-armed spirals. Since the
                // phase decreases, those bright streaks move CCW when seen from above.
                // Keep every visible layer inside the configured maximum radius.
                int haloAlpha = BlastVisualProfile.haloAlpha(age);
                if (haloAlpha > 0) {
                    shell(transform, Math.min(blast.snapshot.radius(), radius * 1.025),
                            255, 158, 32, haloAlpha, 0, phase, 0.0);
                }
                if (radius >= 0.15) {
                    shell(transform, radius, 255, 145, 28, 14, 30, phase, 0.2);
                    shell(transform, radius * 0.985, 255, 205, 57, 35, 56, phase, 0.5);
                    shell(transform, radius * 0.955, 255, 250, 207, 20, 50, phase, 0.9);
                    // Three crossing shockfront rings are visible from different angles.
                    // Their six lobes rotate counterclockwise with the blast.
                    double shockRadius = BlastVisualProfile.shockRadius(blast.snapshot.radius(), age);
                    int shockAlpha = BlastVisualProfile.shockAlpha(age);
                    if (shockRadius > 0.25 && shockAlpha > 0) {
                        for (int plane = 0; plane < 3; plane++) {
                            shockRing(transform, shockRadius,
                                    BlastVisualProfile.shockWidth(shockRadius),
                                    shockAlpha, phase, plane);
                        }
                    }
                }
                // Bright release flash: a brief physical white pulse, not a full-screen overlay.
                int flashAlpha = BlastVisualProfile.flashAlpha(age);
                if (flashAlpha > 0) {
                    shell(transform, BlastVisualProfile.flashRadius(blast.snapshot.radius(), age),
                            255, 255, 247, flashAlpha, 0, phase, 0.0);
                }
                // Persistent white-hot center, capped to prevent exceeding the radius.
                double core = Math.min(blast.snapshot.radius(), Math.min(5.0, 1.5 + radius * 0.035));
                shell(transform, core, 255, 255, 230, 70, 20, -age * 0.25, 0.0);
                matrices.pop();
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    private static void shell(Matrix4f matrix, double radius,
                              int red, int green, int blue, int baseAlpha,
                              int stripeAlpha, double phase, double stripeIntensity) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int lat = 0; lat < LATITUDES; lat++) {
            double phi0 = -Math.PI * 0.5 + Math.PI * lat / LATITUDES;
            double phi1 = -Math.PI * 0.5 + Math.PI * (lat + 1) / LATITUDES;
            for (int lon = 0; lon < LONGITUDES; lon++) {
                double theta0 = Math.PI * 2.0 * lon / LONGITUDES;
                double theta1 = Math.PI * 2.0 * (lon + 1) / LONGITUDES;
                vertex(builder, matrix, radius, phi0, theta0, red, green, blue,
                        alpha(baseAlpha, stripeAlpha, stripeIntensity, phi0, theta0, phase));
                vertex(builder, matrix, radius, phi1, theta0, red, green, blue,
                        alpha(baseAlpha, stripeAlpha, stripeIntensity, phi1, theta0, phase));
                vertex(builder, matrix, radius, phi1, theta1, red, green, blue,
                        alpha(baseAlpha, stripeAlpha, stripeIntensity, phi1, theta1, phase));
                vertex(builder, matrix, radius, phi0, theta1, red, green, blue,
                        alpha(baseAlpha, stripeAlpha, stripeIntensity, phi0, theta1, phase));
            }
        }
        tessellator.draw();
    }

    /** Three mutually perpendicular, additive shock rings built using position-color quads. */
    private static void shockRing(Matrix4f matrix, double radius, double width,
                                  int baseAlpha, double phase, int plane) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < SHOCK_SEGMENTS; i++) {
            double a0 = i * Math.PI * 2.0 / SHOCK_SEGMENTS;
            double a1 = (i + 1) * Math.PI * 2.0 / SHOCK_SEGMENTS;
            int alpha0 = BlastVisualProfile.stripeAlpha(baseAlpha, a0, phase);
            int alpha1 = BlastVisualProfile.stripeAlpha(baseAlpha, a1, phase);
            // Thin bright front with slightly warmer inner rim.
            shockVertex(builder, matrix, radius - width, a0, plane, 255, 190, 52, alpha0 / 3);
            shockVertex(builder, matrix, radius, a0, plane, 255, 255, 239, alpha0);
            shockVertex(builder, matrix, radius, a1, plane, 255, 255, 239, alpha1);
            shockVertex(builder, matrix, radius - width, a1, plane, 255, 190, 52, alpha1 / 3);
        }
        tessellator.draw();
    }

    private static void shockVertex(BufferBuilder builder, Matrix4f matrix,
                                     double radius, double angle, int plane,
                                     int r, int g, int b, int a) {
        double u = Math.max(0.0, radius) * Math.cos(angle);
        double v = Math.max(0.0, radius) * Math.sin(angle);
        float x;
        float y;
        float z;
        switch (plane) {
            case 0 -> { x = (float) u; y = 0.0F; z = (float) v; }
            case 1 -> { x = (float) u; y = (float) (v * BlastGeometry.VERTICAL_SCALE); z = 0.0F; }
            default -> { x = 0.0F; y = (float) (v * BlastGeometry.VERTICAL_SCALE); z = (float) u; }
        }
        builder.vertex(matrix, x, y, z).color(r, g, b, a).next();
    }

    private static int alpha(int base, int stripes, double intensity,
                             double latitude, double angle, double phase) {
        double spiral = Math.max(0.0, Math.sin(5.0 * (angle - phase) + latitude * 3.0));
        spiral *= spiral * spiral * spiral;
        return Math.min(255, base + (int) (stripes * intensity * spiral));
    }

    private static void vertex(BufferBuilder builder, Matrix4f transform, double radius,
                               double latitude, double angle,
                               int r, int g, int b, int a) {
        double horizontal = radius * Math.cos(latitude);
        builder.vertex(transform,
                (float) (horizontal * Math.cos(angle)),
                (float) (BlastGeometry.verticalRadius(radius) * Math.sin(latitude)),
                (float) (horizontal * Math.sin(angle)))
                .color(r, g, b, a).next();
    }

    private static final class ActiveBlast {
        private BlastSyncPayload.Snapshot snapshot;
        private int age;
        private ActiveBlast(BlastSyncPayload.Snapshot snapshot) {
            this.snapshot = snapshot;
            this.age = snapshot.age();
        }
        private void update(BlastSyncPayload.Snapshot incoming) {
            age = BlastTimeline.reconcileAge(age, incoming.age());
            snapshot = incoming;
        }
    }
}
