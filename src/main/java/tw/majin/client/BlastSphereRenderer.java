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
import tw.majin.network.ChargeSyncPayload;
import tw.majin.visual.ChargeField;
import tw.majin.visual.ChargeWind;
import tw.majin.visual.ChargeRules;
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
    private static final Map<UUID, ActiveCharge> CHARGES = new HashMap<>();
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
            CHARGES.clear();
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

    /** Receive a stable 3-D charge sphere; remove immediately on G-up or H. */
    public static void acceptCharge(ChargeSyncPayload.Snapshot snapshot) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || !world.getRegistryKey().getValue().equals(snapshot.dimension())) return;
        if (world != rememberedWorld) {
            rememberedWorld = world;
            BLASTS.clear();
            CHARGES.clear();
        }
        if (!snapshot.active()) {
            CHARGES.remove(snapshot.casterId());
        } else {
            ActiveCharge existing = CHARGES.get(snapshot.casterId());
            if (existing == null) CHARGES.put(snapshot.casterId(), new ActiveCharge(snapshot));
            else existing.update(snapshot);
        }
    }

    public static void tick(MinecraftClient client) {
        if (client.world != rememberedWorld) {
            rememberedWorld = client.world;
            BLASTS.clear();
            CHARGES.clear(); // Prevent stale lightballs when changing dimension/world.
        }
        if (client.world == null) return;
        Iterator<ActiveBlast> iterator = BLASTS.values().iterator();
        while (iterator.hasNext()) {
            ActiveBlast blast = iterator.next();
            blast.spinAge++;
            blast.ticksSinceUpdate++;
            if (blast.snapshot.terrainSynced()) {
                // Never predict the wave ahead of terrain the server has confirmed
                // fully processed. The ball will pause if excavation is slow.
                blast.age = BlastTimeline.reconcileAge(blast.age, blast.snapshot.age());
            } else {
                blast.age = BlastTimeline.advanceAge(blast.age);
            }
            if (blast.ticksSinceUpdate > 120 ||
                    (!blast.snapshot.terrainSynced() &&
                            blast.age >= BlastGeometry.DURATION_TICKS + 2)) iterator.remove();
        }
        Iterator<ActiveCharge> chargeIterator = CHARGES.values().iterator();
        while (chargeIterator.hasNext()) {
            ActiveCharge charge = chargeIterator.next();
            charge.age++;
            charge.ticks = Math.min(ChargeRules.MAX_TICKS, charge.ticks + 1);
            // A lost stop packet or vanished chunk cannot leave a charge mesh forever.
            if (++charge.ticksSinceUpdate > 80) chargeIterator.remove();
        }
    }

    private static void render(WorldRenderContext context) {
        if ((BLASTS.isEmpty() && CHARGES.isEmpty()) || context.matrixStack() == null) return;
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
                        blast.age + (blast.snapshot.terrainSynced() ? 0.0 : (double) context.tickDelta()));
                double radius = BlastGeometry.waveRadius(blast.snapshot.radius(), age);
                if (radius < 0.15 && age > BlastVisualProfile.FLASH_TICKS) continue;
                Vec3d center = new Vec3d(blast.snapshot.x(), blast.snapshot.y(), blast.snapshot.z());
                if (center.squaredDistanceTo(camera) > 420.0 * 420.0) continue;
                double phase = BlastGeometry.counterclockwisePhase(
                        blast.spinAge + (double) context.tickDelta(), blast.snapshot.charge());
                matrices.push();
                matrices.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
                Matrix4f transform = matrices.peek().getPositionMatrix();
                // Layered color gradients have rotating five-armed spirals. Since the
                // phase decreases, those bright streaks move CCW when seen from above.
                // Keep every visible layer inside the configured maximum radius.
                int haloAlpha = BlastVisualProfile.haloAlpha(age);
                if (haloAlpha > 0) {
                    shell(transform, radius,
                            255, 158, 32, haloAlpha, 0, phase, 0.0);
                }
                if (radius >= 0.15) {
                    shell(transform, radius, 255, 145, 28, 14, 30, phase, 0.2);
                    shell(transform, radius * 0.985, 255, 205, 57, 35, 56, phase, 0.5);
                    shell(transform, radius * 0.955, 255, 250, 207, 20, 50, phase, 0.9);
                    // Three crossing shockfront rings are visible from different angles.
                    // Their six lobes rotate counterclockwise with the blast.
                    double shockRadius = Math.min(radius,
                            BlastVisualProfile.shockRadius(blast.snapshot.radius(), age));
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
                int flashAlpha = BlastVisualProfile.flashAlpha(blast.spinAge);
                if (flashAlpha > 0) {
                    shell(transform, Math.min(radius,
                            BlastVisualProfile.flashRadius(blast.snapshot.radius(), age)),
                            255, 255, 247, flashAlpha, 0, phase, 0.0);
                }
                // Persistent white-hot center, capped to prevent exceeding the radius.
                double core = Math.min(radius, Math.min(5.0, 1.5 + radius * 0.035));
                shell(transform, core, 255, 255, 230, 70, 20, -age * 0.25, 0.0);
                matrices.pop();
            }
            for (ActiveCharge charge : CHARGES.values()) {
                Vec3d center = new Vec3d(charge.snapshot.x(), charge.snapshot.y(), charge.snapshot.z());
                if (center.squaredDistanceTo(camera) > 100.0 * 100.0) continue;
                double progress = ChargeField.clamp(
                        (charge.ticks + (double) context.tickDelta()) / ChargeRules.MAX_TICKS);
                double radius = ChargeField.sphereRadius(progress);
                double age = charge.age + (double) context.tickDelta();
                double phase = ChargeField.clockwisePhase(age, progress);
                matrices.push();
                matrices.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
                Matrix4f transform = matrices.peek().getPositionMatrix();
                // The charge visual is a genuinely continuous yellow spherical mesh
                // rather than FLAME particles or a ring of points. Radius matches
                // the server-owned sphere collision boundary exactly.
                chargeShell(transform, radius * 1.04, 255, 149, 16,
                        35, 28, phase, 0.35);
                chargeShell(transform, radius, 255, 197, 30,
                        76, 120, phase, 1.0);
                chargeShell(transform, radius * 0.965, 255, 230, 67,
                        82, 74, phase * 1.2, 0.85);
                chargeShell(transform, radius * 0.88, 255, 248, 149,
                        27, 30, phase * 1.4, 0.4);
                // Wispy air streaks slide inward from the full suction range,
                // simultaneously curling clockwise around the sphere.
                windRibbons(transform, age, progress, phase);
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
        surface(matrix, radius, BlastGeometry.VERTICAL_SCALE,
                red, green, blue, baseAlpha, stripeAlpha, phase, stripeIntensity);
    }

    private static void chargeShell(Matrix4f matrix, double radius,
                                    int red, int green, int blue, int baseAlpha,
                                    int stripeAlpha, double phase, double stripeIntensity) {
        surface(matrix, radius, 1.0,
                red, green, blue, baseAlpha, stripeAlpha, phase, stripeIntensity);
    }

    private static void surface(Matrix4f matrix, double radius, double verticalScale,
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
                vertex(builder, matrix, radius, verticalScale, phi0, theta0, red, green, blue,
                        alpha(baseAlpha, stripeAlpha, stripeIntensity, phi0, theta0, phase));
                vertex(builder, matrix, radius, verticalScale, phi1, theta0, red, green, blue,
                        alpha(baseAlpha, stripeAlpha, stripeIntensity, phi1, theta0, phase));
                vertex(builder, matrix, radius, verticalScale, phi1, theta1, red, green, blue,
                        alpha(baseAlpha, stripeAlpha, stripeIntensity, phi1, theta1, phase));
                vertex(builder, matrix, radius, verticalScale, phi0, theta1, red, green, blue,
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

    /**
     * Sparse, independent streaks that start in ALL directions around the ball,
     * then travel almost radially inward to its shell. Golden-angle directions
     * cover the sphere (not a ring); different phases, speeds and short lengths
     * prevent the old uniform vortex / horizontal spiral look.
     */
    private static void windRibbons(Matrix4f matrix, double age,
                                    double progress, double phase) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();
        builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        final int streams = 88;
        for (int stream = 0; stream < streams; stream++) {
            double seed = fract((stream + 1) * 0.61803398875);
            double speed = 0.018 + 0.018 * fract(stream * 0.41421356237);
            double head = fract(age * speed * (1.0 + 0.25 * progress) + seed);
            // Each line is only 2–4 small segments, not a continuous coil.
            int segments = 2 + (stream % 3);
            double trailStep = 0.012 + 0.006 * fract(stream * 0.7548776662);
            for (int segment = 0; segment < segments; segment++) {
                double from = head - (segment + 1) * trailStep;
                double to = head - segment * trailStep;
                if (from <= 0.0 || to >= 1.0) continue;
                Vec3d p0 = windPoint(from, stream, streams, progress, phase);
                Vec3d p1 = windPoint(to, stream, streams, progress, phase);
                Vec3d tangent = p1.subtract(p0);
                Vec3d lateral = new Vec3d(-tangent.z, 0.0, tangent.x);
                if (lateral.lengthSquared() < 0.000001) lateral = new Vec3d(1, 0, 0);
                lateral = lateral.normalize().multiply(0.019 + 0.029 * progress);
                // Fade toward both ends, creating individual wisps in open air.
                int alpha0 = windAlpha(from, progress);
                int alpha1 = windAlpha(to, progress);
                windVertex(builder, matrix, p0, lateral.x, lateral.z, 225, 237, 239, alpha0);
                windVertex(builder, matrix, p0, -lateral.x, -lateral.z, 225, 237, 239, alpha0);
                windVertex(builder, matrix, p1, -lateral.x, -lateral.z, 255, 250, 204, alpha1);
                windVertex(builder, matrix, p1, lateral.x, lateral.z, 255, 250, 204, alpha1);
            }
        }
        tessellator.draw();
    }

    private static int windAlpha(double inward, double progress) {
        double fade = Math.sin(Math.PI * inward);
        return Math.max(0, (int) Math.round((18.0 + 52.0 * progress) * fade * fade));
    }

    private static Vec3d windPoint(double inward, int index, int count,
                                   double progress, double phase) {
        double[] p = ChargeWind.point(inward, index, count, progress, phase);
        return new Vec3d(p[0], p[1], p[2]);
    }

    private static double fract(double x) {
        return x - Math.floor(x);
    }

    private static void windVertex(BufferBuilder builder, Matrix4f matrix, Vec3d position,
                                   double sx, double sz,
                                   int red, int green, int blue, int alpha) {
        builder.vertex(matrix, (float) (position.x + sx), (float) position.y,
                (float) (position.z + sz)).color(red, green, blue, alpha).next();
    }

    private static void vertex(BufferBuilder builder, Matrix4f transform, double radius,
                               double verticalScale, double latitude, double angle,
                               int r, int g, int b, int a) {
        double horizontal = radius * Math.cos(latitude);
        builder.vertex(transform,
                (float) (horizontal * Math.cos(angle)),
                (float) (radius * verticalScale * Math.sin(latitude)),
                (float) (horizontal * Math.sin(angle)))
                .color(r, g, b, a).next();
    }

    private static final class ActiveCharge {
        private ChargeSyncPayload.Snapshot snapshot;
        private int age;
        private int ticks;
        private int ticksSinceUpdate;

        private ActiveCharge(ChargeSyncPayload.Snapshot snapshot) {
            this.snapshot = snapshot;
            this.age = snapshot.visualTicks();
            this.ticks = snapshot.chargeTicks();
        }

        private void update(ChargeSyncPayload.Snapshot newer) {
            this.snapshot = newer;
            this.age = Math.max(age, newer.visualTicks());
            this.ticks = Math.max(ticks, newer.chargeTicks());
            this.ticksSinceUpdate = 0;
        }
    }

    private static final class ActiveBlast {
        private BlastSyncPayload.Snapshot snapshot;
        private int age;
        private int spinAge;
        private int ticksSinceUpdate;
        private ActiveBlast(BlastSyncPayload.Snapshot snapshot) {
            this.snapshot = snapshot;
            this.age = snapshot.age();
            this.spinAge = snapshot.age();
        }
        private void update(BlastSyncPayload.Snapshot incoming) {
            age = BlastTimeline.reconcileAge(age, incoming.age());
            ticksSinceUpdate = 0;
            snapshot = incoming;
        }
    }
}
