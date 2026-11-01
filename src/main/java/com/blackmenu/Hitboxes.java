package com.blackmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Smooth glowing rounded hitboxes. */
final class Hitboxes {
    private Hitboxes() {}

    static void draw(MinecraftClient mc, ClientWorld w, Vec3d cam, double t, float td) {
        for (Entity e : w.getEntities()) {
            if (e == mc.player) continue;
            boolean pl = e instanceof PlayerEntity, mob = e instanceof MobEntity;
            if (pl ? !Cfg.hbPlayers : (mob ? !Cfg.hbMobs : !Cfg.hbOthers)) continue;
            Vec3d p = e.getLerpedPos(td);
            double dx = p.x - cam.x, dy = p.y - cam.y, dz = p.z - cam.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 64 * 64) continue;
            box(e, dx, dy, dz, Math.sqrt(d2), t);
        }
    }

    private static float[] typeColor(Entity e) {
        if (e instanceof PlayerEntity) return new float[]{0.45f, 0.85f, 1f};
        if (e instanceof Monster) return new float[]{1f, 0.3f, 0.3f};
        if (e instanceof AnimalEntity) return new float[]{0.4f, 1f, 0.5f};
        if (e instanceof MobEntity) return new float[]{1f, 0.85f, 0.4f};
        return new float[]{0.9f, 0.9f, 0.9f};
    }

    /** Rounded square outline, 4 arcs x 5 points. */
    private static Gfx.Path loop(double cx, double y, double cz, double hw, double rr, double a, float[] col) {
        Gfx.Path p = new Gfx.Path();
        double in = hw - rr;
        for (int c = 0; c < 4; c++) {
            double sx = (c == 0 || c == 3) ? 1 : -1, sz = (c <= 1) ? 1 : -1;
            for (int k = 0; k < 5; k++) {
                double ang = Math.PI / 2 * (c + k / 4.0);
                p.add(cx + sx * in + Math.cos(ang) * rr, y, cz + sz * in + Math.sin(ang) * rr, 1, a, col);
            }
        }
        return p;
    }

    private static void line(Gfx.Path p, boolean closed, double ws, float thin) {
        Gfx.ribbon(p, closed, false, 0.04 * ws, 0.25f * thin, 0f);
        Gfx.ribbon(p, closed, false, 0.010 * ws, thin, 0.5f);
    }

    private static void box(Entity e, double dx, double dy, double dz, double dist, double t) {
        float[] col = Cfg.hbColor == 0 ? typeColor(e) : Gfx.pal(Cfg.hbColor - 1, t * 0.05 + e.getId() * 0.1);
        double hw = e.getWidth() / 2.0, h = e.getHeight();
        double rr = Math.min(0.14, hw * 0.45);
        double ws = Cfg.hbWidth * (1 + dist * 0.03);   // a little thicker with distance so it stays visible
        float a = Cfg.hbAlpha;

        Gfx.Path bot = loop(dx, dy + 0.01, dz, hw, rr, a * 0.9, col);
        Gfx.Path top = loop(dx, dy + h, dz, hw, rr, a * 0.9, col);
        line(bot, true, ws, 1f);
        line(top, true, ws, 1f);

        for (int c = 0; c < 4; c++) {
            double[] b = bot.pts.get(c * 5 + 2), tp = top.pts.get(c * 5 + 2);
            Gfx.Path v = new Gfx.Path();
            v.add(b[0], b[1], b[2], 1, a * 0.8, col);
            v.add(tp[0], tp[1], tp[2], 1, a * 0.8, col);
            line(v, false, ws, 1f);
        }

        if (Cfg.hbFill) {
            int n = bot.pts.size();
            for (int k = 0; k < n; k++) {
                double[] b0 = bot.pts.get(k), b1 = bot.pts.get((k + 1) % n);
                double[] t0 = top.pts.get(k), t1 = top.pts.get((k + 1) % n);
                Gfx.quad(new Gfx.V(b0[0], b0[1], b0[2], col, 0.12f * a),
                        new Gfx.V(b1[0], b1[1], b1[2], col, 0.12f * a),
                        new Gfx.V(t1[0], t1[1], t1[2], col, 0.02f * a),
                        new Gfx.V(t0[0], t0[1], t0[2], col, 0.02f * a));
            }
        }

        if (Cfg.hbEye) {
            double ey = e.getEyeY() - e.getY();
            Gfx.Path eye = loop(dx, dy + ey, dz, hw, rr, a * 0.55, col);
            Gfx.ribbon(eye, true, false, 0.03 * ws, 0.2f, 0f);
            Gfx.ribbon(eye, true, false, 0.007 * ws, 1f, 0.4f);
        }
    }
}
