package com.blackmenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class BlackMenuScreen extends Screen {
    private static final int W = 240, ROW = 28, PAD = 16, HEAD = 52, R = 10;
    private static final float MIN_SCALE = 0.6f, MAX_SCALE = 1.5f;

    private record Option(String name, BooleanSupplier get, Consumer<Boolean> set, float[] anim) {
        Option(String name, BooleanSupplier get, Consumer<Boolean> set) {
            this(name, get, set, new float[]{get.getAsBoolean() ? 1f : 0f});
        }
    }

    private final List<Option> options = List.of(
            new Option("Fullbright", () -> BlackMenuClient.fullbright, BlackMenuClient::setFullbright),
            new Option("FPS", () -> BlackMenuClient.fps, v -> BlackMenuClient.fps = v),
            new Option("Coordinates", () -> BlackMenuClient.coords, v -> BlackMenuClient.coords = v),
            new Option("Target ESP", () -> BlackMenuClient.targetEsp, v -> BlackMenuClient.targetEsp = v),
            new Option("Atmosphere", () -> BlackMenuClient.atmosphere, v -> BlackMenuClient.atmosphere = v)
    );

    private final long opened = System.currentTimeMillis();
    private long last = opened;

    public BlackMenuScreen() {
        super(Text.literal("Black Menu"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------- helpers ----------

    private float progress() {
        float t = Math.min(1f, (System.currentTimeMillis() - opened) / 200f);
        return 1f - (1f - t) * (1f - t);
    }

    private static int argb(int rgb, float a) {
        return ((int) (Math.max(0f, Math.min(1f, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int from, int to, float t) {
        int r = (int) (((from >> 16) & 255) * (1 - t) + ((to >> 16) & 255) * t);
        int g = (int) (((from >> 8) & 255) * (1 - t) + ((to >> 8) & 255) * t);
        int b = (int) ((from & 255) * (1 - t) + (to & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** Filled rounded rectangle (scanline based). */
    private static void rrect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
        c.fill(x, y + r, x + w, y + h - r, color);
    }

    private static boolean inside(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private int panelH() {
        return HEAD + (options.size() + 1) * ROW + 10;
    }

    private int px() {
        return (width - W) / 2;
    }

    private int py() {
        return (height - panelH()) / 2 + (int) ((1f - progress()) * 8);
    }

    /** Menu scale, limited so the panel always fits on screen. */
    private float scale() {
        float fit = Math.min((height - 16f) / panelH(), (width - 16f) / W);
        return Math.max(0.3f, Math.min(BlackMenuClient.menuScale, fit));
    }

    private double lx(double mx) {
        return (mx - width / 2.0) / scale() + width / 2.0;
    }

    private double ly(double my) {
        return (my - height / 2.0) / scale() + height / 2.0;
    }

    private void changeScale(float d) {
        float v = Math.max(MIN_SCALE, Math.min(MAX_SCALE, BlackMenuClient.menuScale + d));
        BlackMenuClient.menuScale = Math.round(v * 20f) / 20f;
    }

    // ---------- rendering ----------

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, argb(0x000000, 0.55f * progress()));
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        renderBackground(c, mx, my, delta);

        float a = progress();
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;

        float s = scale();
        double mxl = lx(mx), myl = ly(my);
        int x = px(), y = py(), h = panelH();

        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate(width / 2f, height / 2f, 0f);
        ms.scale(s, s, 1f);
        ms.translate(-width / 2f, -height / 2f, 0f);

        // panel: border + body, rounded
        rrect(c, x - 1, y - 1, W + 2, h + 2, R + 1, argb(0x262626, a));
        rrect(c, x, y, W, h, R, argb(0x050505, a));

        if (a > 0.05f) {
            c.drawText(textRenderer, Text.literal("BLACK MENU").formatted(Formatting.BOLD),
                    x + PAD, y + 14, argb(0xFFFFFF, a), false);
            c.drawText(textRenderer, "RIGHT SHIFT", x + PAD, y + 27, argb(0x555555, a), false);
        }
        c.fill(x + PAD, y + HEAD - 8, x + W - PAD, y + HEAD - 7, argb(0x1C1C1C, a));

        // toggle rows
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            int ry = y + HEAD + i * ROW;
            if (inside(mxl, myl, x, ry, W, ROW)) {
                rrect(c, x + 6, ry + 1, W - 12, ROW - 2, 6, argb(0x101010, a));
            }

            float target = o.get().getAsBoolean() ? 1f : 0f;
            o.anim()[0] += (target - o.anim()[0]) * Math.min(1f, dt * 16f);
            float t = o.anim()[0];

            if (a > 0.05f) {
                c.drawText(textRenderer, o.name(), x + PAD, ry + (ROW - 8) / 2,
                        argb(mix(0x8A8A8A, 0xFFFFFF, t), a), false);
            }

            int sx = x + W - PAD - 28, sy = ry + (ROW - 14) / 2;
            rrect(c, sx - 1, sy - 1, 30, 16, 8, argb(mix(0x2A2A2A, 0xFFFFFF, t), a));
            rrect(c, sx, sy, 28, 14, 7, argb(mix(0x0F0F0F, 0xFFFFFF, t), a));
            int kx = sx + 3 + (int) (14 * t);
            rrect(c, kx, sy + 3, 8, 8, 4, argb(mix(0x5A5A5A, 0x000000, t), a));
        }

        // size row:  Size   [-]  100%  [+]
        int rowY = y + HEAD + options.size() * ROW;
        int plusX = x + W - PAD - 16;
        int textL = plusX - 6 - 28;
        int minusX = textL - 6 - 16;
        int by = rowY + 6;

        if (a > 0.05f) {
            c.drawText(textRenderer, "Size", x + PAD, rowY + (ROW - 8) / 2, argb(0x8A8A8A, a), false);
            String pct = Math.round(BlackMenuClient.menuScale * 100) + "%";
            c.drawText(textRenderer, pct, textL + (28 - textRenderer.getWidth(pct)) / 2,
                    rowY + (ROW - 8) / 2, argb(0xCCCCCC, a), false);
        }
        boolean hMinus = inside(mxl, myl, minusX, by, 16, 16);
        boolean hPlus = inside(mxl, myl, plusX, by, 16, 16);
        rrect(c, minusX, by, 16, 16, 5, argb(hMinus ? 0x2A2A2A : 0x151515, a));
        rrect(c, plusX, by, 16, 16, 5, argb(hPlus ? 0x2A2A2A : 0x151515, a));
        int sym = argb(0xFFFFFF, a);
        c.fill(minusX + 4, by + 7, minusX + 12, by + 9, sym);
        c.fill(plusX + 4, by + 7, plusX + 12, by + 9, sym);
        c.fill(plusX + 7, by + 4, plusX + 9, by + 12, sym);

        ms.pop();
    }

    // ---------- input ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            double ux = lx(mx), uy = ly(my);
            int x = px(), y = py();

            for (int i = 0; i < options.size(); i++) {
                int ry = y + HEAD + i * ROW;
                if (inside(ux, uy, x, ry, W, ROW)) {
                    Option o = options.get(i);
                    o.set().accept(!o.get().getAsBoolean());
                    return true;
                }
            }

            int rowY = y + HEAD + options.size() * ROW;
            int plusX = x + W - PAD - 16;
            int minusX = plusX - 6 - 28 - 6 - 16;
            if (inside(ux, uy, minusX, rowY + 6, 16, 16)) {
                changeScale(-0.1f);
                return true;
            }
            if (inside(ux, uy, plusX, rowY + 6, 16, 16)) {
                changeScale(0.1f);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        changeScale((float) (vertical * 0.05));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
