package com.blackmenu;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/** Draws electric bolts around the player / animal / mob you are aiming at. */
public final class TargetEsp {
    private static final int BOLTS = 8, SEGMENTS = 6;

    private TargetEsp() {}

    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!BlackMenuClient.targetEsp || mc.world == null) return;

        Entity e = mc.targetedEntity;
        if (!(e instanceof PlayerEntity || e instanceof MobEntity)) return;

        VertexConsumerProvider consumers = ctx.consumers();
        MatrixStack m = ctx.matrixStack();
        if (consumers == null || m == null) return;

        float td = mc.getRenderTickCounter().getTickDelta(false);
        Vec3d cam = ctx.camera().getPos();
        Vec3d pos = e.getLerpedPos(td);
        double w = e.getWidth() * 0.5 + 0.25;
        double h = e.getHeight();

        m.push();
        m.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        MatrixStack.Entry entry = m.peek();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());

        // new bolt shapes every 2 ticks -> flicker
        Random rnd = new Random(e.getId() * 341873128712L + (mc.world.getTime() / 2) * 132897987541L);

        for (int i = 0; i < BOLTS; i++) {
            double a0 = rnd.nextDouble() * Math.PI * 2;
            double a1 = a0 + (rnd.nextDouble() - 0.5) * 2.2;
            double y0 = rnd.nextDouble() * h;
            double y1 = Math.max(-0.1, Math.min(h + 0.2, y0 + (rnd.nextDouble() - 0.5) * h * 1.2));

            double[][] p = new double[SEGMENTS + 1][3];
            for (int k = 0; k <= SEGMENTS; k++) {
                double t = (double) k / SEGMENTS;
                double ang = a0 + (a1 - a0) * t;
                double rad = w + Math.sin(t * Math.PI) * 0.12;
                double j = (k == 0 || k == SEGMENTS) ? 0 : 0.14;
                p[k][0] = Math.cos(ang) * rad + (rnd.nextDouble() - 0.5) * j;
                p[k][1] = y0 + (y1 - y0) * t + (rnd.nextDouble() - 0.5) * j;
                p[k][2] = Math.sin(ang) * rad + (rnd.nextDouble() - 0.5) * j;
            }

            for (int k = 0; k < SEGMENTS; k++) {
                line(vc, entry, p[k], p[k + 1], 0.35f, 0.75f, 1f, 0.55f, 0.02);
                line(vc, entry, p[k], p[k + 1], 0.35f, 0.75f, 1f, 0.55f, -0.02);
                line(vc, entry, p[k], p[k + 1], 1f, 1f, 1f, 1f, 0);
            }
        }

        m.pop();
        if (consumers instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLines());
    }

    private static void line(VertexConsumer vc, MatrixStack.Entry e, double[] p0, double[] p1,
                             float cr, float cg, float cb, float ca, double off) {
        float dx = (float) (p1[0] - p0[0]), dy = (float) (p1[1] - p0[1]), dz = (float) (p1[2] - p0[2]);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) return;
        dx /= len; dy /= len; dz /= len;
        int r = (int) (cr * 255), g = (int) (cg * 255), b = (int) (cb * 255), a = (int) (ca * 255);
        vc.vertex(e.getPositionMatrix(), (float) (p0[0] + off), (float) p0[1], (float) (p0[2] + off))
                .color(r, g, b, a).normal(e, dx, dy, dz);
        vc.vertex(e.getPositionMatrix(), (float) (p1[0] + off), (float) p1[1], (float) (p1[2] + off))
                .color(r, g, b, a).normal(e, dx, dy, dz);
    }
}
