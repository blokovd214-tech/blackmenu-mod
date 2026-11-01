package com.blackmenu;

import net.minecraft.util.math.Vec3d;

/** Falling / floating particles on a world-anchored lattice (no simulation, no allocation per tick). */
final class Falling {
    enum Kind { SNOW, LEAF, PETAL, POLLEN, ASH, EMBER, RAIN }

    private Falling() {}

    static double hash(int n) {
        double s = Math.sin(n * 12.9898) * 43758.5453;
        return s - Math.floor(s);
    }

    /** The value congruent to v (mod period) that is closest to c. */
    static double wrapNear(double c, double v, double period) {
        return v + period * Math.round((c - v) / period);
    }

    static void draw(Kind k, Vec3d cam, double t, double dens, double size, double speed, double wind,
                     double rad, double cy, double hgt, float[][] pal, float alphaMul) {
        int n = (int) ((k == Kind.RAIN ? 450 : 260) * dens * (rad / 16.0));
        double period = 2 * rad;
        for (int i = 0; i < n; i++) {
            double h1 = hash(i * 7 + 1), h2 = hash(i * 7 + 2), h3 = hash(i * 7 + 3),
                    h4 = hash(i * 7 + 4), h5 = hash(i * 7 + 5);
            double x = wrapNear(cam.x, h1 * period + wind * 0.5 * t, period);
            double z = wrapNear(cam.z, h2 * period, period);
            double fall = speed * (0.7 + 0.6 * h3);
            double y = wrapNear(cy, h4 * hgt - fall * t, hgt);
            double sway = k == Kind.RAIN ? 0 : 0.5 * wind;
            x += Math.sin(t * 0.9 + h5 * 6.283) * sway;
            z += Math.cos(t * 0.7 + h3 * 6.283) * sway;

            double dx = x - cam.x, dz = z - cam.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > rad) continue;
            double edge = Math.min(1, (rad - dist) / (rad * 0.3));
            double yy = y - (cy - hgt / 2);
            double vf = Math.min(1, Math.min(yy, hgt - yy) / 2.0);
            float top = Weather.topAt(x, z);
            if (!Float.isNaN(top) && y < top) continue;          // below the ground or under a roof
            float al = (float) (edge * vf) * alphaMul;
            if (al < 0.01f) continue;

            double rx = x - cam.x, ry = y - cam.y, rz = z - cam.z;
            float[] col = pal[(int) (h5 * pal.length) % pal.length];
            double sz = size * (0.7 + 0.6 * h5);
            switch (k) {
                case SNOW -> Shapes.dot(rx, ry, rz, 0.05 * sz, col, al * 0.9f);
                case POLLEN -> Shapes.dot(rx, ry, rz, 0.035 * sz, col,
                        al * (0.4f + 0.4f * (float) Math.sin(t * 2 + h4 * 9)));
                case ASH -> Shapes.dot(rx, ry, rz, 0.04 * sz, col, al * 0.6f);
                case EMBER -> Shapes.dot(rx, ry, rz, 0.035 * sz, col,
                        al * (0.5f + 0.5f * (float) Math.sin(t * 8 + h4 * 20)));
                case RAIN -> {
                    Gfx.Path p = new Gfx.Path();
                    double len = 0.7 * size;
                    p.add(rx - 0.08 * wind, ry + len, rz, 1, 0, col);
                    p.add(rx, ry, rz, 1, al * 0.9, col);
                    Gfx.ribbon(p, false, false, 0.012 * size, 1f, 0.2f);
                }
                case LEAF, PETAL -> {
                    double rot = t * (1.5 + h4 * 2) + h5 * 6.283;
                    double l = (k == Kind.LEAF ? 0.10 : 0.07) * size;
                    double ex = Math.cos(rot) * l, ey = Math.sin(rot * 1.7) * l * 0.35, ez = Math.sin(rot) * l;
                    Gfx.Path p = new Gfx.Path();
                    p.add(rx - ex, ry - ey, rz - ez, 0.25, al * 0.7, col);
                    p.add(rx, ry, rz, 1, al * 0.9, col);
                    p.add(rx + ex, ry + ey, rz + ez, 0.25, al * 0.7, col);
                    Gfx.ribbon(p, false, false, 0.05 * size, 1f, 0.1f);
                }
            }
        }
    }
}
