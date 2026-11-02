package com.blackmenu;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Iterator;

/** Rain, wet glossy floor (sky reflection that gets stronger at grazing angles), ripples and splashes. */
final class Weather {
    static final int CR = 24, N = CR * 2 + 1;
    private static final float[] top = new float[N * N];
    private static final boolean[] floor = new boolean[N * N];
    private static int ox, oz;
    private static boolean valid;
    private static final ArrayList<double[]> ripples = new ArrayList<>();   // x, y, z, bornTick
    private static final ArrayList<double[]> splashes = new ArrayList<>();  // x, y, z, bornTick, seed
    private static final float[][] RAIN = {{0.6f, 0.72f, 1f}};
    private static final double[] AL = new double[4];
    private static final int[][] CS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
    private static final float[] DROP = {0.75f, 0.85f, 1f};

    private Weather() {}

    static boolean storm() {
        return Cfg.atmo && Atmos.theme().storm;
    }

    static boolean raining() {
        return (Cfg.wx && Cfg.wxMode != 1) || storm();
    }

    static boolean wet() {
        return Cfg.wx || storm();
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

    /** Every frame: makes the sky overcast and rainy (client side) so the vanilla rain and gloom appear. */
    static void frame(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null) return;
        if (Cfg.wx && Cfg.wxOvercast && Cfg.wxMode != 1) {
            w.setRainGradient(Cfg.wxMode == 2 ? 0.45f : 1f);
        } else if (storm()) {
            w.setRainGradient(1f);
            w.setThunderGradient(0.6f);
        }
    }

    static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null || mc.player == null || !(Cfg.wx || Cfg.atmo)) {
            valid = false;
            ripples.clear();
            splashes.clear();
            return;
        }
        long now = w.getTime();
        if (!valid || now % 10 == 0) rebuild(w, mc.player.getBlockX(), mc.player.getBlockZ());

        Iterator<double[]> it = ripples.iterator();
        while (it.hasNext()) if (now - it.next()[3] > 24) it.remove();
        Iterator<double[]> is = splashes.iterator();
        while (is.hasNext()) if (now - is.next()[3] > 8) is.remove();

        if (valid && wet() && raining()) {
            boolean rip = (Cfg.wx && Cfg.wxRipples) || storm();
            boolean spl = (Cfg.wx && Cfg.wxSplash) || storm();
            for (int i = 0; i < 8; i++) {
                double x = mc.player.getX() + (Math.random() - 0.5) * 28;
                double z = mc.player.getZ() + (Math.random() - 0.5) * 28;
                float f = floorAt(x, z);
                if (Float.isNaN(f)) continue;
                if (rip && ripples.size() < 70 && i % 2 == 0) ripples.add(new double[]{x, f + 0.015, z, now, 0});
                if (spl && splashes.size() < 70) splashes.add(new double[]{x, f + 0.02, z, now, Math.random() * 6.283});
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
        if (Cfg.wx && Cfg.wxMode != 1 && Cfg.wxDensity > 0.01) {
            boolean drizzle = Cfg.wxMode == 2;
            Falling.draw(Falling.Kind.RAIN, cam, t, Cfg.wxDensity * (drizzle ? 0.4 : 1.0), Cfg.wxSize,
                    drizzle ? 9 : 14, 0.6, Cfg.wxRadius, cam.y + Cfg.wxHeight / 2 - 4, Cfg.wxHeight, RAIN, 0.5f);
        }
        if (!valid || !wet()) return;
        drawSheen(w, cam, t, td);
        if (raining()) {
            if ((Cfg.wx && Cfg.wxRipples) || storm()) drawRipples(w, cam, td);
            if ((Cfg.wx && Cfg.wxSplash) || storm()) drawSplashes(w, cam, td);
        }
    }

    /** Sky reflection on every solid floor: faint underfoot, strong far away (grazing angle), plus puddle patches. */
    private static void drawSheen(ClientWorld w, Vec3d cam, double t, float td) {
        double rg = Math.max(w.getRainGradient(td), Cfg.wx && Cfg.wxMode == 1 ? 0.5 : 0);
        Vec3d sky = w.getSkyColor(cam, td);
        double lum = (sky.x + sky.y + sky.z) / 3.0;
        double m = 0.6 * Math.min(1, rg);
        float[] col = {
                (float) Math.min(1, sky.x * (1 - m) + lum * 1.15 * 0.92 * m + 0.03),
                (float) Math.min(1, sky.y * (1 - m) + lum * 1.15 * 0.97 * m + 0.03),
                (float) Math.min(1, sky.z * (1 - m) + lum * 1.15 * 1.06 * m + 0.04)};
        double shine = Cfg.wx ? Cfg.wxShine : 1.0;
        int rad = (int) (Math.min(CR - 1, Cfg.wx ? Cfg.wxRadius : 16) * Math.max(0.5f, Perf.q()));
        boolean patches = !Cfg.wx || Cfg.wxPuddles;
        int cx = (int) Math.floor(cam.x), cz = (int) Math.floor(cam.z);

        for (int x = cx - rad; x < cx + rad; x++) {
            for (int z = cz - rad; z < cz + rad; z++) {
                float f = floorAt(x + 0.5, z + 0.5);
                if (Float.isNaN(f)) continue;
                double y = f + 0.012;
                double ry = y - cam.y;
                double[] al = AL;
                int[][] cs = CS;
                double best = 0;
                for (int k = 0; k < 4; k++) {
                    double px = x + cs[k][0], pz = z + cs[k][1];
                    double dx = px - cam.x, dz = pz - cam.z;
                    double hd = Math.sqrt(dx * dx + dz * dz);
                    double d = Math.sqrt(hd * hd + ry * ry);
                    if (d < 0.01) d = 0.01;
                    double fres = 0.06 + Math.pow(1 - Math.abs(ry) / d, 2.5);
                    double wetness = patches ? 0.45 + 0.55 * puddle(px, pz) : 0.7;
                    double fade = Math.max(0, Math.min(1, (rad - hd) / (rad * 0.35)));
                    double sh = 0.88 + 0.12 * Math.sin(t * 1.3 + px * 0.6 + pz * 0.4);
                    al[k] = shine * 0.5 * fres * wetness * fade * sh;
                    best = Math.max(best, al[k]);
                }
                if (best < 0.01) continue;
                double x0 = x - cam.x, x1 = x + 1 - cam.x, z0 = z - cam.z, z1 = z + 1 - cam.z;
                Gfx.quad(new Gfx.V(x0, ry, z0, col, (float) al[0]), new Gfx.V(x1, ry, z0, col, (float) al[1]),
                        new Gfx.V(x1, ry, z1, col, (float) al[2]), new Gfx.V(x0, ry, z1, col, (float) al[3]));
            }
        }
    }

    private static void drawRipples(ClientWorld w, Vec3d cam, float td) {
        double now = w.getTime() + td;
        for (double[] r : ripples) {
            double u = (now - r[3]) / 24.0;
            if (u < 0 || u > 1) continue;
            double rad = 0.06 + 0.4 * u;
            float al = (float) ((1 - u) * 0.7);
            Gfx.Path p = new Gfx.Path();
            for (int i = 0; i < 10; i++) {
                double ang = Math.PI * 2 * i / 10;
                p.add(r[0] - cam.x + Math.cos(ang) * rad, r[1] - cam.y, r[2] - cam.z + Math.sin(ang) * rad, 1, al, DROP);
            }
            Gfx.ribbon(p, true, true, 0.05, 0.3f, 0f);
            Gfx.ribbon(p, true, true, 0.013, 1f, 0.5f);
        }
    }

    /** Tiny droplets bouncing up where the rain hits the ground. */
    private static void drawSplashes(ClientWorld w, Vec3d cam, float td) {
        double now = w.getTime() + td;
        for (double[] s : splashes) {
            double u = (now - s[3]) / 8.0;
            if (u < 0 || u > 1) continue;
            for (int k = 0; k < 4; k++) {
                double ang = s[4] + k * 1.5708;
                double dist = 0.12 * u * (0.7 + 0.3 * k / 3.0);
                double h = 0.2 * Math.sin(Math.PI * u) * (0.7 + 0.3 * (k % 2));
                Shapes.dot(s[0] - cam.x + Math.cos(ang) * dist, s[1] - cam.y + h, s[2] - cam.z + Math.sin(ang) * dist,
                        0.018, DROP, (float) ((1 - u) * 0.8));
            }
        }
    }
}
