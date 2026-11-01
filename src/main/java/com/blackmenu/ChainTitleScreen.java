package com.blackmenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Black title screen: menu signs hanging on swaying chains. */
public class ChainTitleScreen extends Screen {
    private static final String[] LABELS = {"SINGLEPLAYER", "MULTIPLAYER", "OPTIONS", "QUIT"};

    private final long opened = System.currentTimeMillis();
    private long last = opened;
    private final int[][] rects = new int[4][4];
    private final float[] hover = new float[4];

    public ChainTitleScreen() {
        super(Text.literal("Black Menu"));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private static double easeOutBack(double p) {
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(p - 1, 3) + c1 * Math.pow(p - 1, 2);
    }

    private static double hash(int n) {
        double s = Math.sin(n * 12.9898) * 43758.5453;
        return s - Math.floor(s);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        double tOpen = (now - opened) / 1000.0, T = now / 1000.0;
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;

        c.fillGradient(0, 0, width, height, 0xFF000000, 0xFF0A0A10);
        dust(c, T);

        // title
        float fade = (float) Math.min(1.0, tOpen / 0.8);
        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate(width / 2f, 18f, 0f);
        ms.scale(3f, 3f, 1f);
        Text title = Text.literal("BLACK MENU").formatted(Formatting.BOLD);
        c.drawText(textRenderer, title, -textRenderer.getWidth(title) / 2, 0, Ui.argb(0xFFFFFF, fade), false);
        ms.pop();
        String sub = "MINECRAFT 1.21.1";
        c.drawText(textRenderer, sub, (width - textRenderer.getWidth(sub)) / 2, 50, Ui.argb(0x555555, fade), false);

        // signs on chains
        int n = LABELS.length;
        int sw = Math.max(70, Math.min(110, (width - 60) / n - 10));
        int sh = 34, gap = 14;
        int left = (width - (n * sw + (n - 1) * gap)) / 2;

        for (int i = 0; i < n; i++) {
            double p = Math.max(0, Math.min(1, (tOpen - 0.15 - i * 0.12) / 0.9));
            double drop = easeOutBack(p);
            int baseTop = (int) (height * 0.46) + ((i % 2 == 0) ? 0 : 20);
            double topY = baseTop * drop - 3 * hover[i];
            double swayX = Math.sin(T * 1.1 + i * 1.3) * 3.0 * Math.min(1.0, drop);

            int slot = left + i * (sw + gap);
            int sx = slot + (int) Math.round(swayX);
            int sy = (int) Math.round(topY);
            rects[i][0] = sx; rects[i][1] = sy; rects[i][2] = sw; rects[i][3] = sh;

            boolean hv = Ui.inside(mx, my, sx, sy, sw, sh);
            hover[i] += ((hv ? 1f : 0f) - hover[i]) * Math.min(1f, dt * 14f);
            float hf = hover[i];

            chain(c, slot + 12, -4, slot + 12 + swayX, sy + 6, T, i * 0.7);
            chain(c, slot + sw - 12, -4, slot + sw - 12 + swayX, sy + 6, T, i * 0.7 + 1.1);

            Ui.rrect(c, sx - 1, sy - 1, sw + 2, sh + 2, 8, 0xFF000000 | Ui.mix(0x2A2A2E, 0xFFFFFF, hf));
            Ui.rrect(c, sx, sy, sw, sh, 7, 0xFF000000 | Ui.mix(0x08080A, 0x141416, hf));
            Ui.rrect(c, sx + 10, sy + 4, 4, 4, 2, 0xFF8A8A90);
            Ui.rrect(c, sx + sw - 14, sy + 4, 4, 4, 2, 0xFF8A8A90);
            int tw = textRenderer.getWidth(LABELS[i]);
            c.drawText(textRenderer, LABELS[i], sx + (sw - tw) / 2, sy + (sh - 8) / 2 + 2,
                    0xFF000000 | Ui.mix(0x8A8A8A, 0xFFFFFF, hf), false);
        }

        String hint = "RIGHT SHIFT  -  menu in game";
        c.drawText(textRenderer, hint, (width - textRenderer.getWidth(hint)) / 2, height - 14,
                Ui.argb(0x444444, fade), false);
    }

    private void chain(DrawContext c, double ax, double ay, double bx, double by, double T, double ph) {
        double len = by - ay;
        if (len < 8) return;
        int links = (int) (len / 7);
        for (int k = 0; k < links; k++) {
            double u = (k + 0.5) * 7 / len;
            double x = ax + (bx - ax) * u * u + Math.sin(T * 1.7 + k * 0.35 + ph) * 0.9 * u;
            int xi = (int) Math.round(x), yi = (int) Math.round(ay + k * 7 + 3.5);
            if (k % 2 == 0) {
                Ui.rrect(c, xi - 3, yi - 5, 7, 10, 3, 0xFF7C7C80);
                Ui.rrect(c, xi - 1, yi - 3, 3, 6, 1, 0xFF050507);
                c.fill(xi - 2, yi - 2, xi - 1, yi + 2, 0xFFB4B4BA);
            } else {
                c.fill(xi - 1, yi - 5, xi + 1, yi + 5, 0xFF55555A);
                c.fill(xi - 1, yi - 5, xi, yi + 5, 0xFF8A8A90);
            }
        }
    }

    private void dust(DrawContext c, double T) {
        for (int j = 0; j < 50; j++) {
            double x = (hash(j) * width + T * (3 + hash(j + 100) * 6)) % width;
            double y = ((hash(j + 200) * height - T * (2 + hash(j + 300) * 5)) % height + height) % height;
            float a = (float) (0.12 + 0.3 * (0.5 + 0.5 * Math.sin(T * 0.8 + j)));
            int s = hash(j + 400) > 0.8 ? 2 : 1;
            c.fill((int) x, (int) y, (int) x + s, (int) y + s, Ui.argb(0xFFFFFF, a));
        }
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
