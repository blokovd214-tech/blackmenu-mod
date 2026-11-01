package com.blackmenu;

import net.minecraft.client.gui.DrawContext;

/** Small 2D drawing helpers shared by the menu, the title screen and the HUD. */
final class Ui {
    private Ui() {}

    static int argb(int rgb, float a) {
        return ((int) (Math.max(0f, Math.min(1f, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    static int mix(int from, int to, float t) {
        int r = (int) (((from >> 16) & 255) * (1 - t) + ((to >> 16) & 255) * t);
        int g = (int) (((from >> 8) & 255) * (1 - t) + ((to >> 8) & 255) * t);
        int b = (int) ((from & 255) * (1 - t) + (to & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    static int rgb(float[] c) {
        return ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
    }

    static boolean inside(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /** Filled rounded rectangle (scanline based). */
    static void rrect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
        c.fill(x, y + r, x + w, y + h - r, color);
    }

    /** 1px outline with cut corners. */
    static void ring(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x + 1, y, x + w - 1, y + 1, color);
        c.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        c.fill(x, y + 1, x + 1, y + h - 1, color);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
