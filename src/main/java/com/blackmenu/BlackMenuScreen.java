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

import static com.blackmenu.Ui.argb;
import static com.blackmenu.Ui.inside;
import static com.blackmenu.Ui.mix;
import static com.blackmenu.Ui.rrect;

public class BlackMenuScreen extends Screen {
    private static final int W = 260, ROW = 26, PAD = 16, HEAD = 52, R = 10, SW = 86, PILL = 100;
    private static final float MIN_SCALE = 0.6f, MAX_SCALE = 1.5f;
    private static final String[] HB_COLORS = {"By type", "Cyan", "Purple", "Pink", "Red", "Green", "Gold", "White", "Rainbow"};

    private enum Page {
        MAIN(null, "BLACK MENU"),
        VISUAL(MAIN, "VISUAL"), PVP(MAIN, "PVP"), WORLD(MAIN, "WORLD"), HUD(MAIN, "HUD"), SETTINGS(MAIN, "SETTINGS"),
        BORDERS(VISUAL, "ITEM BORDERS"), BLUR(VISUAL, "BLUR+"),
        HITBOX(PVP, "HITBOXES"), ESP(PVP, "TARGET ESP"),
        ATMO(WORLD, "ATMOSPHERE"), DUST(WORLD, "CAVE DUST");

        final Page parent;
        final String title;

        Page(Page parent, String title) {
            this.parent = parent;
            this.title = title;
        }
    }

    private enum Kind { TOGGLE, SLIDER, CHOICE, LINK, SIZE }

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

    private static Row link(String name, Page p) {
        Row r = new Row();
        r.kind = Kind.LINK; r.name = name; r.link = p;
        return r;
    }

    private static Row size() {
        Row r = new Row();
        r.kind = Kind.SIZE; r.name = "Menu size";
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
                link("Visual", Page.VISUAL),
                link("PvP", Page.PVP),
                link("World", Page.WORLD),
                link("HUD", Page.HUD),
                link("Settings", Page.SETTINGS)));

        pages.put(Page.VISUAL, List.of(
                toggle("Fullbright", () -> Cfg.fullbright, BlackMenuClient::setFullbright, null),
                toggle("Smooth swapping", () -> Cfg.smoothSwap, v -> Cfg.smoothSwap = v, null),
                slider("Swap speed", 0.5f, 3f, () -> Cfg.swapSpeed, v -> Cfg.swapSpeed = v),
                toggle("Item borders", () -> Cfg.borders, v -> Cfg.borders = v, Page.BORDERS),
                link("Blur+", Page.BLUR)));

        pages.put(Page.PVP, List.of(
                toggle("Hitboxes", () -> Cfg.hb, v -> Cfg.hb = v, Page.HITBOX),
                toggle("Target ESP", () -> Cfg.esp, v -> Cfg.esp = v, Page.ESP)));

        pages.put(Page.WORLD, List.of(
                toggle("Atmosphere", () -> Cfg.atmo, v -> Cfg.atmo = v, Page.ATMO),
                toggle("Cave dust", () -> Cfg.dust, v -> Cfg.dust = v, Page.DUST)));

        pages.put(Page.HUD, List.of(
                toggle("Hotbar", () -> Cfg.hotbar, v -> Cfg.hotbar = v, null),
                toggle("Info panel", () -> Cfg.infoPanel, v -> Cfg.infoPanel = v, null),
                toggle("FPS", () -> Cfg.fps, v -> Cfg.fps = v, null),
                toggle("Coordinates", () -> Cfg.coords, v -> Cfg.coords = v, null),
                toggle("Ping", () -> Cfg.ping, v -> Cfg.ping = v, null),
                choice("Accent", Gfx.PALETTE, () -> Cfg.hudAccent, v -> Cfg.hudAccent = v),
                slider("Opacity", 0.3f, 1f, () -> Cfg.hudAlpha, v -> Cfg.hudAlpha = v)));

        pages.put(Page.SETTINGS, List.of(
                toggle("Chain title screen", () -> Cfg.titleChains, v -> Cfg.titleChains = v, null),
                size()));

        pages.put(Page.BORDERS, List.of(
                toggle("Inventory", () -> Cfg.bordersInv, v -> Cfg.bordersInv = v, null),
                toggle("Hotbar", () -> Cfg.bordersHotbar, v -> Cfg.bordersHotbar = v, null),
                toggle("Hide common", () -> Cfg.bordersHideCommon, v -> Cfg.bordersHideCommon = v, null),
                slider("Opacity", 0.2f, 1f, () -> Cfg.bordersAlpha, v -> Cfg.bordersAlpha = v)));

        pages.put(Page.BLUR, List.of(
                toggle("Blur menu", () -> Cfg.blurMenu, v -> Cfg.blurMenu = v, null),
                slider("Dim", 0f, 0.9f, () -> Cfg.blurDim, v -> Cfg.blurDim = v)));

        pages.put(Page.HITBOX, List.of(
                toggle("Players", () -> Cfg.hbPlayers, v -> Cfg.hbPlayers = v, null),
                toggle("Mobs", () -> Cfg.hbMobs, v -> Cfg.hbMobs = v, null),
                toggle("Others", () -> Cfg.hbOthers, v -> Cfg.hbOthers = v, null),
                toggle("Fill", () -> Cfg.hbFill, v -> Cfg.hbFill = v, null),
                toggle("Eye line", () -> Cfg.hbEye, v -> Cfg.hbEye = v, null),
                choice("Color", HB_COLORS, () -> Cfg.hbColor, v -> Cfg.hbColor = v),
                slider("Line width", 0.5f, 3f, () -> Cfg.hbWidth, v -> Cfg.hbWidth = v),
                slider("Opacity", 0.2f, 1f, () -> Cfg.hbAlpha, v -> Cfg.hbAlpha = v)));

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

        pages.put(Page.DUST, List.of(
                slider("Amount", 0.1f, 2f, () -> Cfg.dustAmount, v -> Cfg.dustAmount = v),
                slider("Size", 0.4f, 3f, () -> Cfg.dustSize, v -> Cfg.dustSize = v),
                slider("Speed", 0.2f, 3f, () -> Cfg.dustSpeed, v -> Cfg.dustSpeed = v),
                choice("Color", Gfx.PALETTE, () -> Cfg.dustColor, v -> Cfg.dustColor = v)));
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

    private float progress() {
        float t = Math.min(1f, (System.currentTimeMillis() - opened) / 200f);
        return 1f - (1f - t) * (1f - t);
    }

    private int panelH() {
        return HEAD + pages.get(page).size() * ROW + 10;
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

    private void go(Page p) {
        page = p;
        dragging = null;
    }

    // ---------- rendering ----------

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
        float p = progress();
        if (Cfg.blurMenu && client != null && client.world != null) {
            super.renderBackground(c, mx, my, delta);   // the game's own background blur
            if (Cfg.blurDim > 0f) c.fill(0, 0, width, height, argb(0x000000, Cfg.blurDim * p));
        } else {
            c.fill(0, 0, width, height, argb(0x000000, (0.35f + Cfg.blurDim * 0.5f) * p));
        }
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
                c.drawText(textRenderer, Text.literal("< " + page.title).formatted(Formatting.BOLD),
                        x + PAD, y + 14, argb(hb ? 0xFFFFFF : 0xBBBBBB, a), false);
                c.drawText(textRenderer, "BACK", x + PAD, y + 27, argb(0x555555, a), false);
            }
        }
        c.fill(x + PAD, y + HEAD - 8, x + W - PAD, y + HEAD - 7, argb(0x1C1C1C, a));

        for (int i = 0; i < rows.size(); i++) {
            drawRow(c, rows.get(i), x, y + HEAD + i * ROW, a, dt, ux, uy);
        }

        ms.pop();
    }

    private void drawRow(DrawContext c, Row r, int x, int ry, float a, float dt, double ux, double uy) {
        boolean rowHover = inside(ux, uy, x, ry, W, ROW);
        if (rowHover) rrect(c, x + 6, ry + 1, W - 12, ROW - 2, 6, argb(0x101010, a));
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
                rrect(c, tx, tyy, Math.max(4, (int) (SW * f)), 4, 2, argb(0xFFFFFF, a));
                rrect(c, tx + (int) ((SW - 8) * f), tyy - 2, 8, 8, 4, argb(0xFFFFFF, a));
            }
            case CHOICE -> {
                int n = r.opts.length;
                String txt = r.opts[Math.floorMod(r.getI.getAsInt(), n)];
                int pxl = x + W - PAD - PILL;
                boolean hv = inside(ux, uy, pxl, ry + 3, PILL, ROW - 6);
                if (a > 0.05f) c.drawText(textRenderer, r.name, x + PAD, ty, argb(0x8A8A8A, a), false);
                rrect(c, pxl, ry + 3, PILL, ROW - 6, 6, argb(hv ? 0x1E1E1E : 0x131313, a));
                if (a > 0.05f) {
                    c.drawText(textRenderer, txt, pxl + (PILL - textRenderer.getWidth(txt)) / 2, ty,
                            argb(0xFFFFFF, a), false);
                }
            }
            case LINK -> {
                if (a > 0.05f) {
                    c.drawText(textRenderer, r.name, x + PAD, ty, argb(rowHover ? 0xFFFFFF : 0xBBBBBB, a), false);
                    c.drawText(textRenderer, ">", x + W - PAD - 6, ty, argb(rowHover ? 0xFFFFFF : 0x666666, a), false);
                }
            }
            case SIZE -> {
                int plusX = x + W - PAD - 16;
                int textL = plusX - 6 - 28;
                int minusX = textL - 6 - 16;
                int by = ry + 5;
                if (a > 0.05f) {
                    c.drawText(textRenderer, r.name, x + PAD, ty, argb(0x8A8A8A, a), false);
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
        }
    }

    // ---------- input ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double ux = lx(mx), uy = ly(my);
        int x = px(), y = py();

        if (page != Page.MAIN && button == 0 && inside(ux, uy, x, y, W, HEAD - 8)) {
            go(page.parent);
            return true;
        }

        List<Row> rows = pages.get(page);
        for (int i = 0; i < rows.size(); i++) {
            int ry = y + HEAD + i * ROW;
            if (!inside(ux, uy, x, ry, W, ROW)) continue;
            click(rows.get(i), x, ry, ux, uy, button);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click(Row r, int x, int ry, double ux, double uy, int button) {
        switch (r.kind) {
            case TOGGLE -> {
                if (button != 0) return;
                int sx = x + W - PAD - 28;
                if (r.link != null && inside(ux, uy, sx - 24, ry, 20, ROW)) go(r.link);
                else r.setB.accept(!r.getB.getAsBoolean());
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
            case LINK -> {
                if (button == 0) go(r.link);
            }
            case SIZE -> {
                if (button != 0) return;
                int plusX = x + W - PAD - 16;
                int minusX = plusX - 6 - 28 - 6 - 16;
                if (inside(ux, uy, minusX, ry + 5, 16, 16)) changeScale(-0.1f);
                else if (inside(ux, uy, plusX, ry + 5, 16, 16)) changeScale(0.1f);
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
            go(page.parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
