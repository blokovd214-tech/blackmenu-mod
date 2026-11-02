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
    public static int titleStyle = 0;           // 0 = helix, 1 = chains
    public static float menuScale = 1f;
    public static int lang = 0;                 // 0 = RU, 1 = EN
    public static String favs = "";

    // blur+
    public static boolean blurMenu = true;
    public static float blurDim = 0.3f;

    // hud
    public static boolean hotbar = true, infoPanel = true, fps = true, coords, ping;
    public static boolean arraylist = true, targetHud = true, armorHud = true, effectsHud = true;
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

    // target highlight
    public static boolean esp, espFlash = true;
    public static int espMode = 4, espMode2 = 0, espColor = 0;
    public static float espSize = 1f, espSpeed = 1f, espCount = 6f, espAlpha = 1f;

    // atmosphere
    public static boolean atmo, aurora = true;
    public static int atmoMode = 1, atmoTime = 0;
    public static float atmoDensity = 1f, atmoSize = 1f, atmoSpeed = 1f, atmoWind = 1f, auroraGlow = 1f;

    // norender
    public static boolean nr, nrHurt = true, nrFire = true, nrItems = true, nrXp, nrStands, nrLightning;

    // weather and wet floor
    public static boolean wx, wxPuddles = true, wxRipples = true, wxOvercast = true, wxSplash = true;
    public static int wxMode = 0;
    public static float wxDensity = 0.5f, wxRadius = 16f, wxHeight = 24f, wxSize = 1f, wxShine = 1f;

    // kill effect
    public static boolean kfx;
    public static int kfxMode = 0;
    public static float kfxPower = 1f, kfxSize = 1f;

    // performance
    public static int quality = 1;
    public static boolean adaptive = true;

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
                else if (f.getType() == String.class) f.set(null, v);
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
