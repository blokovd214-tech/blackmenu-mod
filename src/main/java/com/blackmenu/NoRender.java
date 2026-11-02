package com.blackmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;

import java.util.ArrayList;
import java.util.List;

/** Removes visual noise: hurt camera shake, fire, and unwanted entities (client side only). */
final class NoRender {
    private NoRender() {}

    /** Every frame and every tick, before the world is drawn. */
    static void frame(MinecraftClient mc) {
        if (!Cfg.nr || mc.player == null || mc.world == null) return;
        if (Cfg.nrHurt) mc.player.hurtTime = 0;          // no camera tilt when you get hit
        if (Cfg.nrFire) {
            mc.player.setOnFire(false);                  // no fire on the screen
            for (Entity e : mc.world.getEntities()) {
                if (e.isOnFire()) e.setOnFire(false);    // no fire on other entities
            }
        }
    }

    static void tick(MinecraftClient mc) {
        if (!Cfg.nr || mc.world == null) return;
        List<Integer> remove = null;
        for (Entity e : mc.world.getEntities()) {
            boolean rm = (Cfg.nrItems && e instanceof ItemEntity)
                    || (Cfg.nrXp && e instanceof ExperienceOrbEntity)
                    || (Cfg.nrStands && e instanceof ArmorStandEntity)
                    || (Cfg.nrLightning && e instanceof LightningEntity);
            if (rm) {
                if (remove == null) remove = new ArrayList<>();
                remove.add(e.getId());
            }
        }
        if (remove != null) {
            for (int id : remove) mc.world.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }
    }
}
