package com.blackmenu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Module registry for the menu. Names are written as "English|Русский". */
final class Mods {
    enum Cat {
        HUD("HUD|Худ"), VISUAL("Visual|Визуал"), PVP("PvP|PvP"), COSMETICS("Cosmetics|Косметика"), SETTINGS("Settings|Настройки");

        final String name;

        Cat(String n) {
            name = n;
        }
    }

    enum Kind { BOOL, SLIDER, MODE }

    static final class Set {
        String name, desc;
        Kind kind;
        BooleanSupplier getB;
        Consumer<Boolean> setB;
        Supplier<Float> getF;
        Consumer<Float> setF;
        float min, max;
        boolean deferred;
        Float pending;
        String[] opts;
        IntSupplier getI;
        IntConsumer setI;
        float anim;
    }

    static final class Mod {
        String name;
        Cat cat;
        BooleanSupplier on;
        Consumer<Boolean> setOn;
        final List<Set> sets = new ArrayList<>();
        float anim;
    }

    static final List<Mod> ALL = new ArrayList<>();

    static final String[] PAL = {"Cyan|Бирюзовый", "Purple|Фиолетовый", "Pink|Розовый", "Red|Красный",
            "Green|Зелёный", "Gold|Золотой", "White|Белый", "Rainbow|Радуга"};
    static final String[] ESP = {"Lightning|Молнии", "Circle|Круг", "Chains|Цепи", "Orbs|Сферы", "Crystals|Кристаллы",
            "Halo|Нимб", "Helix|Спираль", "Beam|Луч", "Runes|Руны"};
    static final String[] ESP2 = prepend("None|Нет", ESP);
    static final String[] HB_COLORS = prepend("By type|По типу", PAL);
    static final String[] ATMO = {"Aurora|Аврора", "Winter|Зима", "Autumn|Осень", "Summer|Лето", "Spring|Весна",
            "Fireflies|Светлячки", "Crimson|Багровая ночь", "Storm|Гроза", "Ash|Пепел"};
    static final String[] TIMES = {"Auto|Авто", "Server|Сервер", "Night|Ночь", "Sunset|Закат", "Dawn|Рассвет", "Day|День"};
    static final String[] WX = {"Rain|Дождь", "Wet floor|Мокрый пол", "Drizzle|Морось"};
    static final String[] KFX = {"Blood|Кровь", "Lightning|Молния", "Souls|Души"};

    private Mods() {}

    private static String[] prepend(String first, String[] rest) {
        String[] out = new String[rest.length + 1];
        out[0] = first;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    /** Picks the language part of "English|Русский". */
    static String tr(String s) {
        int i = s.indexOf('|');
        if (i < 0) return s;
        return Cfg.lang == 0 ? s.substring(i + 1) : s.substring(0, i);
    }

    private static String key(Mod m) {
        int i = m.name.indexOf('|');
        return i < 0 ? m.name : m.name.substring(0, i);
    }

    static boolean isFav(Mod m) {
        return ("," + Cfg.favs + ",").contains("," + key(m) + ",");
    }

    static void toggleFav(Mod m) {
        String k = key(m);
        if (isFav(m)) {
            Cfg.favs = ("," + Cfg.favs + ",").replace("," + k + ",", ",");
            Cfg.favs = Cfg.favs.replaceAll("^,+|,+$", "");
        } else {
            Cfg.favs = Cfg.favs.isEmpty() ? k : Cfg.favs + "," + k;
        }
    }

    // ------------------------------------------------------------------ builders

    private static Mod mod(Cat cat, String name, BooleanSupplier on, Consumer<Boolean> setOn) {
        Mod m = new Mod();
        m.cat = cat; m.name = name; m.on = on; m.setOn = setOn;
        m.anim = on != null && on.getAsBoolean() ? 1f : 0f;
        ALL.add(m);
        return m;
    }

    private static void bool(Mod m, String name, String desc, BooleanSupplier g, Consumer<Boolean> s) {
        Set x = new Set();
        x.kind = Kind.BOOL; x.name = name; x.desc = desc; x.getB = g; x.setB = s;
        x.anim = g.getAsBoolean() ? 1f : 0f;
        m.sets.add(x);
    }

    private static Set slider(Mod m, String name, String desc, float min, float max, Supplier<Float> g, Consumer<Float> s) {
        Set x = new Set();
        x.kind = Kind.SLIDER; x.name = name; x.desc = desc; x.min = min; x.max = max; x.getF = g; x.setF = s;
        m.sets.add(x);
        return x;
    }

    private static void mode(Mod m, String name, String desc, String[] opts, IntSupplier g, IntConsumer s) {
        Set x = new Set();
        x.kind = Kind.MODE; x.name = name; x.desc = desc; x.opts = opts; x.getI = g; x.setI = s;
        m.sets.add(x);
    }

    static {
        Mod m;

        // ---- HUD
        m = mod(Cat.HUD, "Hotbar|Хотбар", () -> Cfg.hotbar, v -> Cfg.hotbar = v);
        mode(m, "Accent|Акцент", "Selector color|Цвет рамки выбора", PAL, () -> Cfg.hudAccent, v -> Cfg.hudAccent = v);

        m = mod(Cat.HUD, "Info panel|Инфо-панель", () -> Cfg.infoPanel, v -> Cfg.infoPanel = v);
        bool(m, "FPS|FPS", "Show frame rate|Показывать частоту кадров", () -> Cfg.fps, v -> Cfg.fps = v);
        bool(m, "Coordinates|Координаты", "Show XYZ|Показывать XYZ", () -> Cfg.coords, v -> Cfg.coords = v);
        bool(m, "Ping|Пинг", "Show latency|Показывать задержку", () -> Cfg.ping, v -> Cfg.ping = v);
        slider(m, "Opacity|Прозрачность", "Panel opacity|Прозрачность панели", 0.3f, 1f, () -> Cfg.hudAlpha, v -> Cfg.hudAlpha = v);

        m = mod(Cat.HUD, "GUI animations|Анимации GUI", () -> Cfg.smoothSwap, v -> Cfg.smoothSwap = v);
        slider(m, "Slot speed|Скорость слотов", "Smooth slot switching|Плавное переключение слотов",
                0.5f, 3f, () -> Cfg.swapSpeed, v -> Cfg.swapSpeed = v);

        m = mod(Cat.HUD, "Item borders|Рамки предметов", () -> Cfg.borders, v -> Cfg.borders = v);
        bool(m, "Inventory|Инвентарь", "Borders in containers|Рамки в контейнерах", () -> Cfg.bordersInv, v -> Cfg.bordersInv = v);
        bool(m, "Hotbar|Хотбар", "Borders in the hotbar|Рамки в хотбаре", () -> Cfg.bordersHotbar, v -> Cfg.bordersHotbar = v);
        bool(m, "Hide common|Скрыть обычные", "No border on common items|Без рамки у обычных предметов",
                () -> Cfg.bordersHideCommon, v -> Cfg.bordersHideCommon = v);
        slider(m, "Opacity|Прозрачность", "Border opacity|Прозрачность рамок", 0.2f, 1f, () -> Cfg.bordersAlpha, v -> Cfg.bordersAlpha = v);

        // ---- Visual
        mod(Cat.VISUAL, "Fullbright|Яркость", () -> Cfg.fullbright, BlackMenuClient::setFullbright);

        m = mod(Cat.VISUAL, "Blur+|Размытие GUI", () -> Cfg.blurMenu, v -> Cfg.blurMenu = v);
        slider(m, "Dim|Затемнение", "Extra darkness behind the menu|Доп. затемнение фона", 0f, 0.9f, () -> Cfg.blurDim, v -> Cfg.blurDim = v);

        m = mod(Cat.VISUAL, "Weather & wet floor|Погода и мокрый пол", () -> Cfg.wx, v -> Cfg.wx = v);
        mode(m, "Mode|Режим", "Weather mode|Режим работы", WX, () -> Cfg.wxMode, v -> Cfg.wxMode = v);
        slider(m, "Density|Плотность дождя", "Amount of rain|Количество капель", 0.2f, 2f, () -> Cfg.wxDensity, v -> Cfg.wxDensity = v);
        slider(m, "Cull radius|Радиус отрисовки", "Draw distance|Дальность отрисовки", 8f, 24f, () -> Cfg.wxRadius, v -> Cfg.wxRadius = v);
        slider(m, "Spawn height|Высота спавна", "Rain column height|Высота столба дождя", 8f, 40f, () -> Cfg.wxHeight, v -> Cfg.wxHeight = v);
        slider(m, "Drop size|Размер капель", "Size of drops|Масштаб капель", 0.5f, 2f, () -> Cfg.wxSize, v -> Cfg.wxSize = v);
        bool(m, "Puddles|Лужи", "Wet shiny ground|Мокрая блестящая земля", () -> Cfg.wxPuddles, v -> Cfg.wxPuddles = v);
        bool(m, "Ripples|Рябь", "Circles on puddles in rain|Круги на лужах во время дождя", () -> Cfg.wxRipples, v -> Cfg.wxRipples = v);
        slider(m, "Shine|Блеск", "Wet floor brightness|Яркость мокрого пола", 0.2f, 2f, () -> Cfg.wxShine, v -> Cfg.wxShine = v);

        m = mod(Cat.VISUAL, "Cave dust|Пыль в пещерах", () -> Cfg.dust, v -> Cfg.dust = v);
        slider(m, "Amount|Количество", "How many specks|Сколько пылинок", 0.1f, 2f, () -> Cfg.dustAmount, v -> Cfg.dustAmount = v);
        slider(m, "Size|Размер", "Speck size|Размер пылинок", 0.4f, 3f, () -> Cfg.dustSize, v -> Cfg.dustSize = v);
        slider(m, "Speed|Скорость", "Drift speed|Скорость дрейфа", 0.2f, 3f, () -> Cfg.dustSpeed, v -> Cfg.dustSpeed = v);
        mode(m, "Color|Цвет", "Dust color|Цвет пыли", PAL, () -> Cfg.dustColor, v -> Cfg.dustColor = v);

        // ---- PvP
        m = mod(Cat.PVP, "Hitboxes|Хитбоксы", () -> Cfg.hb, v -> Cfg.hb = v);
        bool(m, "Players|Игроки", "Show on players|Показывать у игроков", () -> Cfg.hbPlayers, v -> Cfg.hbPlayers = v);
        bool(m, "Mobs|Мобы", "Show on mobs and animals|Показывать у мобов и животных", () -> Cfg.hbMobs, v -> Cfg.hbMobs = v);
        bool(m, "Others|Остальные", "Items, projectiles, etc.|Предметы, снаряды и т.д.", () -> Cfg.hbOthers, v -> Cfg.hbOthers = v);
        bool(m, "Fill|Заливка", "Soft side glow|Мягкое свечение граней", () -> Cfg.hbFill, v -> Cfg.hbFill = v);
        bool(m, "Eye line|Линия глаз", "Ring at eye height|Кольцо на уровне глаз", () -> Cfg.hbEye, v -> Cfg.hbEye = v);
        mode(m, "Color|Цвет", "Box color|Цвет хитбокса", HB_COLORS, () -> Cfg.hbColor, v -> Cfg.hbColor = v);
        slider(m, "Line width|Толщина", "Outline thickness|Толщина линий", 0.5f, 3f, () -> Cfg.hbWidth, v -> Cfg.hbWidth = v);
        slider(m, "Opacity|Прозрачность", "Box opacity|Прозрачность", 0.2f, 1f, () -> Cfg.hbAlpha, v -> Cfg.hbAlpha = v);

        m = mod(Cat.PVP, "Target highlight|Подсветка целей", () -> Cfg.esp, v -> Cfg.esp = v);
        mode(m, "Mode|Режим отображения", "Type of 3D object around the target|Тип 3D объекта вокруг цели", ESP, () -> Cfg.espMode, v -> Cfg.espMode = v);
        mode(m, "Extra mode|Доп. режим", "Second effect on top|Второй эффект сверху", ESP2, () -> Cfg.espMode2, v -> Cfg.espMode2 = v);
        mode(m, "Color|Цвет", "Effect color|Цвет эффекта", PAL, () -> Cfg.espColor, v -> Cfg.espColor = v);
        bool(m, "Hit flash|Вспышка при уроне", "Turns red when the target is hit|Краснеет при ударе по цели", () -> Cfg.espFlash, v -> Cfg.espFlash = v);
        slider(m, "Size|Размер", "Effect scale|Масштаб эффекта", 0.5f, 2f, () -> Cfg.espSize, v -> Cfg.espSize = v);
        slider(m, "Speed|Скорость вращения", "Animation speed|Скорость анимации", 0.2f, 3f, () -> Cfg.espSpeed, v -> Cfg.espSpeed = v);
        slider(m, "Count|Кол-во", "Crystals and orbs|Кристаллы и сферы", 3f, 12f, () -> Cfg.espCount, v -> Cfg.espCount = v);
        slider(m, "Opacity|Прозрачность", "Effect opacity|Прозрачность", 0.2f, 1f, () -> Cfg.espAlpha, v -> Cfg.espAlpha = v);

        // ---- Cosmetics
        m = mod(Cat.COSMETICS, "Atmosphere|Атмосфера", () -> Cfg.atmo, v -> Cfg.atmo = v);
        mode(m, "Mode|Режим", "Season or mood|Время года или настроение", ATMO, () -> Cfg.atmoMode, v -> Cfg.atmoMode = v);
        mode(m, "Time|Время суток", "Auto uses the theme's time|Авто берёт время из темы", TIMES, () -> Cfg.atmoTime, v -> Cfg.atmoTime = v);
        slider(m, "Density|Плотность", "Amount of particles|Количество частиц", 0.1f, 2f, () -> Cfg.atmoDensity, v -> Cfg.atmoDensity = v);
        slider(m, "Size|Размер", "Particle size|Размер частиц", 0.4f, 3f, () -> Cfg.atmoSize, v -> Cfg.atmoSize = v);
        slider(m, "Speed|Скорость", "Fall and drift speed|Скорость падения", 0.2f, 3f, () -> Cfg.atmoSpeed, v -> Cfg.atmoSpeed = v);
        slider(m, "Wind|Ветер", "Sideways drift|Боковой снос", 0f, 3f, () -> Cfg.atmoWind, v -> Cfg.atmoWind = v);
        bool(m, "Aurora|Северное сияние", "Where the theme has one|Если есть в теме", () -> Cfg.aurora, v -> Cfg.aurora = v);
        slider(m, "Aurora glow|Яркость сияния", "Brightness|Яркость", 0.2f, 2f, () -> Cfg.auroraGlow, v -> Cfg.auroraGlow = v);

        m = mod(Cat.COSMETICS, "Kill effect|Эффект убийства", () -> Cfg.kfx, v -> Cfg.kfx = v);
        mode(m, "Mode|Режим", "Effect when your target dies|Эффект, когда цель погибает", KFX, () -> Cfg.kfxMode, v -> Cfg.kfxMode = v);
        slider(m, "Power|Сила", "Amount of effect|Количество эффекта", 0.3f, 2f, () -> Cfg.kfxPower, v -> Cfg.kfxPower = v);
        slider(m, "Size|Размер", "Effect scale|Масштаб", 0.5f, 2f, () -> Cfg.kfxSize, v -> Cfg.kfxSize = v);

        // ---- Settings
        m = mod(Cat.SETTINGS, "Interface|Интерфейс", null, null);
        Set sz = slider(m, "Menu size|Размер меню", "Applied when you let go|Применяется после отпускания", 0.6f, 1.5f,
                () -> Cfg.menuScale, v -> Cfg.menuScale = v);
        sz.deferred = true;
        bool(m, "Chain title|Стартовый экран", "Chain main menu on launch|Главное меню на цепях при запуске",
                () -> Cfg.titleChains, v -> Cfg.titleChains = v);
    }
}
