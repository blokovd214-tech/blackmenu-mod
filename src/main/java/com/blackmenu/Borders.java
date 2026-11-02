package com.blackmenu;

import com.blackmenu.mixin.HandledScreenAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

/** Rarity colored borders around items (inventories + hotbar). */
final class Borders {
    private Borders() {}

    /** RGB color for the stack, or -1 for no border. */
    static int color(ItemStack st) {
        if (st.isEmpty()) return -1;
        int col = switch (st.getRarity()) {
            case COMMON -> Cfg.bordersHideCommon ? -1 : 0xAAAAAA;
            case UNCOMMON -> 0xFFFF55;
            case RARE -> 0x55FFFF;
            case EPIC -> 0xFF55FF;
            default -> -1;
        };
        if (col < 0 && st.hasGlint()) col = 0xB27CFF;
        return col;
    }

    static void container(HandledScreen<?> hs, DrawContext c) {
        if (!Cfg.borders || !Cfg.bordersInv) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) hs;
        int ox = acc.bm_getX(), oy = acc.bm_getY();

        c.getMatrices().push();
        c.getMatrices().translate(0f, 0f, 190f);   // above items, below tooltips
        Px.begin(c, (int) Math.max(1, Math.round(Px.guiScale())), 0, 0);
        for (Slot s : hs.getScreenHandler().slots) {
            if (!s.isEnabled()) continue;
            int col = color(s.getStack());
            if (col < 0) continue;
            Px.ring(ox + s.x - 2, oy + s.y - 2, 20, 20, 6, Ui.argb(col, Cfg.bordersAlpha * 0.3f));
            Px.ring(ox + s.x - 1, oy + s.y - 1, 18, 18, 5, Ui.argb(col, Cfg.bordersAlpha));
        }
        Px.end();
        c.getMatrices().pop();
    }
}
