package com.blackmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

/** Black rounded hotbar with a gliding selector, plus an info panel. */
final class CustomHud {
    private static float selX = -1f;
    private static final float[] pop = new float[9];
    private static long lastNs;

    private CustomHud() {}

    static void render(DrawContext c, MinecraftClient mc) {
        if (mc.player == null || mc.options.hudHidden) return;
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1.0e9f);
        lastNs = now;

        boolean spectator = mc.interactionManager != null
                && mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR;
        if (Cfg.hotbar && !spectator) hotbar(c, mc, dt);
        if (Cfg.infoPanel) info(c, mc);
    }

    private static int accent() {
        return Ui.rgb(Gfx.pal(Cfg.hudAccent, System.currentTimeMillis() / 6000.0));
    }

    private static void hotbar(DrawContext c, MinecraftClient mc, float dt) {
        int w = c.getScaledWindowWidth(), h = c.getScaledWindowHeight();
        PlayerInventory inv = mc.player.getInventory();
        int sel = inv.selectedSlot;

        int bw = 188, bh = 23;
        int x0 = (w - bw) / 2, y0 = h - bh;
        int accent = accent();
        int bg = 0xFF050505, border = 0xFF262626;
        int sy = y0 + 2;

        // offhand pod (it also covers the vanilla offhand slot)
        ItemStack off = mc.player.getOffHandStack();
        if (!off.isEmpty()) {
            Ui.rrect(c, x0 - 29, y0 - 1, 32, bh + 7, 7, border);
            Ui.rrect(c, x0 - 28, y0, 31, bh + 7, 6, bg);
            int ox = x0 - 24;
            Ui.rrect(c, ox, sy, 18, 18, 5, 0xFF0E0E0E);
            int bc = (Cfg.borders && Cfg.bordersHotbar) ? Borders.color(off) : -1;
            if (bc >= 0) Ui.ring(c, ox, sy, 18, 18, Ui.argb(bc, Cfg.bordersAlpha));
            c.drawItem(off, ox + 1, sy + 1);
            c.drawItemInSlot(mc.textRenderer, off, ox + 1, sy + 1);
        }

        // bar (also covers the vanilla hotbar; its bottom corners are below the screen edge)
        Ui.rrect(c, x0 - 1, y0 - 1, bw + 2, bh + 7, 7, border);
        Ui.rrect(c, x0, y0, bw, bh + 7, 6, bg);

        float speed = Cfg.smoothSwap ? Cfg.swapSpeed : 50f;
        for (int i = 0; i < 9; i++) {
            int sx = x0 + 5 + i * 20;
            pop[i] += ((i == sel ? 1f : 0f) - pop[i]) * Math.min(1f, dt * 14f * speed);
            Ui.rrect(c, sx, sy, 18, 18, 5, 0xFF000000 | Ui.mix(0x0E0E0E, 0x222222, pop[i]));
            int bc = (Cfg.borders && Cfg.bordersHotbar) ? Borders.color(inv.getStack(i)) : -1;
            if (bc >= 0) Ui.ring(c, sx, sy, 18, 18, Ui.argb(bc, Cfg.bordersAlpha));
        }

        // selector glides to the selected slot
        float tx = x0 + 5 + sel * 20;
        if (selX < 0f || !Cfg.smoothSwap) selX = tx;
        else selX += (tx - selX) * (1f - (float) Math.exp(-dt * 18f * Cfg.swapSpeed));
        int ssx = Math.round(selX);
        Ui.ring(c, ssx - 2, sy - 2, 22, 22, Ui.argb(accent, 0.3f));
        Ui.ring(c, ssx - 1, sy - 1, 20, 20, 0xFF000000 | accent);

        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            int ix = x0 + 5 + i * 20 + 1;
            int iy = sy + 1 - Math.round(pop[i] * 2);
            c.drawItem(st, ix, iy);
            c.drawItemInSlot(mc.textRenderer, st, ix, iy);
        }
    }

    private static void info(DrawContext c, MinecraftClient mc) {
        List<String[]> lines = new ArrayList<>();
        if (Cfg.fps) lines.add(new String[]{"FPS", String.valueOf(mc.getCurrentFps())});
        if (Cfg.coords) {
            lines.add(new String[]{"XYZ", mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ()});
        }
        if (Cfg.ping) {
            PlayerListEntry e = mc.getNetworkHandler() == null ? null
                    : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            lines.add(new String[]{"PING", (e == null ? 0 : e.getLatency()) + " ms"});
        }
        if (lines.isEmpty()) return;

        int maxVal = 0;
        for (String[] l : lines) maxVal = Math.max(maxVal, mc.textRenderer.getWidth(l[1]));
        int labelW = 28;
        int w = 12 + labelW + maxVal + 8, h = 8 + lines.size() * 11;
        int x = 6, y = 6;
        float a = Cfg.hudAlpha;
        int accent = accent();

        Ui.rrect(c, x - 1, y - 1, w + 2, h + 2, 7, Ui.argb(0x262626, a));
        Ui.rrect(c, x, y, w, h, 6, Ui.argb(0x050505, a));
        Ui.rrect(c, x + 4, y + 5, 2, h - 10, 1, 0xFF000000 | accent);
        for (int i = 0; i < lines.size(); i++) {
            int ty = y + 5 + i * 11;
            c.drawText(mc.textRenderer, lines.get(i)[0], x + 11, ty, Ui.argb(0x777777, 1f), false);
            c.drawText(mc.textRenderer, lines.get(i)[1], x + 11 + labelW, ty, 0xFFFFFFFF, false);
        }
    }
}
