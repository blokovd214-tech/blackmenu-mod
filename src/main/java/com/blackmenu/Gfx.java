package com.blackmenu;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Smooth glow geometry: soft ribbons and discs drawn with additive blending. All coordinates are camera-relative. */
final class Gfx {
    static VertexConsumer vc;
    static Matrix4f m;

    static final String[] PALETTE = {"Cyan", "Purple", "Pink", "Red", "Green", "Gold", "White", "Rainbow"};

    record V(double x, double y, double z, float[] c, float a) {}

    static final class Path {
        final List<double[]> pts = new ArrayList<>();

        /** w = width multiplier, a = alpha */
        void add(double x, double y, double z, double w, double a, float[] c) {
            pts.add(new double[]{x, y, z, w, a, c[0], c[1], c[2]});
        }
    }

    private Gfx() {}

    static float[] pal(int idx, double phase) {
        return switch (idx) {
            case 0 -> new float[]{0.30f, 0.80f, 1.00f};
            case 1 -> new float[]{0.65f, 0.35f, 1.00f};
            case 2 -> new float[]{1.00f, 0.40f, 0.75f};
            case 3 -> new float[]{1.00f, 0.25f, 0.25f};
            case 4 -> new float[]{0.30f, 1.00f, 0.50f};
            case 5 -> new float[]{1.00f, 0.80f, 0.30f};
            case 6 -> new float[]{1f, 1f, 1f};
            default -> hsv(phase, 0.7, 1.0);
        };
    }

    static float[] hsv(double h, double s, double v) {
        double hh = (h - Math.floor(h)) * 6;
        int i = (int) hh;
        double f = hh - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        double r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return new float[]{(float) r, (float) g, (float) b};
    }

    private static void emit(V v) {
        float a = Math.max(0f, Math.min(1f, v.a()));
        vc.vertex(m, (float) v.x(), (float) v.y(), (float) v.z()).color(v.c()[0], v.c()[1], v.c()[2], a);
    }

    /** Emitted with both windings so it is visible from any side. */
    static void quad(V a, V b, V c, V d) {
        emit(a); emit(b); emit(c); emit(d);
        emit(d); emit(c); emit(b); emit(a);
    }

    /**
     * Soft ribbon along a path: bright in the middle, fading to nothing at the edges.
     * flat = lies horizontally (rings), otherwise it turns to face the camera.
     */
    static void ribbon(Path path, boolean closed, boolean flat, double wScale, float aScale, float whiten) {
        List<double[]> p = path.pts;
        int n = p.size();
        if (n < 2) return;
        V[] cv = new V[n], lv = new V[n], rv = new V[n];
        for (int i = 0; i < n; i++) {
            double[] cur = p.get(i);
            double[] pr = p.get(closed ? (i - 1 + n) % n : Math.max(i - 1, 0));
            double[] nx = p.get(closed ? (i + 1) % n : Math.min(i + 1, n - 1));
            double tx = nx[0] - pr[0], ty = nx[1] - pr[1], tz = nx[2] - pr[2];
            double vx, vy, vz;
            if (flat) { vx = 0; vy = 1; vz = 0; }
            else { vx = -cur[0]; vy = -cur[1]; vz = -cur[2]; }
            double sx = ty * vz - tz * vy, sy = tz * vx - tx * vz, sz = tx * vy - ty * vx;
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-9) { sx = 0; sy = 1; sz = 0; sl = 1; }
            double w = cur[3] * wScale / sl;
            sx *= w; sy *= w; sz *= w;
            float[] col = {
                    (float) (cur[5] + (1 - cur[5]) * whiten),
                    (float) (cur[6] + (1 - cur[6]) * whiten),
                    (float) (cur[7] + (1 - cur[7]) * whiten)};
            cv[i] = new V(cur[0], cur[1], cur[2], col, (float) cur[4] * aScale);
            lv[i] = new V(cur[0] + sx, cur[1] + sy, cur[2] + sz, col, 0f);
            rv[i] = new V(cur[0] - sx, cur[1] - sy, cur[2] - sz, col, 0f);
        }
        int segs = closed ? n : n - 1;
        for (int i = 0; i < segs; i++) {
            int j = (i + 1) % n;
            quad(lv[i], cv[i], cv[j], lv[j]);
            quad(cv[i], rv[i], rv[j], cv[j]);
        }
    }

    /** Soft glowing orb that always faces the camera. */
    static void disc(double cx, double cy, double cz, double r, float[] col, float a) {
        double dl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (dl < 1e-3) return;
        double dx = -cx / dl, dy = -cy / dl, dz = -cz / dl;
        double ux = -dz, uz = dx;
        double ul = Math.sqrt(ux * ux + uz * uz);
        if (ul < 1e-4) { ux = 1; uz = 0; ul = 1; }
        ux /= ul; uz /= ul;
        double vx = -uz * dy, vy = uz * dx - ux * dz, vz = ux * dy;
        V center = new V(cx, cy, cz, col, a);
        int n = 8;
        V[] mid = new V[n], out = new V[n];
        for (int k = 0; k < n; k++) {
            double ang = Math.PI * 2 * k / n, cs = Math.cos(ang), sn = Math.sin(ang);
            double ex = ux * cs + vx * sn, ey = vy * sn, ez = uz * cs + vz * sn;
            mid[k] = new V(cx + ex * r * 0.35, cy + ey * r * 0.35, cz + ez * r * 0.35, col, a * 0.45f);
            out[k] = new V(cx + ex * r, cy + ey * r, cz + ez * r, col, 0f);
        }
        for (int k = 0; k < n; k++) {
            int j = (k + 1) % n;
            quad(center, mid[k], mid[j], center);
            quad(mid[k], out[k], out[j], mid[j]);
        }
    }

    /** Catmull-Rom smoothing of control points (x,y,z). */
    static double[][] spline(double[][] c, int sub) {
        int n = c.length;
        double[][] out = new double[(n - 1) * sub + 1][3];
        int o = 0;
        for (int i = 0; i < n - 1; i++) {
            double[] p0 = c[Math.max(i - 1, 0)], p1 = c[i], p2 = c[i + 1], p3 = c[Math.min(i + 2, n - 1)];
            for (int s = 0; s < sub; s++) {
                double t = (double) s / sub, t2 = t * t, t3 = t2 * t;
                for (int k = 0; k < 3; k++) {
                    out[o][k] = 0.5 * ((2 * p1[k]) + (-p0[k] + p2[k]) * t
                            + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                            + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3);
                }
                o++;
            }
        }
        out[o] = c[n - 1].clone();
        return out;
    }
}
