package com.blackmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;

/** Effect when something you were aiming at dies: blood, lightning or souls. */
final class KillFx {
    private static final class Watch { LivingEntity e; long seen; double x, y, z, w, h; }
    private static final class P {
        double x, y, z, px, py, pz, vx, vy, vz, size, seed, ground;
        int age, life, kind;   // 0 drop, 1 splat, 2 mist, 3 soul
    }
    private static final class Ring { double x, y, z, max; int age, life; float[] col; }
    private static final class Bolt { double x, y, z; int age; long seed; }

    private static final ArrayList<Watch> watch = new ArrayList<>();
    private static final ArrayList<P> parts = new ArrayList<>();
    private static final ArrayList<Ring> rings = new ArrayList<>();
    private static final ArrayList<Bolt> bolts = new ArrayList<>();

    private static final float[] BLOOD = {0.75f, 0.02f, 0.04f};
    private static final float[] DARK = {0.5f, 0.0f, 0.02f};
    private static final float[] SOUL = {0.55f, 0.85f, 1f};
    private static final float[] BOLT = {0.75f, 0.85f, 1f};

    private KillFx() {}

    static boolean active() {
        return !parts.isEmpty() || !rings.isEmpty() || !bolts.isEmpty();
    }

    static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null) {
            watch.clear(); parts.clear(); rings.clear(); bolts.clear();
            return;
        }
        long now = w.getTime();

        if (Cfg.kfx) {
            Entity t = mc.targetedEntity;
            if (t instanceof LivingEntity le && !(le instanceof ArmorStandEntity)) {
                Watch found = null;
                for (Watch k : watch) if (k.e == le) { found = k; break; }
                if (found == null) { found = new Watch(); found.e = le; watch.add(found); }
                found.seen = now;
            }
            Iterator<Watch> it = watch.iterator();
            while (it.hasNext()) {
                Watch k = it.next();
                boolean alive = !k.e.isRemoved() && !k.e.isDead() && k.e.getHealth() > 0;
                if (alive) {
                    k.x = k.e.getX(); k.y = k.e.getY(); k.z = k.e.getZ();
                    k.w = k.e.getWidth(); k.h = k.e.getHeight();
                    if (now - k.seen > 100) it.remove();
                } else {
                    if (now - k.seen < 100) fire(k);
                    it.remove();
                }
            }
            while (watch.size() > 6) watch.remove(0);
        } else {
            watch.clear();
        }

        Iterator<P> pi = parts.iterator();
        while (pi.hasNext()) {
            P p = pi.next();
            p.age++;
            if (p.age >= p.life) { pi.remove(); continue; }
            p.px = p.x; p.py = p.y; p.pz = p.z;
            switch (p.kind) {
                case 0 -> {
                    p.vy -= 0.045; p.vx *= 0.98; p.vz *= 0.98;
                    p.x += p.vx; p.y += p.vy; p.z += p.vz;
                    if (p.y <= p.ground + 0.02) {
                        if (Math.random() < 0.5) {
                            p.kind = 1; p.y = p.ground + 0.015; p.py = p.y; p.px = p.x; p.pz = p.z;
                            p.vx = p.vy = p.vz = 0; p.age = 0; p.life = 100;
                            p.size = (0.06 + Math.random() * 0.14) * Cfg.kfxSize;
                        } else {
                            pi.remove();
                        }
                    }
                }
                case 2 -> {
                    p.x += p.vx; p.y += p.vy; p.z += p.vz;
                    p.vx *= 0.96; p.vy *= 0.97; p.vz *= 0.96;
                }
                case 3 -> {
                    p.x += Math.sin(p.age * 0.25 + p.seed) * 0.03;
                    p.z += Math.cos(p.age * 0.25 + p.seed) * 0.03;
                    p.y += 0.05;
                }
                default -> { }
            }
        }

        Iterator<Ring> ri = rings.iterator();
        while (ri.hasNext()) {
            Ring rg = ri.next();
            if (++rg.age >= rg.life) ri.remove();
        }
        Iterator<Bolt> bi = bolts.iterator();
        while (bi.hasNext()) {
            Bolt bt = bi.next();
            if (++bt.age > 14) bi.remove();
        }
    }

    private static P particle(int kind, double x, double y, double z, double vx, double vy, double vz,
                              double size, int life, double ground) {
        P p = new P();
        p.kind = kind; p.x = p.px = x; p.y = p.py = y; p.z = p.pz = z;
        p.vx = vx; p.vy = vy; p.vz = vz; p.size = size; p.life = life; p.ground = ground;
        p.seed = Math.random() * 6.283;
        return p;
    }

    private static void ring(double x, double y, double z, double max, int life, float[] col) {
        Ring r = new Ring();
        r.x = x; r.y = y; r.z = z; r.max = max; r.life = life; r.col = col;
        rings.add(r);
    }

    private static void fire(Watch k) {
        double pw = Cfg.kfxPower, sz = Cfg.kfxSize;
        switch (Cfg.kfxMode) {
            case 0 -> {   // blood
                int n = (int) (36 * pw);
                for (int i = 0; i < n && parts.size() < 300; i++) {
                    double ang = Math.random() * Math.PI * 2, sp = 0.08 + Math.random() * 0.22;
                    parts.add(particle(0, k.x, k.y + k.h * (0.3 + Math.random() * 0.6), k.z,
                            Math.cos(ang) * sp, 0.12 + Math.random() * 0.2, Math.sin(ang) * sp,
                            sz, 30 + (int) (Math.random() * 30), k.y));
                }
                for (int i = 0; i < 6; i++) {
                    double ang = Math.random() * Math.PI * 2;
                    parts.add(particle(2, k.x, k.y + k.h * 0.6, k.z, Math.cos(ang) * 0.02, 0.02, Math.sin(ang) * 0.02,
                            (0.5 + Math.random() * 0.5) * sz, 40, k.y));
                }
                ring(k.x, k.y + 0.02, k.z, 1.3 * sz, 16, BLOOD);
            }
            case 1 -> {   // lightning
                Bolt b = new Bolt();
                b.x = k.x; b.y = k.y; b.z = k.z; b.seed = (long) (Math.random() * 1.0e9);
                bolts.add(b);
                ring(k.x, k.y + 0.02, k.z, 2.0 * sz, 14, BOLT);
            }
            default -> {  // souls
                int n = (int) (6 * pw);
                for (int i = 0; i < n && parts.size() < 300; i++) {
                    double ang = Math.random() * Math.PI * 2;
                    parts.add(particle(3, k.x + Math.cos(ang) * 0.3, k.y + k.h * 0.4, k.z + Math.sin(ang) * 0.3,
                            0, 0, 0, sz, 50, k.y));
                }
            }
        }
    }

    static void draw(Vec3d cam, float td) {
        for (P p : parts) {
            double ix = p.px + (p.x - p.px) * td, iy = p.py + (p.y - p.py) * td, iz = p.pz + (p.z - p.pz) * td;
            double rx = ix - cam.x, ry = iy - cam.y, rz = iz - cam.z;
            double fade = Math.min(1.0, (p.life - p.age) / 14.0);
            switch (p.kind) {
                case 0 -> {
                    Gfx.Path s = new Gfx.Path();
                    s.add(rx, ry, rz, 1, 0.9 * fade, BLOOD);
                    s.add(rx - p.vx * 2.5, ry - p.vy * 2.5, rz - p.vz * 2.5, 0.3, 0.0, BLOOD);
                    Gfx.ribbon(s, false, false, 0.03 * p.size, 1f, 0.15f);
                    Shapes.dot(rx, ry, rz, 0.05 * p.size, BLOOD, (float) (0.7 * fade));
                }
                case 1 -> Shapes.floorDisc(rx, ry - 0.0, rz, p.size, DARK, (float) (0.55 * fade));
                case 2 -> Gfx.disc(rx, ry, rz, 0.7 * p.size, BLOOD, (float) (0.10 * fade));
                case 3 -> {
                    Gfx.Path tr = new Gfx.Path();
                    for (int k = 0; k < 10; k++) {
                        double f = 1 - k / 9.0;
                        tr.add(rx + Math.sin((p.age - k) * 0.25 + p.seed) * 0.03 * k, ry - k * 0.08, rz, f, 0.6 * f * fade, SOUL);
                    }
                    Gfx.ribbon(tr, false, false, 0.08 * p.size, 0.3f, 0f);
                    Gfx.ribbon(tr, false, false, 0.02 * p.size, 1f, 0.5f);
                    Shapes.dot(rx, ry, rz, 0.12 * p.size, SOUL, (float) (0.9 * fade));
                }
                default -> { }
            }
        }

        for (Ring r : rings) {
            double u = (r.age + td) / r.life;
            if (u > 1) continue;
            double rad = 0.1 + r.max * u;
            Gfx.Path path = new Gfx.Path();
            for (int i = 0; i < 28; i++) {
                double ang = Math.PI * 2 * i / 28;
                path.add(r.x - cam.x + Math.cos(ang) * rad, r.y - cam.y, r.z - cam.z + Math.sin(ang) * rad,
                        1, (1 - u) * 0.9, r.col);
            }
            Gfx.ribbon(path, true, true, 0.12, 0.3f, 0f);
            Gfx.ribbon(path, true, true, 0.03, 1f, 0.4f);
        }

        for (Bolt b : bolts) {
            java.util.Random rnd = new java.util.Random(b.seed);
            int k = 9;
            double[][] ctl = new double[k][3];
            for (int i = 0; i < k; i++) {
                double s = i / (double) (k - 1);
                double jag = (i == 0 || i == k - 1) ? 0 : 1.2 * (1 - s) + 0.1;
                ctl[i][0] = b.x - cam.x + (rnd.nextDouble() - 0.5) * 2 * jag;
                ctl[i][1] = b.y + 50 - s * 50 - cam.y;
                ctl[i][2] = b.z - cam.z + (rnd.nextDouble() - 0.5) * 2 * jag;
            }
            double[][] pts = Gfx.spline(ctl, 4);
            float al = (float) ((b.age % 4 < 2 ? 1.0 : 0.5) * (1 - b.age / 15.0));
            Gfx.Path path = new Gfx.Path();
            for (double[] q : pts) path.add(q[0], q[1], q[2], 1, al, BOLT);
            Gfx.ribbon(path, false, false, 0.5, 0.3f, 0f);
            Gfx.ribbon(path, false, false, 0.07, 1f, 0.7f);
            Shapes.floorDisc(b.x - cam.x, b.y + 0.03 - cam.y, b.z - cam.z, 2.0, BOLT, al * 0.6f);
        }
    }
}
