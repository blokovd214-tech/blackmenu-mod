package com.blackmenu;

import net.minecraft.client.MinecraftClient;

/** Effect quality: a fixed preset plus an automatic factor that backs off when the frame rate drops. */
final class Perf {
    static final float[] BASE = {1f, 0.8f, 0.55f, 0.35f};
    private static float adapt = 1f;

    private Perf() {}

    /** 0.2 .. 1 multiplier for particle counts, draw distances and detail. */
    static float q() {
        float b = BASE[Math.floorMod(Cfg.quality, BASE.length)];
        return Cfg.adaptive ? Math.max(0.2f, b * adapt) : b;
    }

    static void tick(MinecraftClient mc) {
        int fps = mc.getCurrentFps();
        if (fps <= 0) return;
        if (fps < 40) adapt = Math.max(0.35f, adapt - 0.02f);
        else if (fps > 75) adapt = Math.min(1f, adapt + 0.01f);
    }
}
