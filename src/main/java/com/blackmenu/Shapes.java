package com.blackmenu;

/** Small soft shapes built from Gfx quads. Camera-relative coordinates. */
final class Shapes {
    private Shapes() {}

    /** Two axes perpendicular to the view direction (ux,uy,uz,vx,vy,vz), or null. */
    private static double[] axes(double cx, double cy, double cz) {
        double dl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (dl < 1e-3) return null;
        double dx = -cx / dl, dy = -cy / dl, dz = -cz / dl;
        double ux = -dz, uz = dx, ul = Math.sqrt(ux * ux + uz * uz);
        if (ul < 1e-4) { ux = 1; uz = 0; ul = 1; }
        ux /= ul; uz /= ul;
        return new double[]{ux, 0, uz, -uz * dy, uz * dx - ux * dz, ux * dy};
    }

    /** Cheap soft glowing dot that faces the camera. */
    static void dot(double cx, double cy, double cz, double r, float[] col, float a) {
        double[] ax = axes(cx, cy, cz);
        if (ax == null) return;
        int n = 6;
        Gfx.V c0 = new Gfx.V(cx, cy, cz, col, a);
        Gfx.V[] rim = new Gfx.V[n];
        for (int k = 0; k < n; k++) {
            double ang = Math.PI * 2 * k / n, cs = Math.cos(ang), sn = Math.sin(ang);
            rim[k] = new Gfx.V(cx + (ax[0] * cs + ax[3] * sn) * r, cy + (ax[4] * sn) * r,
                    cz + (ax[2] * cs + ax[5] * sn) * r, col, 0f);
        }
        for (int k = 0; k < n; k++) Gfx.quad(c0, rim[k], rim[(k + 1) % n], c0);
    }

    /** Soft glowing disc lying flat on the ground. */
    static void floorDisc(double cx, double cy, double cz, double r, float[] col, float a) {
        int n = 12;
        Gfx.V c0 = new Gfx.V(cx, cy, cz, col, a);
        Gfx.V[] mid = new Gfx.V[n], out = new Gfx.V[n];
        for (int k = 0; k < n; k++) {
            double ang = Math.PI * 2 * k / n, cs = Math.cos(ang), sn = Math.sin(ang);
            mid[k] = new Gfx.V(cx + cs * r * 0.55, cy, cz + sn * r * 0.55, col, a * 0.55f);
            out[k] = new Gfx.V(cx + cs * r, cy, cz + sn * r, col, 0f);
        }
        for (int k = 0; k < n; k++) {
            int j = (k + 1) % n;
            Gfx.quad(c0, mid[k], mid[j], c0);
            Gfx.quad(mid[k], out[k], out[j], mid[j]);
        }
    }

    private static void edge(Gfx.V p, Gfx.V q, float[] col, float a, double w) {
        Gfx.Path path = new Gfx.Path();
        path.add(p.x(), p.y(), p.z(), 1, a, col);
        path.add(q.x(), q.y(), q.z(), 1, a, col);
        Gfx.ribbon(path, false, false, w * 3, 0.25f, 0f);
        Gfx.ribbon(path, false, false, w, 1f, 0.6f);
    }

    /** Glowing octahedron crystal. rw = half width, hh = half height. */
    static void crystal(double cx, double cy, double cz, double rw, double hh, double rot, float[] col, float a) {
        Gfx.V top = new Gfx.V(cx, cy + hh, cz, col, a * 0.9f), bot = new Gfx.V(cx, cy - hh, cz, col, a * 0.9f);
        Gfx.V[] eq = new Gfx.V[4];
        for (int k = 0; k < 4; k++) {
            double ang = rot + k * Math.PI / 2;
            eq[k] = new Gfx.V(cx + Math.cos(ang) * rw, cy, cz + Math.sin(ang) * rw, col, a * 0.3f);
        }
        for (int k = 0; k < 4; k++) {
            Gfx.V e0 = eq[k], e1 = eq[(k + 1) % 4];
            Gfx.quad(top, e0, e1, e1);
            Gfx.quad(bot, e0, e1, e1);
            edge(top, e0, col, a, rw * 0.09);
            edge(bot, e0, col, a, rw * 0.09);
            edge(e0, e1, col, a, rw * 0.09);
        }
        dot(cx, cy, cz, hh * 1.6, col, a * 0.45f);
    }
}
