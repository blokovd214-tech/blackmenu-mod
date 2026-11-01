package com.blackmenu;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Iterator;

/** Rain, wet floor (puddles with a soft sheen) and ripples. Also keeps a small ground height cache. */
final class Weather {
    static final int CR = 24, N = CR * 2 + 1;
    private static final float[] top = new float[N * N];
    private static final boolean[] floor = new boolean[N * N];
    private static int ox, oz;
    private static boolean valid;
    private static final ArrayList<double[]> ripples = new ArrayList<>();   // x, y, z, bornTick
    private static final float[][] RAIN = {{0.6f, 0.72f, 1f}};

    private Weather() {}

    static boolean raining() {
        return (Cfg.wx && Cfg.wxMode != 1) || (Cfg.atmo && Atmos.theme().storm);
    }

    /** Highest motion blocking Y of the column (roof or ground), NaN if unknown. */
    static float topAt(double x, double z) {
        if (!valid) return Float.NaN;
        int i = (int) Math.floor(x) - ox, j = (int) Math.floor(z) - oz;
        if (i < 0 || j < 0 || i >= N || j >= N) return Float.NaN;
        return top[i * N + j];
    }

    /** Y of the open solid floor of this column, NaN if it is not a plain solid floor. */
    static float floorAt(double x, double z) {
        if (!valid) return Float.NaN;
        int i = (int) Math.floor(x) - ox, j = (int) Math.floor(z) - oz;
        if (i < 0 || j < 0 || i >= N || j >= N) return Float.NaN;
        return floor[i * N + j] ? top[i * N + j] : Float.NaN;
    }

    static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null || mc.player == null || !(Cfg.wx || Cfg.atmo)) {
            valid = false;
            ripples.clear();
            return;
        }
        long now = w.getTime();
        if (!valid || now % 10 == 0) rebuild(w, mc.player.getBlockX(), mc.player.getBlockZ());

        Iterator<double[]> it = ripples.iterator();
        while (it.hasNext()) if (now - it.next()[3] > 24) it.remove();

        boolean puddles = (Cfg.wx && Cfg.wxPuddles) || (Cfg.atmo && Atmos.theme().storm);
        boolean ripplesOn = (Cfg.wx && Cfg.wxRipples) || (Cfg.atmo && Atmos.theme().storm);
        if (valid && puddles && ripplesOn && raining() && ripples.size() < 120) {
            for (int tries = 0; tries < 6; tries++) {
                double x = mc.player.getX() + (Math.random() - 0.5) * 24;
                double z = mc.player.getZ() + (Math.random() - 0.5) * 24;
                float f = floorAt(x, z);
                if (!Float.isNaN(f) && puddle(Math.floor(x), Math.floor(z)) > 0.35) {
                    ripples.add(new double[]{x, f + 0.015, z, now});
                    if (ripples.size() % 3 == 0) break;
                }
            }
        }
    }

    private static void rebuild(ClientWorld w, int px, int pz) {
        ox = px - CR;
        oz = pz - CR;
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                int idx = i * N + j;
                int x = ox + i, z = oz + j;
                int tp = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                if (tp <= w.getBottomY() + 1) {
                    top[idx] = Float.NaN;
                    floor[idx] = false;
                    continue;
                }
                top[idx] = tp;
                BlockState st = w.getBlockState(new BlockPos(x, tp - 1, z));
                floor[idx] = st.isOpaque() && st.getFluidState().isEmpty();
            }
        }
        valid = true;
    }

    // ------------------------------------------------------------------ noise

    private static double vnoise(double x, double z) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double fx = x - xi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = Falling.hash(xi * 57 + zi * 131), b = Falling.hash((xi + 1) * 57 + zi * 131);
        double c = Falling.hash(xi * 57 + (zi + 1) * 131), d = Falling.hash((xi + 1) * 57 + (zi + 1) * 131);
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }

    /** 0..1 puddle amount at a block corner, continuous across tiles so there are no square edges. */
    static double puddle(double x, double z) {
        double n = vnoise(x * 0.21, z * 0.21) * 0.7 + vnoise(x * 0.53 + 17, z * 0.53 + 3) * 0.3;
        double k = Math.max(0, Math.min(1, (n - 0.50) / 0.18));
        return k * k * (3 - 2 * k);
    }

    // ------------------------------------------------------------------ drawing

    static void draw(MinecraftClient mc, ClientWorld w, Vec3d cam, double t, float td) {
        boolean storm = Cfg.atmo && Atmos.theme().storm;
        if (Cfg.wx && Cfg.wxMode != 1) {
            boolean drizzle = Cfg.wxMode == 2;
            Falling.draw(Falling.Kind.RAIN, cam, t, Cfg.wxDensity * (drizzle ? 0.4 : 1.0), Cfg.wxSize,
                    drizzle ? 9 : 14, 0.6, Cfg.wxRadius, cam.y + Cfg.wxHeight / 2 - 4, Cfg.wxHeight, RAIN, 0.5f);
        }
        boolean puddles = (Cfg.wx && Cfg.wxPuddles) || storm;
        if (!puddles || !valid) return;
        drawPuddles(w, cam, t, td, Cfg.wx ? Cfg.wxShine : 1.0);
        if (raining() && ((Cfg.wx && Cfg.wxRipples) || storm)) drawRipples(w, cam, td);
    }

    private static void drawPuddles(ClientWorld w, Vec3d cam, double t, float td, double shine) {
        float dayF = (float) Math.max(0, Math.min(1, 0.5 + 0.5 * Math.cos(w.getSkyAngle(td) * Math.PI * 2)));
        float[] col = {0.12f + 0.33f * dayF, 0.18f + 0.37f * dayF, 0.35f + 0.4f * dayF};
        int rad = (int) Math.min(CR - 1, Cfg.wx ? Cfg.wxRadius : 16);
        int cx = (int) Math.floor(cam.x), cz = (int) Math.floor(cam.z);
        float k = (float) (0.34 * shine);
        for (int x = cx - rad; x < cx + rad; x++) {
            for (int z = cz - rad; z < cz + rad; z++) {
                float f = floorAt(x + 0.5, z + 0.5);
                if (Float.isNaN(f)) continue;
                double a00 = puddle(x, z), a10 = puddle(x + 1, z), a11 = puddle(x + 1, z + 1), a01 = puddle(x, z + 1);
                if (Math.max(Math.max(a00, a10), Math.max(a11, a01)) < 0.02) continue;
                double y = f + 0.012 - cam.y;
                double sh = 0.8 + 0.2 * Math.sin(t * 1.2 + x * 0.7 + z * 0.5);
                double x0 = x - cam.x, x1 = x + 1 - cam.x, z0 = z - cam.z, z1 = z + 1 - cam.z;
                Gfx.quad(new Gfx.V(x0, y, z0, col, (float) (a00 * k * sh)),
                        new Gfx.V(x1, y, z0, col, (float) (a10 * k * sh)),
                        new Gfx.V(x1, y, z1, col, (float) (a11 * k * sh)),
                        new Gfx.V(x0, y, z1, col, (float) (a01 * k * sh)));
            }
        }
    }

    private static void drawRipples(ClientWorld w, Vec3d cam, float td) {
        double now = w.getTime() + td;
        float[] col = {0.7f, 0.85f, 1f};
        for (double[] r : ripples) {
            double u = (now - r[3]) / 24.0;
            if (u < 0 || u > 1) continue;
            double rad = 0.06 + 0.4 * u;
            float al = (float) ((1 - u) * 0.7);
            Gfx.Path p = new Gfx.Path();
            for (int i = 0; i < 14; i++) {
                double ang = Math.PI * 2 * i / 14;
                p.add(r[0] - cam.x + Math.cos(ang) * rad, r[1] - cam.y, r[2] - cam.z + Math.sin(ang) * rad, 1, al, col);
            }
            Gfx.ribbon(p, true, true, 0.06, 0.3f, 0f);
            Gfx.ribbon(p, true, true, 0.015, 1f, 0.5f);
        }
    }
}
