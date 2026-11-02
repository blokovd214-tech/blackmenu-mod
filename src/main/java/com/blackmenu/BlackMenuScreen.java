package com.blackmenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

import static com.blackmenu.Ui.argb;
import static com.blackmenu.Ui.inside;
import static com.blackmenu.Ui.mix;

/**
 * Sidebar with categories, search, and three columns of module cards.
 * Drawn in whole-pixel units (see Px), so every edge is sharp and every corner smooth.
 */
public class BlackMenuScreen extends Screen {
    private static final int W = 520, H = 290, SIDE = 118, R = 10, GAP = 8;
    private static final int HEAD_H = 24, BOOL_H = 24, SLIDER_H = 30, MODE_H = 34, PAD_B = 6;
    private static final Mods.Cat[] SIDE_CATS = {null, Mods.Cat.HUD, Mods.Cat.VISUAL, Mods.Cat.PVP, Mods.Cat.COSMETICS};

    private static final class Hit {
        final int x, y, w, h;
        final boolean clipped;
        final Runnable click;
        final DoubleConsumer drag;
        final Mods.Set set;

        Hit(int x, int y, int w, int h, boolean clipped, Runnable click, DoubleConsumer drag, Mods.Set set) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.clipped = clipped; this.click = click; this.drag = drag; this.set = set;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private Mods.Cat cat = null;               // null = all
    private String query = "";
    private boolean searchFocus, favOnly;
    private float scroll, scrollTarget, maxScroll;
    private Hit dragHit;
    private Mods.Set dragSet;
    private Mods.Set ddSet;
    private int ddX, ddY, ddW;
    private int cy0, cy1;
    private int U = 3, OX, OY;                  // real pixels per unit, origin in real pixels

    private final long opened = System.currentTimeMillis();
    private long last = opened;

    public BlackMenuScreen() {
        super(Text.literal("Black Menu"));
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

    private void add(int x, int y, int w, int h, boolean clipped, Runnable click, DoubleConsumer drag, Mods.Set set) {
        hits.add(new Hit(x, y, w, h, clipped, click, drag, set));
    }

    private float progress() {
        float t = Math.min(1f, (System.currentTimeMillis() - opened) / 200f);
        return 1f - (1f - t) * (1f - t);
    }

    /** Picks a whole number of pixels per unit so the menu fits the window and stays sharp. */
    private void layout() {
        double gs = Px.guiScale();
        double fbW = width * gs, fbH = height * gs;
        int maxU = (int) Math.floor(Math.min((fbH - 16) / H, (fbW - 16) / W));
        int want = (int) Math.round(Cfg.menuScale * gs);
        U = Math.max(1, Math.min(want, maxU));
        OX = (int) Math.round((fbW - (double) W * U) / 2.0);
        OY = (int) Math.round((fbH - (double) H * U) / 2.0) + (int) ((1f - progress()) * 8 * U);
    }

    private double mu(double mx) {
        return (mx * Px.guiScale() - OX) / U;
    }

    private double mv(double my) {
        return (my * Px.guiScale() - OY) / U;
    }

    private static void rrect(int x, int y, int w, int h, int r, int color) {
        Px.rr(x, y, w, h, r, color);
    }

    private static void fill(int x1, int y1, int x2, int y2, int color) {
        Px.fill(x1, y1, x2, y2, color);
    }

    private static void text(String s, int x, int y, int color) {
        Px.text(s, x, y, color, 1);
    }

    private static void small(String s, int x, int y, int color, double sc) {
        Px.text(s, x, y, color, sc);
    }

    private List<Mods.Mod> visible() {
        List<Mods.Mod> out = new ArrayList<>();
        String q = query.toLowerCase();
        for (Mods.Mod m : Mods.ALL) {
            if (!q.isEmpty()) {
                if (!m.name.toLowerCase().contains(q)) continue;
            } else if (cat == null ? m.cat == Mods.Cat.SETTINGS : m.cat != cat) {
                continue;
            }
            if (favOnly && !Mods.isFav(m)) continue;
            out.add(m);
        }
        return out;
    }

    private static int setH(Mods.Set s) {
        return switch (s.kind) {
            case BOOL -> BOOL_H;
            case SLIDER -> SLIDER_H;
            case MODE -> MODE_H;
        };
    }

    private static int cardH(Mods.Mod m) {
        int h = HEAD_H + PAD_B;
        for (Mods.Set s : m.sets) h += setH(s);
        return h;
    }

    private void setSlider(Mods.Set s, double ux, int bx, int bw) {
        double f = Math.max(0, Math.min(1, (ux - bx) / bw));
        float v = (float) (s.min + f * (s.max - s.min));
        v = s.max >= 10 ? Math.round(v) : Math.round(v * 10f) / 10f;
        if (s.deferred) s.pending = v;
        else s.setF.accept(v);
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
        hits.clear();
        layout();
        renderBackground(c, mx, my, delta);

        float a = progress();
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;
        double ux = mu(mx), uy = mv(my);

        Px.begin(c, U, OX, OY);
        rrect(-1, -1, W + 2, H + 2, R + 1, argb(0x1C1C24, a));
        rrect(0, 0, W, H, R, argb(0x07070B, a));

        drawSidebar(a, ux, uy);
        drawTop(a, ux, uy);
        drawCards(c, a, dt, ux, uy);
        drawDropdown(a, ux, uy);
        Px.end();
    }

    private void sideItem(int ry, String label, boolean selected, float a, double ux, double uy, Runnable click) {
        boolean hv = inside(ux, uy, 8, ry, SIDE - 16, 20);
        if (selected) rrect(8, ry, SIDE - 16, 20, 7, argb(0x14141B, a));
        else if (hv) rrect(8, ry, SIDE - 16, 20, 7, argb(0x0D0D12, a));
        if (selected) rrect(8, ry + 5, 2, 10, 1, argb(0xFFFFFF, a));
        text(Mods.tr(label), 18, ry + 6, argb(selected ? 0xFFFFFF : (hv ? 0xCCCCCC : 0x777782), a));
        add(8, ry, SIDE - 16, 20, false, click, null, null);
    }

    private void drawSidebar(float a, double ux, double uy) {
        rrect(0, 0, SIDE, H, R, argb(0x040407, a));
        fill(SIDE - R, 0, SIDE, H, argb(0x040407, a));
        fill(SIDE, 8, SIDE + 1, H - 8, argb(0x15151C, a));

        rrect(12, 12, 12, 12, 4, argb(0xFFFFFF, a));
        rrect(17, 14, 2, 8, 1, argb(0x000000, a));
        rrect(14, 17, 8, 2, 1, argb(0x000000, a));
        Px.text(Text.literal("BLACK").formatted(Formatting.BOLD), 30, 14, argb(0xFFFFFF, a), 1, false);
        small(Mods.tr("Categories|Категории"), 12, 38, argb(0x555560, a), 0.75);

        for (int i = 0; i < SIDE_CATS.length; i++) {
            final Mods.Cat k = SIDE_CATS[i];
            sideItem(50 + i * 22, k == null ? "All|Все" : k.name, cat == k, a, ux, uy, () -> {
                cat = k;
                scroll = scrollTarget = 0;
            });
        }

        sideItem(H - 62, Mods.Cat.SETTINGS.name, cat == Mods.Cat.SETTINGS, a, ux, uy, () -> {
            cat = Mods.Cat.SETTINGS;
            scroll = scrollTarget = 0;
        });

        fill(12, H - 34, SIDE - 12, H - 33, argb(0x15151C, a));
        String user = client != null ? client.getSession().getUsername() : "";
        text(Px.trim(user, SIDE - 24, 1), 12, H - 28, argb(0xDDDDDD, a));
        small("Black Menu 1.21.1", 12, H - 16, argb(0x555560, a), 0.75);
    }

    private void drawTop(float a, double ux, double uy) {
        int cx0 = SIDE + 8, cx1 = W - 8;
        String title = !query.isEmpty() ? Mods.tr("Search|Поиск") : (cat == null ? Mods.tr("All|Все") : Mods.tr(cat.name));
        Px.text(Text.literal(title).formatted(Formatting.BOLD), cx0 + 2, 14, argb(0xFFFFFF, a), 1, false);

        int sw = 150, sx = cx1 - 48 - 8 - sw;
        rrect(sx - 1, 7, sw + 2, 20, 7, argb(searchFocus ? 0x6A6A78 : 0x1A1A22, a));
        rrect(sx, 8, sw, 18, 6, argb(0x0D0D12, a));
        if (query.isEmpty() && !searchFocus) {
            text(Mods.tr("Search modules...|Поиск модулей..."), sx + 8, 14, argb(0x555560, a));
        } else {
            String t = query + (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
            text(Px.trim(t, sw - 14, 1), sx + 8, 14, argb(0xFFFFFF, a));
        }
        add(sx, 8, sw, 18, false, () -> searchFocus = true, null, null);

        int fx = cx1 - 48;
        boolean fh = inside(ux, uy, fx, 8, 14, 18);
        small("*", fx + 2, 10, argb(favOnly ? 0xFFC94A : (fh ? 0xAAAAAA : 0x555560), a), 1.8);
        add(fx, 8, 14, 18, false, () -> {
            favOnly = !favOnly;
            scroll = scrollTarget = 0;
        }, null, null);

        int lx = cx1 - 28;
        boolean lh = inside(ux, uy, lx, 8, 28, 18);
        rrect(lx, 8, 28, 18, 6, argb(lh ? 0x1E1E28 : 0x101016, a));
        String l = Cfg.lang == 0 ? "RU" : "EN";
        text(l, lx + (int) ((28 - Px.tw(l, 1)) / 2), 14, argb(0xFFFFFF, a));
        add(lx, 8, 28, 18, false, () -> Cfg.lang = Cfg.lang == 0 ? 1 : 0, null, null);

        fill(cx0, 34, cx1, 35, argb(0x15151C, a));
    }

    private void drawCards(DrawContext c, float a, float dt, double ux, double uy) {
        int cx0 = SIDE + 8, cx1 = W - 8;
        cy0 = 40;
        cy1 = H - 8;
        int colW = (cx1 - cx0 - 2 * GAP) / 3;

        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 14f);
        int off = Math.round(scroll);

        // clip to the card area (scissor wants GUI units, so leave pixel mode for a moment)
        double gs = Px.guiScale();
        int gx1 = (int) Math.floor((OX + (cx0 - 2) * U) / gs), gx2 = (int) Math.ceil((OX + (cx1 + 2) * U) / gs);
        int gy1 = (int) Math.floor((OY + cy0 * U) / gs), gy2 = (int) Math.ceil((OY + cy1 * U) / gs);
        Px.suspend();
        c.enableScissor(gx1, gy1, gx2, gy2);
        Px.resume();

        List<Mods.Mod> list = visible();
        int[] colY = new int[3];
        for (Mods.Mod m : list) {
            int col = 0;
            for (int k = 1; k < 3; k++) if (colY[k] < colY[col]) col = k;
            int cx = cx0 + col * (colW + GAP);
            int cyy = cy0 - off + colY[col];
            int ch = cardH(m);
            if (cyy + ch >= cy0 && cyy <= cy1) drawCard(m, cx, cyy, colW, ch, a, dt, ux, uy);
            colY[col] += ch + GAP;
        }
        int total = Math.max(colY[0], Math.max(colY[1], colY[2])) - GAP;
        maxScroll = Math.max(0, total - (cy1 - cy0));

        Px.suspend();
        c.disableScissor();
        Px.resume();

        if (list.isEmpty()) {
            String t = Mods.tr("Nothing found|Ничего не найдено");
            text(t, cx0 + (int) ((cx1 - cx0 - Px.tw(t, 1)) / 2), cy0 + 40, argb(0x555560, a));
        }
    }

    private void drawCard(Mods.Mod m, int x, int y, int w, int h, float a, float dt, double ux, double uy) {
        rrect(x - 1, y - 1, w + 2, h + 2, 9, argb(0x17171E, a));
        rrect(x, y, w, h, 8, argb(0x0C0C11, a));

        boolean hasToggle = m.on != null;
        boolean on = hasToggle && m.on.getAsBoolean();
        if (hasToggle) m.anim += ((on ? 1f : 0f) - m.anim) * Math.min(1f, dt * 16f);
        else m.anim = 1f;

        int tx = x + w - 8 - 22;
        int starX = (hasToggle ? tx : x + w - 8) - 14;
        String nm = Px.trim(Mods.tr(m.name), starX - (x + 8) - 2, 1);
        text(nm, x + 8, y + 8, argb(mix(0x9A9AA5, 0xFFFFFF, m.anim), a));
        small("*", starX + 2, y + 6, argb(Mods.isFav(m) ? 0xFFC94A : 0x444450, a), 1.5);

        if (hasToggle) {
            rrect(tx - 1, y + 5, 24, 14, 7, argb(mix(0x2A2A32, 0xFFFFFF, m.anim), a));
            rrect(tx, y + 6, 22, 12, 6, argb(mix(0x0F0F14, 0xFFFFFF, m.anim), a));
            rrect(tx + 2 + (int) (10 * m.anim), y + 8, 8, 8, 4, argb(mix(0x5A5A66, 0x000000, m.anim), a));
            add(x + 4, y + 2, Math.max(8, starX - x - 6), 20, true, () -> m.setOn.accept(!m.on.getAsBoolean()), null, null);
            add(tx - 2, y + 4, 26, 16, true, () -> m.setOn.accept(!m.on.getAsBoolean()), null, null);
        }
        add(starX, y + 4, 12, 16, true, () -> Mods.toggleFav(m), null, null);

        int sy = y + HEAD_H;
        for (Mods.Set s : m.sets) {
            drawSet(s, x, sy, w, a, dt, ux, uy);
            sy += setH(s);
        }
    }

    private void drawSet(Mods.Set s, int x, int y, int w, float a, float dt, double ux, double uy) {
        String nm = Mods.tr(s.name), ds = Mods.tr(s.desc);
        int lx = x + 8, rw = w - 16;
        int nameMax = rw - 26;

        switch (s.kind) {
            case BOOL -> {
                boolean v = s.getB.getAsBoolean();
                s.anim += ((v ? 1f : 0f) - s.anim) * Math.min(1f, dt * 16f);
                int bx = x + w - 8 - 12, by = y + 5;
                rrect(bx - 1, by - 1, 14, 14, 5, argb(mix(0x2A2A32, 0xFFFFFF, s.anim), a));
                rrect(bx, by, 12, 12, 4, argb(mix(0x0F0F14, 0xFFFFFF, s.anim), a));
                if (s.anim > 0.5f) {
                    int k = argb(0x000000, a);
                    rrect(bx + 3, by + 6, 2, 2, 1, k);
                    rrect(bx + 4, by + 7, 2, 2, 1, k);
                    rrect(bx + 5, by + 6, 2, 2, 1, k);
                    rrect(bx + 6, by + 5, 2, 2, 1, k);
                    rrect(bx + 7, by + 4, 2, 2, 1, k);
                    rrect(bx + 8, by + 3, 2, 2, 1, k);
                }
                text(Px.trim(nm, nameMax, 1), lx, y + 3, argb(mix(0x8A8A95, 0xFFFFFF, s.anim), a));
                small(Px.trim(ds, nameMax, 0.7), lx, y + 13, argb(0x555560, a), 0.7);
                add(x + 2, y, w - 4, BOOL_H, true, () -> s.setB.accept(!s.getB.getAsBoolean()), null, null);
            }
            case SLIDER -> {
                float v = s.pending != null ? s.pending : s.getF.get();
                float f = Math.max(0f, Math.min(1f, (v - s.min) / (s.max - s.min)));
                String val = s.max >= 10 ? String.format("%.0f", v) : String.format("%.1f", v);
                int bx = x + 8, bw = w - 16, by = y + 23;
                text(Px.trim(nm, nameMax - 6, 1), lx, y + 3, argb(0xCCCCD5, a));
                text(val, x + w - 8 - (int) Math.ceil(Px.tw(val, 1)), y + 3, argb(0xFFFFFF, a));
                small(Px.trim(ds, nameMax, 0.7), lx, y + 13, argb(0x555560, a), 0.7);
                rrect(bx, by, bw, 3, 1, argb(0x20202A, a));
                rrect(bx, by, Math.max(3, (int) (bw * f)), 3, 1, argb(0xFFFFFF, a));
                rrect(bx + (int) ((bw - 7) * f), by - 2, 7, 7, 3, argb(0xFFFFFF, a));
                add(bx - 3, y + 17, bw + 6, 13, true, null, ux2 -> setSlider(s, ux2, bx, bw), s);
            }
            case MODE -> {
                String cur = Mods.tr(s.opts[Math.floorMod(s.getI.getAsInt(), s.opts.length)]);
                int bx = x + 8, bw = w - 16, by = y + 19;
                boolean hv = inside(ux, uy, bx, by, bw, 13);
                text(Px.trim(nm, nameMax + 20, 1), lx, y + 3, argb(0xCCCCD5, a));
                small(Px.trim(ds, rw, 0.7), lx, y + 12, argb(0x555560, a), 0.7);
                rrect(bx, by, bw, 13, 5, argb(hv || ddSet == s ? 0x20202A : 0x15151B, a));
                text(Px.trim(cur, bw - 18, 1), bx + 5, by + 3, argb(0xFFFFFF, a));
                text("v", bx + bw - 10, by + 3, argb(0x666672, a));
                if (ddSet == s) {
                    ddX = bx;
                    ddY = by + 14;
                    ddW = bw;
                }
                add(bx, by, bw, 13, true, () -> ddSet = (ddSet == s ? null : s), null, null);
            }
        }
    }

    private void drawDropdown(float a, double ux, double uy) {
        if (ddSet == null) return;
        Mods.Set s = ddSet;
        int n = s.opts.length, oh = 13;
        int total = n * oh + 4;
        int dy = ddY;
        if (dy + total > H - 4) dy = ddY - 14 - total;

        rrect(ddX - 1, dy - 1, ddW + 2, total + 2, 7, argb(0x2A2A34, a));
        rrect(ddX, dy, ddW, total, 6, argb(0x101016, a));
        add(ddX, dy, ddW, total, false, () -> { }, null, null);

        int cur = Math.floorMod(s.getI.getAsInt(), n);
        for (int i = 0; i < n; i++) {
            int oy = dy + 2 + i * oh;
            boolean hv = inside(ux, uy, ddX + 2, oy, ddW - 4, oh);
            if (hv) rrect(ddX + 2, oy, ddW - 4, oh, 5, argb(0x1E1E28, a));
            String t = Px.trim(Mods.tr(s.opts[i]), ddW - 12, 1);
            text(t, ddX + 6, oy + 3, argb(i == cur ? 0xFFFFFF : (hv ? 0xDDDDDD : 0x8A8A95), a));
            final int idx = i;
            add(ddX + 2, oy, ddW - 4, oh, false, () -> {
                s.setI.accept(idx);
                ddSet = null;
            }, null, null);
        }
    }

    // ---------- input ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double ux = mu(mx), uy = mv(my);
        Mods.Set open = ddSet;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!inside(ux, uy, h.x, h.y, h.w, h.h)) continue;
            if (h.clipped && (uy < cy0 || uy >= cy1)) continue;
            searchFocus = false;
            if (button == 0) {
                if (h.drag != null) {
                    dragHit = h;
                    dragSet = h.set;
                    h.drag.accept(ux);
                }
                if (h.click != null) h.click.run();
            }
            if (open != null && ddSet == open) ddSet = null;
            return true;
        }
        searchFocus = false;
        if (open != null) ddSet = null;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragHit != null && dragHit.drag != null) {
            dragHit.drag.accept(mu(mx));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragSet != null && dragSet.pending != null) {
            dragSet.setF.accept(dragSet.pending);
            dragSet.pending = null;
        }
        dragHit = null;
        dragSet = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (hasControlDown()) {
            float v = Math.max(0.6f, Math.min(1.5f, Cfg.menuScale + (float) (vertical * 0.05)));
            Cfg.menuScale = Math.round(v * 20f) / 20f;
        } else {
            scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) vertical * 28f));
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocus && chr >= 32 && chr != 127 && query.length() < 20) {
            query += chr;
            scroll = scrollTarget = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (ddSet != null) { ddSet = null; return true; }
            if (searchFocus) { searchFocus = false; return true; }
        }
        if (searchFocus) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                scroll = scrollTarget = 0;
            } else if (keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocus = false;
            }
            return true;   // keep typing from triggering other shortcuts
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
