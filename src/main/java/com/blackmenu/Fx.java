package com.blackmenu;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;

/** World rendering: aurora, atmosphere particles, weather, dust, kill effects, hitboxes and target effects. */
public final class Fx {
    static final Motes FLIES = new Motes(false);
    static final Motes DUST = new Motes(true);

    private static VertexConsumerProvider.Immediate imm;

    private Fx() {}

    /** Called every tick AND every frame, so the sky never flickers back to server time. */
    static void applyTime(MinecraftClient mc) {
        if (!Cfg.atmo || mc.world == null) return;
        long t = Atmos.timeFor();
        if (t >= 0) mc.world.setTimeOfDay(t);
    }

    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld w = mc.world;
        MatrixStack ms = ctx.matrixStack();
        if (w == null || mc.player == null || ms == null) return;

        Atmos.Theme th = Atmos.theme();
        boolean at = Cfg.atmo;
        boolean doAur = at && Cfg.aurora && th.aur >= 0;
        boolean doAtmos = at && (th.kind != null || th.sparkle);
        boolean doMotes = at && th.motes;
        boolean doWx = Cfg.wx || (at && th.storm);
        boolean doDust = Cfg.dust, doHb = Cfg.hb, doKill = KillFx.active();
        Entity target = TargetEsp.pick(mc);
        if (!(doAur || doAtmos || doMotes || doWx || doDust || doHb || doKill) && target == null) return;

        if (imm == null) imm = VertexConsumerProvider.immediate(new BufferAllocator(1 << 20));

        float td = mc.getRenderTickCounter().getTickDelta(false);
        Vec3d cam = ctx.camera().getPos();
        double t = (w.getTime() + td) / 20.0;
        if (at && th.storm) Atmos.updateStorm(t);

        Gfx.m = ms.peek().getPositionMatrix();
        Gfx.vc = imm.getBuffer(RenderLayer.getLightning());

        if (doAur) Aurora.draw(w, t, td, th.aur);
        if (at && th.storm) Atmos.drawBolt(cam, t);
        if (doAtmos) Atmos.draw(cam, t);
        if (doMotes) FLIES.draw(cam, td);
        if (doWx) Weather.draw(mc, w, cam, t, td);
        if (doDust) DUST.draw(cam, td);
        if (doKill) KillFx.draw(cam, td);
        if (doHb) Hitboxes.draw(mc, w, cam, t, td);
        if (target != null) TargetEsp.draw(target, cam, t, td);

        // glow must not be eaten by distance fog
        float fs = RenderSystem.getShaderFogStart(), fe = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1.0E8f);
        RenderSystem.setShaderFogEnd(2.0E8f);
        imm.draw();
        RenderSystem.setShaderFogStart(fs);
        RenderSystem.setShaderFogEnd(fe);
    }

    // ------------------------------------------------------------------ aurora

    static final class Aurora {
        private static double smooth(double a, double b, double x) {
            double k = Math.max(0, Math.min(1, (x - a) / (b - a)));
            return k * k * (3 - 2 * k);
        }

        private static float[] color(int idx, double v, double s, double t) {
            float[] a, b;
            switch (idx) {
                case 0 -> { a = new float[]{0.25f, 1f, 0.55f}; b = new float[]{0.55f, 0.35f, 1f}; }
                case 1 -> { a = new float[]{0.25f, 0.9f, 1f}; b = new float[]{0.45f, 0.3f, 1f}; }
                case 2 -> { a = new float[]{0.85f, 0.35f, 1f}; b = new float[]{1f, 0.45f, 0.65f}; }
                case 4 -> { a = new float[]{1f, 0.2f, 0.25f}; b = new float[]{0.6f, 0.1f, 0.5f}; }
                default -> { return Gfx.hsv(0.3 + 0.35 * v + s * 0.2 + t * 0.01, 0.7, 1.0); }
            }
            float k = (float) Math.pow(v, 0.8);
            return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
        }

        static void draw(ClientWorld w, double t, float td, int colorIdx) {
            if (!w.getRegistryKey().equals(World.OVERWORLD)) return;
            double sun = Math.cos(w.getSkyAngle(td) * Math.PI * 2);
            double night = Math.max(0, Math.min(1, (0.30 - sun) / 0.55));
            night *= 1.0 - w.getRainGradient(td);
            if (night < 0.01) return;

            double spd = Cfg.atmoSpeed;
            int n = 40, m = 7;
            for (int k = 2; k >= 0; k--) {   // far to near
                double rho = 240 + 45 * k;
                double center = 0.9 + k * 2.1 + Math.sin(t * 0.05 * spd + k) * 0.15;
                double span = 1.7;
                Gfx.V[][] g = new Gfx.V[n][m];
                for (int i = 0; i < n; i++) {
                    double s = (double) i / (n - 1);
                    double th = center - span / 2 + s * span;
                    double r = rho + 28 * Math.sin(s * 6 + t * 0.35 * spd + k * 1.7)
                            + 12 * Math.sin(s * 13 - t * 0.5 * spd);
                    double y0 = 85 + 22 * Math.sin(s * 4 + t * 0.25 * spd + k);
                    double hh = 110 + 45 * Math.sin(s * 3 - t * 0.2 * spd + k * 2);
                    double x = Math.cos(th) * r, z = Math.sin(th) * r;
                    double rays = 0.55 + 0.45 * Math.sin(s * 17 + t * 0.9 * spd + k * 3);
                    double taper = Math.pow(Math.sin(Math.PI * s), 0.7);
                    double breathe = 0.8 + 0.2 * Math.sin(t * 0.3 * spd + k);
                    double base = 0.7 * Cfg.auroraGlow * night * rays * taper * breathe;
                    for (int j = 0; j < m; j++) {
                        double v = (double) j / (m - 1);
                        double prof = smooth(0, 0.14, v) * Math.pow(1 - v, 1.6);
                        g[i][j] = new Gfx.V(x, y0 + hh * v, z, color(colorIdx, v, s, t), (float) (base * prof));
                    }
                }
                for (int i = 0; i < n - 1; i++) {
                    for (int j = 0; j < m - 1; j++) {
                        Gfx.quad(g[i][j], g[i + 1][j], g[i + 1][j + 1], g[i][j + 1]);
                    }
                }
            }
        }
    }

    // ----------------------------------------------------- floating particles / cave dust

    static final class Motes {
        private static final class F {
            double x, y, z, px, py, pz, vx, vy, vz, seed, sz, d, ix, iy, iz;
            float light;
            int age, life;
        }

        private final boolean dust;
        private final ArrayList<F> list = new ArrayList<>();

        Motes(boolean dust) {
            this.dust = dust;
        }

        private boolean enabled() {
            return dust ? Cfg.dust : (Cfg.atmo && Atmos.theme().motes);
        }

        void tick(MinecraftClient mc) {
            if (!enabled() || mc.world == null || mc.player == null) {
                list.clear();
                return;
            }
            ClientWorld w = mc.world;
            BlockPos pp = mc.player.getBlockPos();
            boolean canSpawn = !dust || w.getLightLevel(LightType.SKY, pp) <= 4;   // dust only in caves / indoors
            int target = (int) (dust ? 130 * Cfg.dustAmount : 110 * Cfg.atmoDensity);
            double sp = dust ? Cfg.dustSpeed : Cfg.atmoSpeed;

            for (int i = 0; canSpawn && i < 3 && list.size() < target; i++) {
                double ang = Math.random() * Math.PI * 2;
                double rr = dust ? 1 + Math.random() * 9 : 3 + Math.random() * 20;
                double x = mc.player.getX() + Math.cos(ang) * rr;
                double y = mc.player.getY() + (dust ? -3 + Math.random() * 7 : -1 + Math.random() * 8);
                double z = mc.player.getZ() + Math.sin(ang) * rr;
                if (dust && !w.getBlockState(new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))).isAir()) {
                    continue;
                }
                F f = new F();
                f.x = f.px = x; f.y = f.py = y; f.z = f.pz = z;
                f.seed = Math.random() * Math.PI * 2;
                f.sz = 0.7 + Math.random() * 0.6;
                f.life = (dust ? 150 : 200) + (int) (Math.random() * 200);
                list.add(f);
            }

            double maxD = dust ? 14 : 45;
            Iterator<F> it = list.iterator();
            while (it.hasNext()) {
                F f = it.next();
                f.age++;
                f.px = f.x; f.py = f.y; f.pz = f.z;
                double tt = f.age * 0.04 * sp + f.seed;
                if (dust) {
                    f.vx = f.vx * 0.97 + Math.sin(tt * 1.7) * 0.0004 * sp;
                    f.vy = f.vy * 0.97 + Math.sin(tt * 1.1 + 2) * 0.00025 * sp;
                    f.vz = f.vz * 0.97 + Math.cos(tt * 1.3 + 1) * 0.0004 * sp;
                    BlockPos bp = new BlockPos((int) Math.floor(f.x), (int) Math.floor(f.y), (int) Math.floor(f.z));
                    float lt = w.getLightLevel(LightType.BLOCK, bp) / 15f;
                    f.light += (lt - f.light) * 0.15f;
                } else {
                    f.vx = f.vx * 0.96 + Math.sin(tt * 1.3) * 0.0016 * sp;
                    f.vy = f.vy * 0.96 + (Math.sin(tt * 0.9 + 2) * 0.0010 + 0.0003) * sp;
                    f.vz = f.vz * 0.96 + Math.cos(tt * 1.1 + 1) * 0.0016 * sp;
                }
                f.x += f.vx; f.y += f.vy; f.z += f.vz;
                double dx = f.x - mc.player.getX(), dz = f.z - mc.player.getZ();
                if (f.age >= f.life || dx * dx + dz * dz > maxD * maxD) it.remove();
            }
        }

        void draw(Vec3d cam, float td) {
            if (list.isEmpty()) return;
            for (F f : list) {
                f.ix = f.px + (f.x - f.px) * td;
                f.iy = f.py + (f.y - f.py) * td;
                f.iz = f.pz + (f.z - f.pz) * td;
                double dx = f.ix - cam.x, dy = f.iy - cam.y, dz = f.iz - cam.z;
                f.d = dx * dx + dy * dy + dz * dz;
            }
            list.sort((a, b) -> Double.compare(b.d, a.d));   // far to near
            for (F f : list) {
                double fade = Math.min(1.0, Math.min(f.age / 30.0, (f.life - f.age) / 30.0));
                float al;
                double rad;
                float[] col;
                if (dust) {
                    al = (float) (fade * (0.03 + 0.85 * f.light * f.light));
                    rad = 0.045 * Cfg.dustSize * f.sz;
                    col = Gfx.pal(Cfg.dustColor, f.seed / (Math.PI * 2));
                } else {
                    double twinkle = 0.75 + 0.25 * Math.sin(f.age * 0.09 + f.seed);
                    al = (float) (fade * twinkle * 0.9);
                    rad = 0.16 * Cfg.atmoSize * f.sz;
                    col = Gfx.pal(Atmos.theme().motesColor, f.seed / (Math.PI * 2));
                }
                Gfx.disc(f.ix - cam.x, f.iy - cam.y, f.iz - cam.z, rad, col, al);
            }
        }
    }
}
