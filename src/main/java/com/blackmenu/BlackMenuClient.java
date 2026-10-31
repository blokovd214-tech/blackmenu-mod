package com.blackmenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;

public class BlackMenuClient implements ClientModInitializer {
    public static boolean fullbright, fps, coords, targetEsp, atmosphere;
    public static float menuScale = 1.0f;
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blackmenu.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.blackmenu"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new BlackMenuScreen());
            }

            if (fullbright && mc.player != null) {
                StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
                if (cur == null || cur.getDuration() < 300) {
                    mc.player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.NIGHT_VISION, 600, 0, false, false, false));
                }
            }

            if (atmosphere && mc.world != null && mc.player != null) {
                // client-side only: golden sunset sky + soft floating glow particles
                mc.world.setTimeOfDay(12800L);
                Random r = mc.world.getRandom();
                for (int i = 0; i < 2; i++) {
                    double x = mc.player.getX() + (r.nextDouble() - 0.5) * 24;
                    double y = mc.player.getEyeY() + (r.nextDouble() - 0.4) * 8;
                    double z = mc.player.getZ() + (r.nextDouble() - 0.5) * 24;
                    mc.world.addParticle(ParticleTypes.GLOW, x, y, z,
                            (r.nextDouble() - 0.5) * 0.02, 0.01 + r.nextDouble() * 0.01, (r.nextDouble() - 0.5) * 0.02);
                }
            }
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            int y = 6;
            if (fps) {
                ctx.drawTextWithShadow(mc.textRenderer, mc.getCurrentFps() + " FPS", 6, y, 0xFFFFFFFF);
                y += 11;
            }
            if (coords) {
                ctx.drawTextWithShadow(mc.textRenderer,
                        mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ(),
                        6, y, 0xFFFFFFFF);
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(TargetEsp::render);
    }

    public static void setFullbright(boolean on) {
        fullbright = on;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!on && mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
    }
}
