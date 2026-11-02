package com.blackmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Drawing in real screen pixels: crisp, anti-aliased rounded shapes and pixel-perfect text.
 * Coordinates are "units"; one unit is U real pixels (U is a whole number, so nothing ends up blurry or crooked).
 */
final class Px {
    static DrawContext c;
    static double gs = 1;
    static int u = 1, ox, oy;
    private static boolean on;

    private Px() {}

    static double guiScale() {
        return MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    private static TextRenderer tr() {
        return MinecraftClient.getInstance().textRenderer;
    }

    /** unit = real pixels per unit, originX/originY = real pixel position of unit (0,0). */
    static void begin(DrawContext ctx, int unit, int originX, int originY) {
        c = ctx;
        gs = guiScale();
        u = Math.max(1, unit);
        ox = originX;
        oy = originY;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.scale((float) (1.0 / gs), (float) (1.0 / gs), 1f);
        on = true;
    }

    static void end() {
        if (on) {
            c.getMatrices().pop();
            on = false;
        }
    }

    static void suspend() {
        end();
    }

    static void resume() {
        begin(c, u, ox, oy);
    }

    static int X(double x) {
        return ox + (int) Math.round(x * u);
    }

    static int Y(double y) {
        return oy + (int) Math.round(y * u);
    }

    static int alpha(int color, double mul) {
        int a = (int) Math.max(0, Math.min(255, (color >>> 24) * mul));
        return (a << 24) | (color & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ shapes

    static void fill(double x1, double y1, double x2, double y2, int color) {
        c.fill(X(x1), Y(y1), X(x2), Y(y2), color);
    }

    static void rr(double x, double y, double w, double h, double r, int color) {
        rrPx(X(x), Y(y), X(x + w), Y(y + h), r * u, color);
    }

    /** Thin rounded outline (about half a unit thick). */
    static void ring(double x, double y, double w, double h, double r, int color) {
        ringPx(X(x), Y(y), X(x + w), Y(y + h), r * u, Math.max(1, u / 2), color);
    }

    private static double insetAt(double cy, double h, double r) {
        if (r <= 0) return 0;
        if (cy < r) {
            double dy = r - cy;
            return r - Math.sqrt(Math.max(0, r * r - dy * dy));
        }
        if (cy > h - r) {
            double dy = cy - (h - r);
            return r - Math.sqrt(Math.max(0, r * r - dy * dy));
        }
        return 0;
    }

    /** Horizontal span with soft (anti-aliased) ends. */
    private static void hspan(int y, double xa, double xb, int color) {
        if (xb <= xa) return;
        int ia = (int) Math.ceil(xa), ib = (int) Math.floor(xb);
        if (ib > ia) c.fill(ia, y, ib, y + 1, color);
        if (ib >= ia) {
            double la = ia - xa, lb = xb - ib;
            if (la > 0.03) c.fill(ia - 1, y, ia, y + 1, alpha(color, la));
            if (lb > 0.03) c.fill(ib, y, ib + 1, y + 1, alpha(color, lb));
        } else {
            c.fill(ib, y, ib + 1, y + 1, alpha(color, xb - xa));
        }
    }

    static void rrPx(int x0, int y0, int x1, int y1, double r, int color) {
        int w = x1 - x0, h = y1 - y0;
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2.0));
        if (r < 1.0) {
            c.fill(x0, y0, x1, y1, color);
            return;
        }
        int rc = (int) Math.ceil(r);
        for (int row = 0; row < rc; row++) {
            double io = insetAt(row + 0.5, h, r);
            hspan(y0 + row, x0 + io, x1 - io, color);
            if (h - 1 - row != row) hspan(y1 - 1 - row, x0 + io, x1 - io, color);
        }
        if (h > 2 * rc) c.fill(x0, y0 + rc, x1, y1 - rc, color);
    }

    static void ringPx(int x0, int y0, int x1, int y1, double r, int t, int color) {
        int w = x1 - x0, h = y1 - y0;
        if (w <= 2 * t || h <= 2 * t) {
            rrPx(x0, y0, x1, y1, r, color);
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2.0));
        double rin = Math.max(0, r - t);
        int rc = Math.max((int) Math.ceil(r), t);
        for (int row = 0; row < rc && row < h; row++) {
            double cy = row + 0.5;
            double io = insetAt(cy, h, r);
            for (int pass = 0; pass < 2; pass++) {
                int y = pass == 0 ? y0 + row : y1 - 1 - row;
                if (pass == 1 && y == y0 + row) break;
                if (row < t) {
                    hspan(y, x0 + io, x1 - io, color);
                } else {
                    double ii = t + insetAt(cy - t, h - 2 * t, rin);
                    hspan(y, x0 + io, x0 + ii, color);
                    hspan(y, x1 - ii, x1 - io, color);
                }
            }
        }
        if (h > 2 * rc) {
            c.fill(x0, y0 + rc, x0 + t, y1 - rc, color);
            c.fill(x1 - t, y0 + rc, x1, y1 - rc, color);
        }
    }

    // ------------------------------------------------------------------ text and items

    /** Whole number of real pixels per text pixel, so glyphs stay crisp. */
    static int k(double sc) {
        return Math.max(1, (int) Math.round(sc * u));
    }

    static double tw(String s, double sc) {
        return tr().getWidth(s) * (double) k(sc) / u;
    }

    static double tw(Text t, double sc) {
        return tr().getWidth(t) * (double) k(sc) / u;
    }

    static String trim(String s, double maxUnits, double sc) {
        return tr().trimToWidth(s, (int) Math.max(0, maxUnits * u / k(sc)));
    }

    static void text(String s, double x, double y, int color, double sc) {
        text(Text.literal(s), x, y, color, sc, false);
    }

    static void text(Text t, double x, double y, int color, double sc, boolean shadow) {
        if ((color >>> 24) < 8) return;
        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate((float) X(x), (float) Y(y), 0f);
        float kk = k(sc);
        ms.scale(kk, kk, 1f);
        c.drawText(tr(), t, 0, 0, color, shadow);
        ms.pop();
    }

    /** A 16x16 unit item. withSlot adds the count / durability overlay. */
    static void item(ItemStack st, double x, double y, boolean withSlot) {
        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate((float) X(x), (float) Y(y), 0f);
        ms.scale((float) u, (float) u, 1f);
        c.drawItem(st, 0, 0);
        if (withSlot) c.drawItemInSlot(tr(), st, 0, 0);
        ms.pop();
    }
}
