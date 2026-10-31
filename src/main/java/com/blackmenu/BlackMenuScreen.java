package com.blackmenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class BlackMenuScreen extends Screen {
    private static final int W = 240, ROW = 28, PAD = 16, HEAD = 52;

    private record Option(String name, BooleanSupplier get, Consumer<Boolean> set, float[] anim) {
        Option(String name, BooleanSupplier get, Consumer<Boolean> set) {
            this(name, get, set, new float[]{get.getAsBoolean() ? 1f : 0f});
        }
    }

    private final List<Option> options = List.of(
            new Option("Fullbright", () -> BlackMenuClient.fullbright, BlackMenuClient::setFullbright),
            new Option("FPS", () -> BlackMenuClient.fps, v -> BlackMenuClient.fps = v),
            new Option("Coordinates", () -> BlackMenuClient.coords, v -> BlackMenuClient.coords = v)
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

    private int panelH() {
        return HEAD + options.size() * ROW + 10;
    }

    private int px() {
        return (width - W) / 2;
    }

    private int py() {
        return (height - panelH()) / 2 + (int) ((1f - progress()) * 8);
    }

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

        int x = px(), y = py(), h = panelH();

        c.fill(x - 1, y - 1, x + W + 1, y + h + 1, argb(0x262626, a));
        c.fill(x, y, x + W, y + h, argb(0x050505, a));

        if (a > 0.05f) {
            c.drawText(textRenderer, Text.literal("BLACK MENU").formatted(Formatting.BOLD),
                    x + PAD, y + 14, argb(0xFFFFFF, a), false);
            c.drawText(textRenderer, "RIGHT SHIFT", x + PAD, y + 27, argb(0x555555, a), false);
        }
        c.fill(x + PAD, y + HEAD - 8, x + W - PAD, y + HEAD - 7, argb(0x1C1C1C, a));

        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            int ry = y + HEAD + i * ROW;
            boolean hover = mx >= x && mx < x + W && my >= ry && my < ry + ROW;
            if (hover) c.fill(x + 1, ry, x + W - 1, ry + ROW, argb(0x101010, a));

            float target = o.get().getAsBoolean() ? 1f : 0f;
            o.anim()[0] += (target - o.anim()[0]) * Math.min(1f, dt * 16f);
            float t = o.anim()[0];

            if (a > 0.05f) {
                c.drawText(textRenderer, o.name(), x + PAD, ry + (ROW - 8) / 2,
                        argb(mix(0x8A8A8A, 0xFFFFFF, t), a), false);
            }

            int sx = x + W - PAD - 28, sy = ry + (ROW - 14) / 2;
            c.fill(sx - 1, sy - 1, sx + 29, sy + 15, argb(mix(0x2A2A2A, 0xFFFFFF, t), a));
            c.fill(sx, sy, sx + 28, sy + 14, argb(mix(0x0F0F0F, 0xFFFFFF, t), a));
            int kx = sx + 3 + (int) (14 * t);
            c.fill(kx, sy + 3, kx + 8, sy + 11, argb(mix(0x5A5A5A, 0x000000, t), a));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int x = px(), y = py();
            for (int i = 0; i < options.size(); i++) {
                int ry = y + HEAD + i * ROW;
                if (mx >= x && mx < x + W && my >= ry && my < ry + ROW) {
                    Option o = options.get(i);
                    o.set().accept(!o.get().getAsBoolean());
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
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
