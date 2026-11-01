package com.blackmenu;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** All settings. Saved to config/blackmenu.properties when the menu closes. */
public final class Cfg {
    // general
    public static boolean fullbright, titleChains = true;
    public static float menuScale = 1f;

    // blur+
    public static boolean blurMenu = true;
    public static float blurDim = 0.3f;

    // hud
    public static boolean hotbar = true, infoPanel = true, fps = true, coords, ping;
    public static int hudAccent = 6;
    public static float hudAlpha = 0.85f;
    public static boolean smoothSwap = true;
    public static float swapSpeed = 1f;

    // item borders
    public static boolean borders = true, bordersInv = true, bordersHotbar = true, bordersHideCommon = true;
    public static float bordersAlpha = 0.9f;

    // hitboxes
    public static boolean hb, hbPlayers = true, hbMobs = true, hbOthers, hbFill = true, hbEye = true;
    public static int hbColor = 0;
    public static float hbWidth = 1f, hbAlpha = 1f;

    // target esp
    public static boolean esp, espLightning = true, espCircle = true, espChains, espOrbs;
    public static int espColor = 0;
    public static float espSize = 1f, espSpeed = 1f, espAlpha = 1f;

    // atmosphere
    public static boolean atmo, aurora = true, flies = true;
    public static int atmoTime = 1, auroraColor = 0, fliesColor = 5;
    public static float auroraGlow = 1f, auroraSpeed = 1f;
    public static float fliesAmount = 1f, fliesSize = 1f, fliesSpeed = 1f;

    // cave dust
    public static boolean dust;
    public static int dustColor = 6;
    public static float dustAmount = 1f, dustSize = 1f, dustSpeed = 1f;

    private Cfg() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("blackmenu.properties");
    }

    public static void load() {
        Path file = file();
        if (!Files.exists(file)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        for (Field f : Cfg.class.getFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            String v = p.getProperty(f.getName());
            if (v == null) continue;
            try {
                if (f.getType() == boolean.class) f.setBoolean(null, Boolean.parseBoolean(v));
                else if (f.getType() == int.class) f.setInt(null, Integer.parseInt(v));
                else if (f.getType() == float.class) f.setFloat(null, Float.parseFloat(v));
            } catch (Exception ignored) {
            }
        }
    }

    public static void save() {
        Properties p = new Properties();
        for (Field f : Cfg.class.getFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            try {
                p.setProperty(f.getName(), String.valueOf(f.get(null)));
            } catch (Exception ignored) {
            }
        }
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "Black Menu");
        } catch (IOException ignored) {
        }
    }
}
