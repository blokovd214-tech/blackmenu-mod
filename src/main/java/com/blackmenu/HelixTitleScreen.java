package com.blackmenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Main menu: a glowing double helix of light particles over a starfield, with pill buttons. */
public class HelixTitleScreen extends Screen {
    private static final String[] LABELS = {"Singleplayer|Одиночная игра", "Multiplayer|Сетевая игра",
            "Options|Настройки", "Quit|Выход"};

    private final long opened = System.currentTimeMillis();
    private long last = opened;
    private final int[][] rects = new int[4][4];
    private final float[] hover = new float[4];

    public HelixTitleScreen() {
        super(Text.literal("Black Menu"));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private static double hash(int n) {
        return Falling.hash(n);
    }

    private static double ease(double x) {
        return 1 - Math.pow(1 - x, 3);
    }

    /** Soft glowing dot made of concentric discs (pixel space). */
    private static void glow(DrawContext c, double x, double y, double r, int rgb, float a, boolean wide) {
        if (a < 0.02f) return;
        int cx = (int) Math.round(x), cy = (int) Math.round(y);
        int mid = Math.max(2, (int) Math.ceil(r * 1.4));
        int core = Math.max(1, (int) Math.round(r * 0.55));
        if (wide) {
            int big = (int) Math.ceil(r * 2.4);
            Ui.rrect(c, cx - big, cy - big, big * 2, big * 2, big, Ui.argb(rgb, a * 0.09f));
        }
        Ui.rrect(c, cx - mid, cy - mid, mid * 2, mid * 2, mid, Ui.argb(rgb, a * 0.22f));
        Ui.rrect(c, cx - core, cy - core, core * 2, core * 2, core, Ui.argb(rgb, a * 0.95f));
    }

    private void stars(DrawContext c, double pw, double ph, double T, double tOpen, double ox, double oy) {
        double u = Math.max(0.6, ph / 1080.0);
        int px = Math.max(1, (int) Math.round(u));
        for (int i = 0; i < 160; i++) {
            double x = hash(i * 3 + 1) * pw + ox * 0.4, y = hash(i * 3 + 2) * ph + oy * 0.4;
            double tw = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(T * (0.6 + hash(i * 3 + 3) * 1.5) + i));
            int s = (hash(i + 900) > 0.85 ? 2 : 1) * px;
            c.fill((int) x, (int) y, (int) x + s, (int) y + s, Ui.argb(0xCFE8FF, (float) (tw * 0.8)));
        }
        float fade = (float) Math.min(1, tOpen);
        for (int i = 0; i < 26; i++) {
            double x = hash(i * 5 + 41) * pw + ox * 1.2;
            double y = ((hash(i * 5 + 42) * ph - T * (6 + hash(i * 5 + 43) * 10) * u) % ph + ph) % ph + oy * 1.2;
            double r = (3 + hash(i * 5 + 44) * 6) * u;
            glow(c, x, y, r, 0xBFE6FF, (float) (0.25 + 0.2 * hash(i * 5 + 45)) * fade, true);
        }
    }

    private void helix(DrawContext c, double pw, double ph, double T, double tOpen, double ox, double oy) {
        double u = Math.max(0.6, ph / 1080.0);
        double e = ease(Math.min(1, tOpen / 2.2));          // the particles assemble into the helix
        double cx = pw * 0.5 + ox * 2.0, cy = ph * 0.46 + oy * 2.0;
        double tilt = -0.42 + Math.sin(T * 0.15) * 0.04;
        double ax = Math.sin(tilt), ay = Math.cos(tilt);    // axis
        double nx = ay, ny = -ax;                           // normal
        double len = ph * 1.7, radius = ph * 0.17 * (1 + 0.05 * Math.sin(T * 0.7));
        double turns = 2.6;
        int n = (int) Math.max(30, 90 * Perf.q());

        for (int pass = 0; pass < 2; pass++) {
            for (int s = 0; s < 2; s++) {
                for (int i = 0; i < n; i++) {
                    double uu = i / (double) (n - 1) - 0.5;
                    double phase = uu * turns * Math.PI * 2 + T * 0.45 + s * Math.PI;
                    double z = Math.cos(phase);
                    if ((pass == 0) != (z < 0)) continue;
                    double lat = Math.sin(phase) * radius;
                    int key = s * 1000 + i;
                    double sx0 = (1 - e) * (hash(key * 3 + 1) - 0.5) * pw * 0.9;
                    double sy0 = (1 - e) * (hash(key * 3 + 2) - 0.5) * ph * 0.9;
                    double x = cx + ax * uu * len + nx * lat + sx0;
                    double y = cy + ay * uu * len + ny * lat + sy0;
                    double depth = 0.5 + 0.5 * z;
                    double r = (1.6 + 2.2 * depth) * u;
                    float a = (float) ((0.35 + 0.65 * depth) * e);

                    glow(c, x, y, r, 0xE8FBFF, a, depth > 0.45);
                    if (z > 0.8) glow(c, x, y, r * 1.8, 0x9FFFF0, a * 0.5f, false);   // bright front swoosh

                    for (int k = 0; k < 3; k++) {
                        double ang = hash(key * 7 + k) * 6.283;
                        double d = (3 + hash(key * 11 + k) * 14) * u;
                        double tw = 0.4 + 0.6 * (0.5 + 0.5 * Math.sin(T * 1.7 + key + k * 2.0));
                        int sz = Math.max(1, (int) Math.round(u * (1 + hash(key * 13 + k))));
                        int sx = (int) (x + Math.cos(ang) * d), sy = (int) (y + Math.sin(ang) * d);
                        c.fill(sx, sy, sx + sz, sy + sz, Ui.argb(0xDFF6FF, (float) (a * tw * 0.7)));
                    }
                }
            }
            if (pass == 0) rungs(c, cx, cy, ax, ay, nx, ny, len, radius, turns, u, e, T);
        }
    }

    /** Base pairs between the two strands. */
    private void rungs(DrawContext c, double cx, double cy, double ax, double ay, double nx, double ny,
                       double len, double radius, double turns, double u, double e, double T) {
        int m = 26;
        for (int k = 0; k < m; k++) {
            double uu = (k + 0.5) / m - 0.5;
            double phase = uu * turns * Math.PI * 2 + T * 0.45;
            double lat = Math.sin(phase) * radius, z = Math.cos(phase);
            for (int j = 1; j < 8; j++) {
                double t = j / 8.0;
                double l = lat * (1 - 2 * t), zz = z * (1 - 2 * t);
                double x = cx + ax * uu * len + nx * l, y = cy + ay * uu * len + ny * l;
                double depth = 0.5 + 0.5 * zz;
                glow(c, x, y, (0.8 + 0.8 * depth) * u, 0xBFEFFF, (float) (0.5 * (0.4 + 0.6 * depth) * e), false);
            }
        }
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        double tOpen = (now - opened) / 1000.0, T = now / 1000.0;
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;

        c.fillGradient(0, 0, width, height, 0xFF03040A, 0xFF0B0E1C);

        // background + helix in real pixels, so the particles are round and crisp
        double gs = client != null ? client.getWindow().getScaleFactor() : 2.0;
        double pw = width * gs, ph = height * gs;
        double ox = (mx / (double) Math.max(1, width) - 0.5) * 24 * gs;
        double oy = (my / (double) Math.max(1, height) - 0.5) * 24 * gs;
        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.scale((float) (1.0 / gs), (float) (1.0 / gs), 1f);
        stars(c, pw, ph, T, tOpen, ox, oy);
        helix(c, pw, ph, T, tOpen, ox, oy);
        ms.pop();

        float fade = (float) Math.min(1.0, Math.max(0, (tOpen - 0.6) / 0.9));
        Px.begin(c, (int) Math.max(1, Math.round(gs)), 0, 0);

        // pill tag
        String tag = Mods.tr("VISUAL CLIENT x 1.21.1|ВИЗУАЛЬНЫЙ КЛИЕНТ x 1.21.1");
        double tagW = Px.tw(tag, 1) + 18;
        double tagY = height * 0.27, tagX = (width - tagW) / 2;
        Px.rr(tagX - 0.5, tagY - 0.5, tagW + 1, 15, 7.5, Ui.argb(0x56667E, 0.8f * fade));
        Px.rr(tagX, tagY, tagW, 14, 7, Ui.argb(0x0A0E1A, 0.85f * fade));
        Px.text(tag, tagX + 9, tagY + 3, Ui.argb(0xB8C8DC, fade), 1);

        // title with a soft glow
        double ty = tagY + 24;
        Text title = Text.literal("BLACK MENU").formatted(Formatting.BOLD);
        double tx = (width - Px.tw(title, 3)) / 2;
        double o = 1.5 / Px.u;
        double[][] offs = {{-o, 0}, {o, 0}, {0, -o}, {0, o}};
        for (double[] d : offs) {
            Px.text(title, tx + d[0], ty + d[1], Ui.argb(0x7FF5FF, 0.22f * fade), 3, false);
        }
        Px.text(title, tx, ty, Ui.argb(0xFFFFFF, fade), 3, true);

        String sub = Mods.tr("Watch points of light organize into your client|Смотри, как точки света складываются в твой клиент");
        Px.text(sub, (width - Px.tw(sub, 1)) / 2, ty + 32, Ui.argb(0x8FA0B8, fade), 1);

        // pill buttons
        int n = LABELS.length, gap = 8;
        int bw = Math.max(72, Math.min(104, (width - 40) / n - gap)), bh = 18;
        int left = (width - (n * bw + (n - 1) * gap)) / 2;
        int by = (int) (height * 0.74);
        for (int i = 0; i < n; i++) {
            float f = (float) Math.min(1.0, Math.max(0, (tOpen - 1.4 - i * 0.1) / 0.5));
            int bx = left + i * (bw + gap);
            rects[i][0] = bx; rects[i][1] = by; rects[i][2] = bw; rects[i][3] = bh;
            boolean hv = Ui.inside(mx, my, bx, by, bw, bh);
            hover[i] += ((hv ? 1f : 0f) - hover[i]) * Math.min(1f, dt * 14f);
            float hf = hover[i];
            if (hf > 0.03f) Px.rr(bx - 3, by - 3, bw + 6, bh + 6, 12, Ui.argb(0x7FF5FF, 0.12f * hf * f));
            Px.rr(bx - 1, by - 1, bw + 2, bh + 2, 10, Ui.argb(Ui.mix(0x3A4660, 0xBFF4FF, hf), f));
            Px.rr(bx, by, bw, bh, 9, Ui.argb(Ui.mix(0x090C16, 0x14202E, hf), 0.92f * f));
            String label = Mods.tr(LABELS[i]);
            Px.text(label, bx + (bw - Px.tw(label, 1)) / 2, by + 5, Ui.argb(Ui.mix(0x9AA8BC, 0xFFFFFF, hf), f), 1);
        }

        String hint = Mods.tr("RIGHT SHIFT  -  menu in game|RIGHT SHIFT  -  меню в игре");
        Px.text(hint, (width - Px.tw(hint, 1)) / 2, height - 14, Ui.argb(0x44506A, fade), 1);
        Px.end();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < rects.length; i++) {
                if (!Ui.inside(mx, my, rects[i][0], rects[i][1], rects[i][2], rects[i][3])) continue;
                switch (i) {
                    case 0 -> client.setScreen(new SelectWorldScreen(this));
                    case 1 -> client.setScreen(new MultiplayerScreen(this));
                    case 2 -> client.setScreen(new OptionsScreen(this, client.options));
                    default -> client.scheduleStop();
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
