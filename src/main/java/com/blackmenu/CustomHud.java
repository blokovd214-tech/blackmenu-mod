package com.blackmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Dark glass HUD: hotbar, watermark, effects, module list, armor and target card. */
final class CustomHud {
    private static float selX = -1f;
    private static final float[] pop = new float[9];
    private static long lastNs;
    private static LivingEntity tgt;
    private static int tgtId = -1;
    private static float tgtFade, shownHp;

    private CustomHud() {}

    static void render(DrawContext c, MinecraftClient mc) {
        if (mc.player == null || mc.options.hudHidden) return;
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1.0e9f);
        lastNs = now;
        int w = c.getScaledWindowWidth(), h = c.getScaledWindowHeight();

        float fl = Atmos.flash();
        if (fl > 0.01f) c.fill(0, 0, w, h, Ui.argb(0xFFFFFF, fl * 0.3f));

        boolean spectator = mc.interactionManager != null
                && mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR;

        Px.begin(c, (int) Math.max(1, Math.round(Px.guiScale())), 0, 0);
        if (Cfg.hotbar && !spectator) hotbar(mc, w, h, dt);
        int y = 6;
        if (Cfg.infoPanel) y = watermark(mc, y);
        if (Cfg.effectsHud) effects(mc, y);
        if (Cfg.arraylist) arraylist(w);
        if (Cfg.armorHud) armor(mc, w, h);
        if (Cfg.targetHud) target(mc, w, h, dt);
        Px.end();
    }

    private static int accent(int i) {
        return Ui.rgb(Gfx.pal(Cfg.hudAccent, System.currentTimeMillis() / 6000.0 + i * 0.05));
    }

    private static void panel(double x, double y, double w, double h, double r) {
        float a = Cfg.hudAlpha;
        Px.rr(x, y, w, h, r, Ui.argb(0x07080D, 0.78f * a));
        Px.ring(x, y, w, h, r, Ui.argb(accent(0), 0.35f * a));
    }

    // ------------------------------------------------------------------ hotbar

    private static void hotbar(MinecraftClient mc, int w, int h, float dt) {
        PlayerInventory inv = mc.player.getInventory();
        int sel = inv.selectedSlot;
        int bw = 188, bh = 23;
        double x0 = (w - bw) / 2.0, y0 = h - bh;
        int ac = 0xFF000000 | accent(0);
        int bg = 0xFF07080D, border = 0xFF262630;
        double sy = y0 + 2;

        // offhand pod (also covers the vanilla offhand slot)
        ItemStack off = mc.player.getOffHandStack();
        if (!off.isEmpty()) {
            Px.rr(x0 - 29, y0 - 1, 32, bh + 7, 7, border);
            Px.rr(x0 - 28, y0, 31, bh + 7, 6, bg);
            double ox = x0 - 24;
            Px.rr(ox, sy, 18, 18, 5, 0xFF0E0E14);
            int bc = (Cfg.borders && Cfg.bordersHotbar) ? Borders.color(off) : -1;
            if (bc >= 0) Px.ring(ox, sy, 18, 18, 5, Ui.argb(bc, Cfg.bordersAlpha));
            Px.item(off, ox + 1, sy + 1, true);
        }

        // bar (also covers the vanilla hotbar; its bottom corners are below the screen edge)
        Px.rr(x0 - 1, y0 - 1, bw + 2, bh + 7, 7, border);
        Px.rr(x0, y0, bw, bh + 7, 6, bg);

        // selector glides to the selected slot
        float tx = (float) (x0 + 5 + sel * 20);
        if (selX < 0f || !Cfg.smoothSwap) selX = tx;
        else selX += (tx - selX) * (1f - (float) Math.exp(-dt * 18f * Cfg.swapSpeed));
        Px.rr(selX - 2, sy - 2, 22, 22, 7, Ui.argb(ac, 0.25f));
        Px.rr(selX - 1, sy - 1, 20, 20, 6, ac);

        float speed = Cfg.smoothSwap ? Cfg.swapSpeed : 50f;
        for (int i = 0; i < 9; i++) {
            double sx = x0 + 5 + i * 20;
            pop[i] += ((i == sel ? 1f : 0f) - pop[i]) * Math.min(1f, dt * 14f * speed);
            Px.rr(sx, sy, 18, 18, 5, 0xFF000000 | Ui.mix(0x0E0E14, 0x23232C, pop[i]));
            int bc = (Cfg.borders && Cfg.bordersHotbar) ? Borders.color(inv.getStack(i)) : -1;
            if (bc >= 0) Px.ring(sx, sy, 18, 18, 5, Ui.argb(bc, Cfg.bordersAlpha));
        }

        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            Px.item(st, x0 + 5 + i * 20 + 1, sy + 1 - Math.round(pop[i] * 2), true);
        }
    }

    // ------------------------------------------------------------------ watermark

    private static int watermark(MinecraftClient mc, int y) {
        List<String> parts = new ArrayList<>();
        if (Cfg.fps) parts.add(mc.getCurrentFps() + " fps");
        if (Cfg.ping) {
            PlayerListEntry e = mc.getNetworkHandler() == null ? null
                    : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            parts.add((e == null ? 0 : e.getLatency()) + " ms");
        }
        Text brand = Text.literal("BLACK").formatted(Formatting.BOLD);
        double x = 6, pad = 6;
        double bw = Px.tw(brand, 1);
        double total = pad + 8 + bw;
        for (String s : parts) total += 8 + Px.tw(s, 1);
        total += pad;

        panel(x, y, total, 15, 7);
        int ac = 0xFF000000 | accent(0);
        double cx = x + pad;
        Px.rr(cx, y + 5, 5, 5, 2.5, ac);
        cx += 8;
        Px.text(brand, cx, y + 4, ac, 1, false);
        cx += bw;
        for (String s : parts) {
            Px.rr(cx + 3.5, y + 4, 1, 7, 0.5, Ui.argb(0xFFFFFF, 0.22f));
            Px.text(s, cx + 8, y + 4, 0xFFEDEDF2, 1);
            cx += 8 + Px.tw(s, 1);
        }

        int ny = y + 17;
        if (Cfg.coords) {
            String xyz = mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
            double cw = pad + Px.tw("XYZ", 1) + 6 + Px.tw(xyz, 1) + pad;
            panel(x, ny, cw, 15, 7);
            Px.text("XYZ", x + pad, ny + 4, 0xFF7C7C8A, 1);
            Px.text(xyz, x + pad + Px.tw("XYZ", 1) + 6, ny + 4, 0xFFEDEDF2, 1);
            ny += 17;
        }
        return ny;
    }

    // ------------------------------------------------------------------ effects

    private static void effects(MinecraftClient mc, int y) {
        int i = 0;
        for (StatusEffectInstance e : mc.player.getStatusEffects()) {
            if (i >= 6) break;
            String name = e.getEffectType().value().getName().getString()
                    + (e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "");
            int sec = e.getDuration() / 20;
            String time = sec > 3600 ? "inf" : (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60);
            double w = 8 + Px.tw(name, 1) + 10 + Px.tw(time, 1) + 6;
            boolean good = e.getEffectType().value().isBeneficial();
            int col = good ? 0x55E08A : 0xFF5566;
            panel(6, y, w, 13, 6);
            Px.rr(9, y + 3, 2, 7, 1, 0xFF000000 | col);
            Px.text(name, 14, y + 3, 0xFFEDEDF2, 1);
            Px.text(time, 14 + Px.tw(name, 1) + 10, y + 3, 0xFF9A9AA8, 1);
            y += 15;
            i++;
        }
    }

    // ------------------------------------------------------------------ module list

    private static void arraylist(int w) {
        List<String> names = new ArrayList<>();
        for (Mods.Mod m : Mods.ALL) {
            if (m.on != null && m.on.getAsBoolean()) names.add(Mods.tr(m.name));
        }
        names.sort(Comparator.comparingDouble((String s) -> -Px.tw(s, 1)));
        float a = Cfg.hudAlpha;
        int y = 6, i = 0;
        for (String s : names) {
            double pw = Px.tw(s, 1) + 13;
            double x = w - 6 - pw;
            Px.rr(x, y, pw, 13, 5, Ui.argb(0x07080D, 0.7f * a));
            Px.rr(x + pw - 5, y + 3, 2, 7, 1, 0xFF000000 | accent(i));
            Px.text(s, x + 5, y + 3, 0xFFFFFFFF, 1);
            y += 15;
            i++;
        }
    }

    // ------------------------------------------------------------------ armor

    private static void armor(MinecraftClient mc, int w, int h) {
        PlayerInventory inv = mc.player.getInventory();
        boolean any = false;
        for (int i = 0; i < 4; i++) if (!inv.getArmorStack(i).isEmpty()) any = true;
        if (!any) return;

        double pw = 4 * 20 + 4, ph = 26;
        double x = w - 6 - pw, y = h - 6 - ph;
        panel(x, y, pw, ph, 7);
        for (int i = 0; i < 4; i++) {
            ItemStack st = inv.getArmorStack(3 - i);
            double sx = x + 4 + i * 20, sy = y + 3;
            Px.rr(sx, sy, 18, 18, 5, 0xFF0E0E14);
            if (st.isEmpty()) continue;
            Px.item(st, sx + 1, sy + 1, false);
            if (st.isDamageable()) {
                double ratio = Math.max(0, 1.0 - st.getDamage() / (double) st.getMaxDamage());
                Px.rr(sx + 2, y + 22, 14, 2, 1, 0xFF1C1C24);
                Px.rr(sx + 2, y + 22, Math.max(1, 14 * ratio), 2, 1,
                        0xFF000000 | Ui.mix(0xFF4455, 0x55E08A, (float) ratio));
            }
        }
    }

    // ------------------------------------------------------------------ target card

    private static void target(MinecraftClient mc, int w, int h, float dt) {
        Entity t = mc.targetedEntity;
        if (t instanceof LivingEntity le) {
            if (le.getId() != tgtId) {
                tgtId = le.getId();
                shownHp = le.getHealth();
            }
            tgt = le;
            tgtFade = Math.min(1f, tgtFade + dt * 6f);
        } else {
            tgtFade = Math.max(0f, tgtFade - dt * 4f);
        }
        if (tgt == null || tgtFade < 0.02f) return;

        float hp = Math.max(0, tgt.getHealth()), max = Math.max(1, tgt.getMaxHealth());
        shownHp += (hp - shownHp) * Math.min(1f, dt * 8f);
        float a = tgtFade;
        String name = tgt.getName().getString();
        String hpTxt = String.format("%.1f", hp);

        double pw = Math.max(96, Px.tw(name, 1) + 16), ph = 28;
        double x = w / 2.0 + 20, y = h / 2.0 + 18 + (1 - a) * 4;
        float al = Cfg.hudAlpha * a;
        Px.rr(x, y, pw, ph, 7, Ui.argb(0x07080D, 0.8f * al));
        Px.ring(x, y, pw, ph, 7, Ui.argb(accent(0), 0.4f * al));
        Px.text(name, x + 7, y + 5, Ui.argb(0xFFFFFF, a), 1);

        double by = y + 17, bw = pw - 14 - Px.tw(hpTxt, 1) - 4;
        double fr = Math.max(0, Math.min(1, shownHp / max));
        Px.rr(x + 7, by, bw, 4, 2, Ui.argb(0x22222B, a));
        Px.rr(x + 7, by, Math.max(2, bw * fr), 4, 2, Ui.argb(Ui.mix(0xFF4455, 0x55E08A, (float) fr), a));
        Px.text(hpTxt, x + 7 + bw + 4, by - 2, Ui.argb(0xDDDDDD, a), 1);
    }
}
