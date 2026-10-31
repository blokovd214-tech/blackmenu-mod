package com.blackmenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class BlackMenuScreen extends Screen {
    private static final int W = 260, ROW = 26, PAD = 16, HEAD = 52, R = 10, SW = 86, PILL = 100;
    private static final float MIN_SCALE = 0.6f, MAX_SCALE = 1.5f;

    private enum Page { MAIN, ESP, ATMO }
    private enum Kind { TOGGLE, SLIDER, CHOICE }

    private static final class Row {
        Kind kind;
        String name;
        BooleanSupplier getB;
        Consumer<Boolean> setB;
        Page link;
        Supplier<Float> getF;
        Consumer<Float> setF;
        float min, max;
        String[] opts;
        IntSupplier getI;
        IntConsumer setI;
        float anim;
    }

    private static Row toggle(String name, BooleanSupplier g, Consumer<Boolean> s, Page link) {
        Row r = new Row();
        r.kind = Kind.TOGGLE; r.name = name; r.getB = g; r.setB = s; r.link = link;
        r.anim = g.getAsBoolean() ? 1f : 0f;
        return r;
    }

    private static Row slider(String name, float min, float max, Supplier<Float> g, Consumer<Float> s) {
        Row r = new Row();
        r.kind = Kind.SLIDER; r.name = name; r.min = min; r.max = max; r.getF = g; r.setF = s;
        return r;
    }

    private static Row choice(String name, String[] opts, IntSupplier g, IntConsumer s) {
        Row r = new Row();
        r.kind = Kind.CHOICE; r.name = name; r.opts = opts; r.getI = g; r.setI = s;
        return r;
    }

    private final EnumMap<Page, List<Row>> pages = new EnumMap<>(Page.class);
    private Page page = Page.MAIN;
    private Row dragging;

    private final long opened = System.currentTimeMillis();
    private long last = opened;

    public BlackMenuScreen() {
        super(Text.literal("Black Menu"));

        pages.put(Page.MAIN, List.of(
                toggle("Fullbright", () -> Cfg.fullbright, BlackMenuClient::setFullbright, null),
                toggle("FPS", () -> Cfg.fps, v -> Cfg.fps = v, null),
                toggle("Coordinates", () -> Cfg.coords, v -> Cfg.coords = v, null),
                toggle("Target ESP", () -> Cfg.esp, v -> Cfg.esp = v, Page.ESP),
                toggle("Atmosphere", () -> Cfg.atmo, v -> Cfg.atmo = v, Page.ATMO)));

        pages.put(Page.ESP, List.of(
                toggle("Lightning", () -> Cfg.espLightning, v -> Cfg.espLightning = v, null),
                toggle("Circle", () -> Cfg.espCircle, v -> Cfg.espCircle = v, null),
                toggle("Chains", () -> Cfg.espChains, v -> Cfg.espChains = v, null),
                toggle("Orbs", () -> Cfg.espOrbs, v -> Cfg.espOrbs = v, null),
                choice("Color", Gfx.PALETTE, () -> Cfg.espColor, v -> Cfg.espColor = v),
                slider("Size", 0.5f, 2f, () -> Cfg.espSize, v -> Cfg.espSize = v),
                slider("Speed", 0.2f, 3f, () -> Cfg.espSpeed, v -> Cfg.espSpeed = v),
                slider("Opacity", 0.2f, 1f, () -> Cfg.espAlpha, v -> Cfg.espAlpha = v)));

        pages.put(Page.ATMO, List.of(
                choice("Time", Fx.TIME_NAMES, () -> Cfg.atmoTime, v -> Cfg.atmoTime = v),
                toggle("Aurora", () -> Cfg.aurora, v -> Cfg.aurora = v, null),
                choice("Aurora color", Fx.AURORA_COLORS, () -> Cfg.auroraColor, v -> Cfg.auroraColor = v),
                slider("Aurora glow", 0.2f, 2f, () -> Cfg.auroraGlow, v -> Cfg.auroraGlow = v),
                slider("Aurora speed", 0.2f, 3f, () -> Cfg.auroraSpeed, v -> Cfg.auroraSpeed = v),
                toggle("Particles", () -> Cfg.flies, v -> Cfg.flies = v, null),
                choice("Particle color", Gfx.PALETTE, () -> Cfg.fliesColor, v -> Cfg.fliesColor = v),
                slider("Particle amount", 0.1f, 2f, () -> Cfg.fliesAmount, v -> Cfg.fliesAmount = v),
                slider("Particle size", 0.4f, 3f, () -> Cfg.fliesSize, v -> Cfg.fliesSize = v),
                slider("Particle speed", 0.2f, 3f, () -> Cfg.fliesSpeed, v -> Cfg.fliesSpeed = v)));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        Cfg.save();
    }

    // ---------- helpers ----------

    private String title() {
        return switch (page) {
            case MAIN -> "BLACK MENU";
            case ESP -> "TARGET ESP";
            case ATMO -> "ATMOSPHERE";
        };
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
        return HEAD + (pages.get(page).size() + (page == Page.MAIN ? 1 : 0)) * ROW + 10;
    }

    private int px() {
        return (width - W) / 2;
    }

    private int py() {
        return (height - panelH()) / 2 + (int) ((1f - progress()) * 8);
    }

    private float scale() {
        float fit = Math.min((height - 16f) / panelH(), (width - 16f) / W);
        return Math.max(0.3f, Math.min(Cfg.menuScale, fit));
    }

    private double lx(double mx) {
        return (mx - width / 2.0) / scale() + width / 2.0;
    }

    private double ly(double my) {
        return (my - height / 2.0) / scale() + height / 2.0;
    }

    private void changeScale(float d) {
        float v = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Cfg.menuScale + d));
        Cfg.menuScale = Math.round(v * 20f) / 20f;
    }

    private static int sliderX(int x) {
        return x + W - PAD - 34 - SW;
    }

    private void setSlider(Row r, double ux, int x) {
        double f = Math.max(0, Math.min(1, (ux - sliderX(x)) / SW));
        float v = (float) (r.min + f * (r.max - r.min));
        r.setF.accept(Math.round(v * 10f) / 10f);
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
        double ux = lx(mx), uy = ly(my);
        int x = px(), y = py(), h = panelH();
        List<Row> rows = pages.get(page);

        MatrixStack ms = c.getMatrices();
        ms.push();
        ms.translate(width / 2f, height / 2f, 0f);
        ms.scale(s, s, 1f);
        ms.translate(-width / 2f, -height / 2f, 0f);

        rrect(c, x - 1, y - 1, W + 2, h + 2, R + 1, argb(0x262626, a));
        rrect(c, x, y, W, h, R, argb(0x050505, a));

        if (a > 0.05f) {
            if (page == Page.MAIN) {
                c.drawText(textRenderer, Text.literal("BLACK MENU").formatted(Formatting.BOLD),
                        x + PAD, y + 14, argb(0xFFFFFF, a), false);
                c.drawText(textRenderer, "RIGHT SHIFT", x + PAD, y + 27, argb(0x555555, a), false);
            } else {
                boolean hb = inside(ux, uy, x, y, W, HEAD - 8);
                c.drawText(textRenderer, Text.literal("< " + title()).formatted(Formatting.BOLD),
                        x + PAD, y + 14, argb(hb ? 0xFFFFFF : 0xBBBBBB, a), false);
                c.drawText(textRenderer, "BACK", x + PAD, y + 27, argb(0x555555, a), false);
            }
        }
        c.fill(x + PAD, y + HEAD - 8, x + W - PAD, y + HEAD - 7, argb(0x1C1C1C, a));

        for (int i = 0; i < rows.size(); i++) {
            drawRow(c, rows.get(i), x, y + HEAD + i * ROW, a, dt, ux, uy);
        }
        if (page == Page.MAIN) drawSize(c, x, y + HEAD + rows.size() * ROW, a, ux, uy);

        ms.pop();
    }

    private void drawRow(DrawContext c, Row r, int x, int ry, float a, float dt, double ux, double uy) {
        if (inside(ux, uy, x, ry, W, ROW)) {
            rrect(c, x + 6, ry + 1, W - 12, ROW - 2, 6, argb(0x101010, a));
        }
        int ty = ry + (ROW - 8) / 2;

        switch (r.kind) {
            case TOGGLE -> {
                float target = r.getB.getAsBoolean() ? 1f : 0f;
                r.anim += (target - r.anim) * Math.min(1f, dt * 16f);
                float t = r.anim;
                if (a > 0.05f) {
                    c.drawText(textRenderer, r.name, x + PAD, ty, argb(mix(0x8A8A8A, 0xFFFFFF, t), a), false);
                }
                int sx = x + W - PAD - 28, sy = ry + (ROW - 14) / 2;
                rrect(c, sx - 1, sy - 1, 30, 16, 8, argb(mix(0x2A2A2A, 0xFFFFFF, t), a));
                rrect(c, sx, sy, 28, 14, 7, argb(mix(0x0F0F0F, 0xFFFFFF, t), a));
                int kx = sx + 3 + (int) (14 * t);
                rrect(c, kx, sy + 3, 8, 8, 4, argb(mix(0x5A5A5A, 0x000000, t), a));
                if (r.link != null && a > 0.05f) {
                    boolean hv = inside(ux, uy, sx - 24, ry, 20, ROW);
                    c.drawText(textRenderer, ">", sx - 18, ty, argb(hv ? 0xFFFFFF : 0x666666, a), false);
                }
            }
            case SLIDER -> {
                float v = r.getF.get();
                float f = Math.max(0f, Math.min(1f, (v - r.min) / (r.max - r.min)));
                if (a > 0.05f) {
                    c.drawText(textRenderer, r.name, x + PAD, ty, argb(0x8A8A8A, a), false);
                    String txt = String.format("%.1f", v);
                    c.drawText(textRenderer, txt, x + W - PAD - textRenderer.getWidth(txt), ty,
                            argb(0xCCCCCC, a), false);
                }
                int tx = sliderX(x), tyy = ry + ROW / 2 - 2;
                rrect(c, tx, tyy, SW, 4, 2, argb(0x1E1E1E, a));
                int fw = Math.max(4, (int) (SW * f));
                rrect(c, tx, tyy, fw, 4, 2, argb(0xFFFFFF, a));
                rrect(c, tx + (int) ((SW - 8) * f), tyy - 2, 8, 8, 4, argb(0xFFFFFF, a));
            }
            case CHOICE -> {
                int n = r.opts.length;
                String txt = r.opts[Math.floorMod(r.getI.getAsInt(), n)];
                int pxl = x + W - PAD - PILL;
                boolean hv = inside(ux, uy, pxl, ry + 3, PILL, ROW - 6);
                if (a > 0.05f) {
                    c.drawText(textRenderer, r.name, x + PAD, ty, argb(0x8A8A8A, a), false);
                }
                rrect(c, pxl, ry + 3, PILL, ROW - 6, 6, argb(hv ? 0x1E1E1E : 0x131313, a));
                if (a > 0.05f) {
                    c.drawText(textRenderer, txt, pxl + (PILL - textRenderer.getWidth(txt)) / 2, ty,
                            argb(0xFFFFFF, a), false);
                }
            }
        }
    }

    private void drawSize(DrawContext c, int x, int rowY, float a, double ux, double uy) {
        int plusX = x + W - PAD - 16;
        int textL = plusX - 6 - 28;
        int minusX = textL - 6 - 16;
        int by = rowY + 5;

        if (a > 0.05f) {
            int ty = rowY + (ROW - 8) / 2;
            c.drawText(textRenderer, "Size", x + PAD, ty, argb(0x8A8A8A, a), false);
            String pct = Math.round(Cfg.menuScale * 100) + "%";
            c.drawText(textRenderer, pct, textL + (28 - textRenderer.getWidth(pct)) / 2, ty,
                    argb(0xCCCCCC, a), false);
        }
        boolean hMinus = inside(ux, uy, minusX, by, 16, 16);
        boolean hPlus = inside(ux, uy, plusX, by, 16, 16);
        rrect(c, minusX, by, 16, 16, 5, argb(hMinus ? 0x2A2A2A : 0x151515, a));
        rrect(c, plusX, by, 16, 16, 5, argb(hPlus ? 0x2A2A2A : 0x151515, a));
        int sym = argb(0xFFFFFF, a);
        c.fill(minusX + 4, by + 7, minusX + 12, by + 9, sym);
        c.fill(plusX + 4, by + 7, plusX + 12, by + 9, sym);
        c.fill(plusX + 7, by + 4, plusX + 9, by + 12, sym);
    }

    // ---------- input ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double ux = lx(mx), uy = ly(my);
        int x = px(), y = py();

        if (page != Page.MAIN && button == 0 && inside(ux, uy, x, y, W, HEAD - 8)) {
            page = Page.MAIN;
            dragging = null;
            return true;
        }

        List<Row> rows = pages.get(page);
        for (int i = 0; i < rows.size(); i++) {
            int ry = y + HEAD + i * ROW;
            if (!inside(ux, uy, x, ry, W, ROW)) continue;
            click(rows.get(i), x, ry, ux, uy, button);
            return true;
        }

        if (page == Page.MAIN && button == 0) {
            int rowY = y + HEAD + rows.size() * ROW;
            int plusX = x + W - PAD - 16;
            int minusX = plusX - 6 - 28 - 6 - 16;
            if (inside(ux, uy, minusX, rowY + 5, 16, 16)) { changeScale(-0.1f); return true; }
            if (inside(ux, uy, plusX, rowY + 5, 16, 16)) { changeScale(0.1f); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click(Row r, int x, int ry, double ux, double uy, int button) {
        switch (r.kind) {
            case TOGGLE -> {
                if (button != 0) return;
                int sx = x + W - PAD - 28;
                if (r.link != null && inside(ux, uy, sx - 24, ry, 20, ROW)) {
                    page = r.link;
                    dragging = null;
                } else {
                    r.setB.accept(!r.getB.getAsBoolean());
                }
            }
            case SLIDER -> {
                if (button == 0 && inside(ux, uy, sliderX(x) - 6, ry, SW + 12, ROW)) {
                    dragging = r;
                    setSlider(r, ux, x);
                }
            }
            case CHOICE -> {
                int n = r.opts.length;
                int i = Math.floorMod(r.getI.getAsInt(), n);
                r.setI.accept(Math.floorMod(i + (button == 1 ? -1 : 1), n));
            }
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            setSlider(dragging, lx(mx), px());
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
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
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && page != Page.MAIN) {
            page = Page.MAIN;
            dragging = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
