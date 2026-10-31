package com.blackmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/** Smooth glowing effects around the player / animal / mob you are aiming at. */
final class TargetEsp {
    private static Entity cur;
    private static float fade;
    private static long lastNs;

    private TargetEsp() {}

    /** Returns the entity to draw effects on (with fade in/out), or null. */
    static Entity pick(MinecraftClient mc) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1.0e9f);
        lastNs = now;
        Entity t = Cfg.esp ? mc.targetedEntity : null;
        if (t instanceof PlayerEntity || t instanceof MobEntity) {
            cur = t;
            fade = Math.min(1f, fade + dt * 6f);
        } else {
            fade = Math.max(0f, fade - dt * 4f);
        }
        if (cur == null || cur.isRemoved() || fade <= 0.01f) return null;
        return cur;
    }

    static void draw(Entity e, Vec3d cam, double t, float td) {
        Vec3d p = e.getLerpedPos(td);
        double bx = p.x - cam.x, by = p.y - cam.y, bz = p.z - cam.z;
        double h = e.getHeight();
        double sz = Cfg.espSize;
        double rw = e.getWidth() * 0.5 + 0.22 * sz;
        double tt = t * Cfg.espSpeed;
        float a = Cfg.espAlpha * fade;

        if (Cfg.espLightning) lightning(e, bx, by, bz, h, rw, sz, tt, a);
        if (Cfg.espCircle) circle(bx, by, bz, h, rw, sz, tt, a);
        if (Cfg.espChains) chains(bx, by, bz, h, rw, sz, tt, a);
        if (Cfg.espOrbs) orbs(bx, by, bz, h, rw, sz, tt, a);
    }

    private static void lightning(Entity e, double bx, double by, double bz, double h, double rw,
                                  double sz, double tt, float a) {
        for (int i = 0; i < 5; i++) {
            double period = 0.9 + 0.17 * i;
            double x = (tt + i * 0.37) / period;
            long cycle = (long) Math.floor(x);
            double u = x - cycle;
            double env = Math.pow(Math.sin(Math.PI * u), 1.5);
            if (env < 0.01) continue;

            Random r = new Random(e.getId() * 7919L + i * 104729L + cycle * 1299709L);
            double a0 = r.nextDouble() * Math.PI * 2;
            double sweep = (0.8 + r.nextDouble() * 1.4) * (r.nextBoolean() ? 1 : -1);
            double y0 = h * (0.1 + 0.8 * r.nextDouble());
            double dy = (r.nextDouble() - 0.5) * h * 0.9;

            int k = 6;
            double[][] ctl = new double[k][3];
            for (int q = 0; q < k; q++) {
                double s = q / (double) (k - 1);
                double j = (q == 0 || q == k - 1) ? 0 : 0.10 * sz;
                double ang = a0 + sweep * s;
                double rad = rw + (r.nextDouble() - 0.5) * 0.1 * sz;
                double wob = Math.sin(tt * 3 + q * 1.7 + i) * 0.025 * sz;
                ctl[q][0] = bx + Math.cos(ang) * rad + (r.nextDouble() - 0.5) * 2 * j + wob;
                ctl[q][1] = by + y0 + dy * s + (r.nextDouble() - 0.5) * 2 * j;
                ctl[q][2] = bz + Math.sin(ang) * rad + (r.nextDouble() - 0.5) * 2 * j - wob;
            }
            double[][] pts = Gfx.spline(ctl, 5);
            Gfx.Path path = new Gfx.Path();
            for (int q = 0; q < pts.length; q++) {
                double s = q / (double) (pts.length - 1);
                double taper = Math.sin(Math.PI * s);
                path.add(pts[q][0], pts[q][1], pts[q][2], Math.pow(taper, 0.6),
                        env * a * Math.pow(taper, 0.5), Gfx.pal(Cfg.espColor, i * 0.17 + s * 0.2 + tt * 0.05));
            }
            Gfx.ribbon(path, false, false, 0.085 * sz, 0.28f, 0f);
            Gfx.ribbon(path, false, false, 0.016 * sz, 1f, 0.7f);
        }
    }

    private static void circle(double bx, double by, double bz, double h, double rw,
                               double sz, double tt, float a) {
        // ground ring with a slowly rotating highlight
        double rr = rw * 1.1 + 0.15 * sz;
        int n = 72;
        Gfx.Path ring = new Gfx.Path();
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n;
            double hl = 0.5 + 0.5 * Math.sin(2 * ang - tt * 2.2);
            ring.add(bx + Math.cos(ang) * rr, by + 0.03, bz + Math.sin(ang) * rr, 1,
                    a * (0.30 + 0.70 * hl), Gfx.pal(Cfg.espColor, ang / (Math.PI * 2) + tt * 0.05));
        }
        Gfx.ribbon(ring, true, true, 0.14 * sz, 0.35f, 0f);
        Gfx.ribbon(ring, true, true, 0.03 * sz, 1f, 0.6f);

        // scanner ring gliding up and down the body
        double u = 0.5 + 0.5 * Math.sin(tt * 1.6);
        double env = Math.pow(Math.sin(Math.PI * u), 0.5);
        double rs = rw * 0.95;
        Gfx.Path sc = new Gfx.Path();
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n;
            sc.add(bx + Math.cos(ang) * rs, by + h * u, bz + Math.sin(ang) * rs, 1,
                    a * 0.8 * env, Gfx.pal(Cfg.espColor, ang / (Math.PI * 2) + tt * 0.05));
        }
        Gfx.ribbon(sc, true, true, 0.08 * sz, 0.3f, 0f);
        Gfx.ribbon(sc, true, true, 0.015 * sz, 1f, 0.6f);
    }

    private static void chains(double bx, double by, double bz, double h, double rw,
                               double sz, double tt, float a) {
        int links = 12;
        double span = h + 0.2;
        double turns = Math.PI * 2 * 1.25;
        for (int c = 0; c < 2; c++) {
            for (int j = 0; j < links; j++) {
                double s = (j + 0.5) / links;
                double ang = c * Math.PI + tt * 1.1 + s * turns;
                double rad = rw + 0.02;
                double cx = bx + Math.cos(ang) * rad, cy = by + s * span, cz = bz + Math.sin(ang) * rad;

                // tangent T, radial N, binormal B
                double tx = -Math.sin(ang) * rad * turns, ty = span, tz = Math.cos(ang) * rad * turns;
                double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                tx /= tl; ty /= tl; tz /= tl;
                double nx = Math.cos(ang), ny = 0, nz = Math.sin(ang);
                double qx = ty * nz - tz * ny, qy = tz * nx - tx * nz, qz = tx * ny - ty * nx;
                double ql = Math.sqrt(qx * qx + qy * qy + qz * qz);
                qx /= ql; qy /= ql; qz /= ql;

                // every second link is turned 90 degrees so they interlock
                double ox = (j % 2 == 0) ? nx : qx, oy = (j % 2 == 0) ? ny : qy, oz = (j % 2 == 0) ? nz : qz;
                double la = 0.09 * sz, lb = 0.05 * sz;
                double al = a * 0.95 * Math.pow(Math.sin(Math.PI * s), 0.35);
                float[] col = Gfx.pal(Cfg.espColor, s + c * 0.5 + tt * 0.05);

                Gfx.Path lk = new Gfx.Path();
                for (int q = 0; q < 12; q++) {
                    double ph = Math.PI * 2 * q / 12, cs = Math.cos(ph), sn = Math.sin(ph);
                    lk.add(cx + tx * la * cs + ox * lb * sn,
                            cy + ty * la * cs + oy * lb * sn,
                            cz + tz * la * cs + oz * lb * sn, 1, al, col);
                }
                Gfx.ribbon(lk, true, false, 0.035 * sz, 0.3f, 0f);
                Gfx.ribbon(lk, true, false, 0.011 * sz, 1f, 0.6f);
            }
        }
    }

    private static void orbs(double bx, double by, double bz, double h, double rw,
                             double sz, double tt, float a) {
        for (int i = 0; i < 3; i++) {
            float[] col = Gfx.pal(Cfg.espColor, i / 3.0 + tt * 0.03);
            Gfx.Path tr = new Gfx.Path();
            double hx = 0, hy = 0, hz = 0;
            int len = 18;
            for (int k = 0; k < len; k++) {
                double tk = tt - k * 0.035;
                double ang = i * Math.PI * 2 / 3 + tk * (2.2 + 0.5 * i);
                double yy = h * (0.2 + 0.6 * (0.5 + 0.5 * Math.sin(tk * 1.3 + i * 2.1)));
                double rr = rw + 0.04 * sz;
                double x = bx + Math.cos(ang) * rr, y = by + yy, z = bz + Math.sin(ang) * rr;
                double f = 1 - k / (double) (len - 1);
                tr.add(x, y, z, f, a * 0.9 * f * f, col);
                if (k == 0) { hx = x; hy = y; hz = z; }
            }
            Gfx.ribbon(tr, false, false, 0.06 * sz, 0.3f, 0f);
            Gfx.ribbon(tr, false, false, 0.016 * sz, 1f, 0.5f);
            Gfx.disc(hx, hy, hz, 0.22 * sz, col, a * 0.9f);
        }
    }
}
