package com.blackmenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import org.lwjgl.glfw.GLFW;

public class BlackMenuClient implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Cfg.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blackmenu.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.blackmenu"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (Cfg.titleChains && mc.currentScreen instanceof TitleScreen) {
                mc.setScreen(Cfg.titleStyle == 0 ? new HelixTitleScreen() : new ChainTitleScreen());
            }

            while (openKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new BlackMenuScreen());
            }

            if (Cfg.fullbright && mc.player != null) {
                StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
                if (cur == null || cur.getDuration() < 300) {
                    mc.player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.NIGHT_VISION, 600, 0, false, false, false));
                }
            }

            Perf.tick(mc);
            Fx.applyTime(mc);
            NoRender.frame(mc);
            NoRender.tick(mc);
            Weather.frame(mc);
            Weather.tick(mc);
            Fx.FLIES.tick(mc);
            Fx.DUST.tick(mc);
            KillFx.tick(mc);
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> CustomHud.render(ctx, MinecraftClient.getInstance()));

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof HandledScreen<?> hs && !(screen instanceof CreativeInventoryScreen)) {
                ScreenEvents.afterRender(screen).register((s, ctx, mx, my, d) -> Borders.container(hs, ctx));
            }
        });

        WorldRenderEvents.START.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            Fx.applyTime(mc);
            NoRender.frame(mc);
            Weather.frame(mc);
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(Fx::render);
    }

    public static void setFullbright(boolean on) {
        Cfg.fullbright = on;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!on && mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
    }
}
