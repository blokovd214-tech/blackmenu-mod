package com.blackmenu;

import net.minecraft.util.math.Vec3d;

/** Atmosphere themes: time of day, aurora, falling particles, storm flashes. */
final class Atmos {
    static final class Theme {
        final long time;
        final int aur;                 // aurora color index, -1 = none
        final Falling.Kind kind;       // falling particles, null = none
        final boolean motes;           // floating fireflies
        final int motesColor;
        final float[][] pal;
        final double speed, dens;
        final boolean storm, sparkle;

        Theme(long time, int aur, Falling.Kind kind, boolean motes, int motesColor, float[][] pal,
              double speed, double dens, boolean storm, boolean sparkle) {
            this.time = time; this.aur = aur; this.kind = kind; this.motes = motes; this.motesColor = motesColor;
            this.pal = pal; this.speed = speed; this.dens = dens; this.storm = storm; this.sparkle = sparkle;
        }
    }

    private static final float[][] WHITE = {{0.9f, 0.95f, 1f}};
    private static final float[][] SNOW = {{0.92f, 0.96f, 1f}, {0.75f, 0.88f, 1f}};
    private static final float[][] AUTUMN = {{1f, 0.55f, 0.15f}, {0.95f, 0.3f, 0.1f}, {1f, 0.8f, 0.25f}, {0.7f, 0.35f, 0.12f}};
    private static final float[][] SPRING = {{1f, 0.6f, 0.8f}, {1f, 0.8f, 0.9f}, {1f, 0.45f, 0.65f}};
    private static final float[][] POLLEN = {{1f, 0.95f, 0.6f}, {1f, 1f, 0.85f}};
    private static final float[][] EMBER = {{1f, 0.45f, 0.1f}, {1f, 0.25f, 0.1f}, {1f, 0.7f, 0.2f}};
    private static final float[][] ASH = {{0.55f, 0.55f, 0.58f}, {0.4f, 0.38f, 0.4f}, {0.7f, 0.5f, 0.45f}};
    private static final float[][] RAIN = {{0.6f, 0.72f, 1f}};
    private static final float[] SPARK = {0.85f, 0.95f, 1f};

    /** Order must match Mods.ATMO names. */
    static final Theme[] THEMES = {
            new Theme(18000, 0, null, true, 0, WHITE, 0, 1, false, false),                       // Aurora
            new Theme(4000, 1, Falling.Kind.SNOW, false, 0, SNOW, 1.4, 1.2, false, true),         // Winter
            new Theme(12500, -1, Falling.Kind.LEAF, false, 0, AUTUMN, 1.0, 1.0, false, false),   // Autumn
            new Theme(6000, -1, Falling.Kind.POLLEN, false, 0, POLLEN, -0.25, 0.7, false, false), // Summer
            new Theme(2500, -1, Falling.Kind.PETAL, false, 0, SPRING, 0.8, 0.9, false, false),    // Spring
            new Theme(18500, -1, null, true, 5, WHITE, 0, 1, false, false),                       // Fireflies
            new Theme(17500, 4, Falling.Kind.EMBER, false, 0, EMBER, -1.2, 0.8, false, false),    // Crimson
            new Theme(14500, -1, Falling.Kind.RAIN, false, 0, RAIN, 16, 1.0, true, false),        // Storm
            new Theme(13500, -1, Falling.Kind.ASH, false, 0, ASH, 0.7, 1.0, false, false)         // Ash
    };

    private static final long[] TIME_TABLE = {0, 0, 18000, 12800, 23000, 6000};   // index = Cfg.atmoTime

    private Atmos() {}

    static Theme theme() {
        return THEMES[Math.floorMod(Cfg.atmoMode, THEMES.length)];
    }

    /** Time of day to force, or -1 to leave it to the server. */
    static long timeFor() {
        int t = Cfg.atmoTime;
        if (t == 0) return theme().time;
        if (t == 1 || t >= TIME_TABLE.length) return -1;
        return TIME_TABLE[t];
    }

    static void draw(Vec3d cam, double t) {
        Theme th = theme();
        if (th.kind != null) {
            boolean rain = th.kind == Falling.Kind.RAIN;
            Falling.draw(th.kind, cam, t, Cfg.atmoDensity * th.dens, Cfg.atmoSize, th.speed * Cfg.atmoSpeed,
                    Cfg.atmoWind, 16, cam.y + 4, rain ? 26 : 20, th.pal, rain ? 0.5f : 1f);
        }
        if (th.sparkle) sparkles(cam, t);
    }

    /** Glitter on snowy ground. */
    private static void sparkles(Vec3d cam, double t) {
        double period = 28;
        for (int i = 0; i < 60; i++) {
            double x = Falling.wrapNear(cam.x, Falling.hash(i * 5 + 1) * period, period);
            double z = Falling.wrapNear(cam.z, Falling.hash(i * 5 + 2) * period, period);
            float f = Weather.floorAt(x, z);
            if (Float.isNaN(f)) continue;
            double tw = Math.pow(Math.max(0, Math.sin(t * (1.5 + Falling.hash(i * 5 + 3) * 2) + i * 1.7)), 8);
            if (tw < 0.02) continue;
            Shapes.dot(x - cam.x, f + 0.05 - cam.y, z - cam.z, 0.06 * Cfg.atmoSize, SPARK, (float) tw);
        }
    }

    // ------------------------------------------------------------------ storm

    private static double nextFlash = 4, flashStart = -100, lastT;
    private static long boltSeed;

    static void updateStorm(double t) {
        lastT = t;
        if (t < flashStart - 1) { nextFlash = t + 4; flashStart = -100; }   // time went backwards (new world)
        if (t >= nextFlash) {
            flashStart = t;
            nextFlash = t + 6 + Math.random() * 10;
            boltSeed = (long) (Math.random() * 1.0e9);
        }
    }

    /** 0..1 white flash for the screen. */
    static float flash() {
        if (!Cfg.atmo || !theme().storm) return 0f;
        double dt = lastT - flashStart;
        if (dt < 0 || dt > 0.6) return 0f;
        return (float) (Math.exp(-dt * 7) * (0.6 + 0.4 * Math.sin(dt * 60)));
    }

    static void drawBolt(Vec3d cam, double t) {
        double dt = t - flashStart;
        if (dt < 0 || dt > 0.3) return;
        java.util.Random r = new java.util.Random(boltSeed);
        double ang = r.nextDouble() * Math.PI * 2, dist = 70 + r.nextDouble() * 50;
        double bx = Math.cos(ang) * dist, bz = Math.sin(ang) * dist;
        int k = 9;
        double[][] ctl = new double[k][3];
        for (int i = 0; i < k; i++) {
            double s = i / (double) (k - 1);
            double jag = (i == 0 || i == k - 1) ? 0 : 7 * (1 - s) + 1;
            ctl[i][0] = bx + (r.nextDouble() - 0.5) * 2 * jag;
            ctl[i][1] = 140 - s * 150;
            ctl[i][2] = bz + (r.nextDouble() - 0.5) * 2 * jag;
        }
        double[][] pts = Gfx.spline(ctl, 4);
        float al = (float) Math.exp(-dt * 10);
        float[] col = {0.75f, 0.85f, 1f};
        Gfx.Path p = new Gfx.Path();
        for (double[] q : pts) p.add(q[0], q[1], q[2], 1, al, col);
        Gfx.ribbon(p, false, false, 1.6, 0.3f, 0f);
        Gfx.ribbon(p, false, false, 0.3, 1f, 0.7f);
    }
}
