package com.blackmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/** Smooth glowing effects around the player / animal / mob you are aiming at. Modes: see Mods.ESP. */
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

    private static float[] col(Entity e, double phase) {
        float[] c = Gfx.pal(Cfg.espColor, phase);
        if (Cfg.espFlash && e instanceof LivingEntity le && le.hurtTime > 0) {
            float f = Math.min(1f, le.hurtTime / 6f);
            return new float[]{c[0] + (1f - c[0]) * f, c[1] * (1 - f) + 0.15f * f, c[2] * (1 - f) + 0.15f * f};
        }
        return c;
    }

    static void draw(Entity e, Vec3d cam, double t, float td) {
        Vec3d p = e.getLerpedPos(td);
        double bx = p.x - cam.x, by = p.y - cam.y, bz = p.z - cam.z;
        double h = e.getHeight(), sz = Cfg.espSize;
        double rw = e.getWidth() * 0.5 + 0.22 * sz;
        double tt = t * Cfg.espSpeed;
        float a = Cfg.espAlpha * fade;

        mode(Cfg.espMode, e, bx, by, bz, h, rw, sz, tt, a);
        if (Cfg.espMode2 > 0) mode(Cfg.espMode2 - 1, e, bx, by, bz, h, rw, sz, tt, a);
    }

    private static void mode(int m, Entity e, double bx, double by, double bz, double h, double rw,
                             double sz, double tt, float a) {
        switch (m) {
            case 0 -> lightning(e, bx, by, bz, h, rw, sz, tt, a);
            case 1 -> circle(e, bx, by, bz, h, rw, sz, tt, a);
            case 2 -> chains(e, bx, by, bz, h, rw, sz, tt, a);
            case 3 -> orbs(e, bx, by, bz, h, rw, sz, tt, a);
            case 4 -> crystals(e, bx, by, bz, h, rw, sz, tt, a);
            case 5 -> halo(e, bx, by, bz, h, sz, tt, a);
            case 6 -> helix(e, bx, by, bz, h, rw, sz, tt, a);
            case 7 -> beam(e, bx, by, bz, h, rw, sz, tt, a);
            default -> runes(e, bx, by, bz, rw, sz, tt, a);
        }
    }

    // ------------------------------------------------------------------ modes

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
                        env * a * Math.pow(taper, 0.5), col(e, i * 0.17 + s * 0.2 + tt * 0.05));
            }
            Gfx.ribbon(path, false, false, 0.085 * sz, 0.28f, 0f);
            Gfx.ribbon(path, false, false, 0.016 * sz, 1f, 0.7f);
        }
    }

    private static void ringPath(Entity e, Gfx.Path p, double cx, double y, double cz, double r, double a, double tt) {
        int n = 64;
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n;
            p.add(cx + Math.cos(ang) * r, y, cz + Math.sin(ang) * r, 1, a, col(e, ang / (Math.PI * 2) + tt * 0.05));
        }
    }

    private static void circle(Entity e, double bx, double by, double bz, double h, double rw,
                               double sz, double tt, float a) {
        double rr = rw * 1.1 + 0.15 * sz;
        int n = 72;
        Gfx.Path ring = new Gfx.Path();
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n;
            double hl = 0.5 + 0.5 * Math.sin(2 * ang - tt * 2.2);
            ring.add(bx + Math.cos(ang) * rr, by + 0.03, bz + Math.sin(ang) * rr, 1,
                    a * (0.30 + 0.70 * hl), col(e, ang / (Math.PI * 2) + tt * 0.05));
        }
        Gfx.ribbon(ring, true, true, 0.14 * sz, 0.35f, 0f);
        Gfx.ribbon(ring, true, true, 0.03 * sz, 1f, 0.6f);

        double u = 0.5 + 0.5 * Math.sin(tt * 1.6);
        double env = Math.pow(Math.sin(Math.PI * u), 0.5);
        Gfx.Path sc = new Gfx.Path();
        ringPath(e, sc, bx, by + h * u, bz, rw * 0.95, a * 0.8 * env, tt);
        Gfx.ribbon(sc, true, true, 0.08 * sz, 0.3f, 0f);
        Gfx.ribbon(sc, true, true, 0.015 * sz, 1f, 0.6f);
    }

    private static void chains(Entity e, double bx, double by, double bz, double h, double rw,
                               double sz, double tt, float a) {
        int links = 12;
        double span = h + 0.2, turns = Math.PI * 2 * 1.25;
        for (int c = 0; c < 2; c++) {
            for (int j = 0; j < links; j++) {
                double s = (j + 0.5) / links;
                double ang = c * Math.PI + tt * 1.1 + s * turns;
                double rad = rw + 0.02;
                double cx = bx + Math.cos(ang) * rad, cy = by + s * span, cz = bz + Math.sin(ang) * rad;

                double tx = -Math.sin(ang) * rad * turns, ty = span, tz = Math.cos(ang) * rad * turns;
                double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                tx /= tl; ty /= tl; tz /= tl;
                double nx = Math.cos(ang), ny = 0, nz = Math.sin(ang);
                double qx = ty * nz - tz * ny, qy = tz * nx - tx * nz, qz = tx * ny - ty * nx;
                double ql = Math.sqrt(qx * qx + qy * qy + qz * qz);
                qx /= ql; qy /= ql; qz /= ql;

                boolean even = j % 2 == 0;
                double ox = even ? nx : qx, oy = even ? ny : qy, oz = even ? nz : qz;
                double la = 0.09 * sz, lb = 0.05 * sz;
                double al = a * 0.95 * Math.pow(Math.sin(Math.PI * s), 0.35);
                float[] cc = col(e, s + c * 0.5 + tt * 0.05);

                Gfx.Path lk = new Gfx.Path();
                for (int q = 0; q < 12; q++) {
                    double ph = Math.PI * 2 * q / 12, cs = Math.cos(ph), sn = Math.sin(ph);
                    lk.add(cx + tx * la * cs + ox * lb * sn, cy + ty * la * cs + oy * lb * sn,
                            cz + tz * la * cs + oz * lb * sn, 1, al, cc);
                }
                Gfx.ribbon(lk, true, false, 0.035 * sz, 0.3f, 0f);
                Gfx.ribbon(lk, true, false, 0.011 * sz, 1f, 0.6f);
            }
        }
    }

    private static void orbs(Entity e, double bx, double by, double bz, double h, double rw,
                             double sz, double tt, float a) {
        int count = Math.max(1, Math.min(12, (int) Cfg.espCount));
        for (int i = 0; i < count; i++) {
            float[] cc = col(e, i / (double) count + tt * 0.03);
            Gfx.Path tr = new Gfx.Path();
            double hx = 0, hy = 0, hz = 0;
            int len = 18;
            for (int k = 0; k < len; k++) {
                double tk = tt - k * 0.035;
                double ang = i * Math.PI * 2 / count + tk * (2.2 + 0.3 * (i % 3));
                double yy = h * (0.2 + 0.6 * (0.5 + 0.5 * Math.sin(tk * 1.3 + i * 2.1)));
                double rr = rw + 0.04 * sz;
                double x = bx + Math.cos(ang) * rr, y = by + yy, z = bz + Math.sin(ang) * rr;
                double f = 1 - k / (double) (len - 1);
                tr.add(x, y, z, f, a * 0.9 * f * f, cc);
                if (k == 0) { hx = x; hy = y; hz = z; }
            }
            Gfx.ribbon(tr, false, false, 0.06 * sz, 0.3f, 0f);
            Gfx.ribbon(tr, false, false, 0.016 * sz, 1f, 0.5f);
            Gfx.disc(hx, hy, hz, 0.22 * sz, cc, a * 0.9f);
        }
    }

    private static void crystals(Entity e, double bx, double by, double bz, double h, double rw,
                                 double sz, double tt, float a) {
        int count = Math.max(1, Math.min(12, (int) Cfg.espCount));
        for (int i = 0; i < count; i++) {
            double ang = tt * 1.3 + i * Math.PI * 2 / count;
            double rr = rw + 0.15 * sz;
            double yy = h * (0.5 + 0.35 * Math.sin(tt * 1.1 + i * 1.7));
            Shapes.crystal(bx + Math.cos(ang) * rr, by + yy, bz + Math.sin(ang) * rr,
                    0.07 * sz, 0.16 * sz, tt * 2 + i, col(e, i / (double) count + tt * 0.05), a);
        }
    }

    private static void halo(Entity e, double bx, double by, double bz, double h, double sz, double tt, float a) {
        double hy = by + h + 0.22 * sz + Math.sin(tt * 2) * 0.03;
        double hr = (0.28 + e.getWidth() * 0.15) * sz;
        double al = a * (0.75 + 0.25 * Math.sin(tt * 3));
        Gfx.Path ring = new Gfx.Path();
        int n = 48;
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n;
            ring.add(bx + Math.cos(ang) * hr, hy, bz + Math.sin(ang) * hr, 1, al, col(e, ang / (Math.PI * 2) + tt * 0.05));
        }
        Gfx.ribbon(ring, true, true, 0.09 * sz, 0.35f, 0f);
        Gfx.ribbon(ring, true, true, 0.02 * sz, 1f, 0.6f);
        for (int j = 0; j < 4; j++) {
            double ang = tt * 2 + j * Math.PI / 2;
            Shapes.dot(bx + Math.cos(ang) * hr, hy, bz + Math.sin(ang) * hr, 0.06 * sz, col(e, j * 0.25), a);
        }
    }

    private static void helix(Entity e, double bx, double by, double bz, double h, double rw,
                              double sz, double tt, float a) {
        int per = 14;
        double span = h + 0.2;
        for (int k = 0; k < per; k++) {
            double u = ((k / (double) per) + tt * 0.3) % 1.0;
            double y = u * span;
            float al = (float) (a * Math.pow(Math.sin(Math.PI * u), 0.6));
            double[][] pos = new double[2][];
            for (int s = 0; s < 2; s++) {
                double ang = y * 7 + s * Math.PI + tt * 2.4;
                pos[s] = new double[]{bx + Math.cos(ang) * rw, by + y, bz + Math.sin(ang) * rw};
                Shapes.dot(pos[s][0], pos[s][1], pos[s][2], 0.04 * sz, col(e, u + s * 0.5), al);
            }
            if (k % 2 == 0) {
                float[] cc = col(e, u);
                Gfx.Path rung = new Gfx.Path();
                rung.add(pos[0][0], pos[0][1], pos[0][2], 1, al * 0.6, cc);
                rung.add(pos[1][0], pos[1][1], pos[1][2], 1, al * 0.6, cc);
                Gfx.ribbon(rung, false, false, 0.03 * sz, 0.3f, 0f);
                Gfx.ribbon(rung, false, false, 0.008 * sz, 1f, 0.5f);
            }
        }
    }

    private static void beam(Entity e, double bx, double by, double bz, double h, double rw,
                             double sz, double tt, float a) {
        int n = 14;
        double topY = h + 24;
        Gfx.Path p = new Gfx.Path();
        for (int i = 0; i < n; i++) {
            double s = i / (double) (n - 1);
            p.add(bx, by - 0.1 + s * topY, bz, 1, a * Math.pow(1 - s, 1.2) * 0.9, col(e, s * 0.3 + tt * 0.05));
        }
        Gfx.ribbon(p, false, false, 0.35 * sz, 0.22f, 0f);
        Gfx.ribbon(p, false, false, 0.06 * sz, 1f, 0.6f);
        for (int r = 0; r < 3; r++) {
            double u = (tt * 0.5 + r / 3.0) % 1.0;
            double rad = rw * 0.6 + (0.3 + 0.25 * u) * sz;
            Gfx.Path ring = new Gfx.Path();
            ringPath(e, ring, bx, by + u * (h + 3), bz, rad, a * (1 - u) * 0.8, tt);
            Gfx.ribbon(ring, true, true, 0.06 * sz, 0.3f, 0f);
            Gfx.ribbon(ring, true, true, 0.015 * sz, 1f, 0.6f);
        }
    }

    private static void runes(Entity e, double bx, double by, double bz, double rw, double sz, double tt, float a) {
        double r1 = rw * 1.25 + 0.4 * sz, r2 = r1 * 0.72, y = by + 0.03;
        Gfx.Path outer = new Gfx.Path(), inner = new Gfx.Path();
        ringPath(e, outer, bx, y, bz, r1, a * 0.9, tt);
        ringPath(e, inner, bx, y, bz, r2, a * 0.7, tt);
        for (Gfx.Path p : new Gfx.Path[]{outer, inner}) {
            Gfx.ribbon(p, true, true, 0.1 * sz, 0.3f, 0f);
            Gfx.ribbon(p, true, true, 0.02 * sz, 1f, 0.6f);
        }
        for (int k = 0; k < 12; k++) {
            double ang = k * Math.PI * 2 / 12 + tt * 0.5;
            float[] cc = col(e, k / 12.0);
            Gfx.Path mk = new Gfx.Path();
            mk.add(bx + Math.cos(ang) * (r2 + 0.05), y, bz + Math.sin(ang) * (r2 + 0.05), 1, a * 0.9, cc);
            mk.add(bx + Math.cos(ang) * (r1 - 0.05), y, bz + Math.sin(ang) * (r1 - 0.05), 1, a * 0.9, cc);
            Gfx.ribbon(mk, false, true, 0.05 * sz, 0.3f, 0f);
            Gfx.ribbon(mk, false, true, 0.012 * sz, 1f, 0.6f);
        }
        for (int tri = 0; tri < 2; tri++) {
            double rot = (tri == 0 ? 1 : -1) * tt * 0.4 + tri * Math.PI / 3;
            Gfx.Path tp = new Gfx.Path();
            for (int j = 0; j < 3; j++) {
                double ang = rot + j * Math.PI * 2 / 3;
                tp.add(bx + Math.cos(ang) * r2 * 0.95, y, bz + Math.sin(ang) * r2 * 0.95, 1, a * 0.8, col(e, j / 3.0 + tri * 0.5));
            }
            Gfx.ribbon(tp, true, true, 0.06 * sz, 0.3f, 0f);
            Gfx.ribbon(tp, true, true, 0.015 * sz, 1f, 0.6f);
        }
    }
}
