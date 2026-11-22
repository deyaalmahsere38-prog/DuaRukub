package com.diya.duarukub;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String NAME = "dua";

    static final String KEY_MASTER = "master";               // everything on/off
    static final String KEY_BTN_ENABLED = "btn_enabled";     // steering button feature on/off
    static final String KEY_URI = "uri";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_MODE = "mode";               // MODE_WAKE or MODE_MOVING
    static final String KEY_ON_BOOT = "on_boot";
    static final String KEY_ON_SCREEN = "on_screen";
    static final String KEY_DELAY = "delay_sec";
    static final String KEY_COOLDOWN = "cooldown_min";
    static final String KEY_SPEED = "speed_kmh";
    static final String KEY_VOLUME = "volume_pct";       // 0 = leave volume as is
    static final String KEY_LAST = "last_played";
    static final String KEY_BRIGHT_ON = "bright_on";
    static final String KEY_BRIGHT_DAY = "bright_day";
    static final String KEY_BRIGHT_NIGHT = "bright_night";
    static final String KEY_DAY_START = "day_start_min";
    static final String KEY_NIGHT_START = "night_start_min";
    static final String KEY_BRIGHT_ALT = "bright_alt";
    static final String KEY_BRIGHT_LAST = "bright_last";
    static final String KEY_BRIGHT_LAST_TIME = "bright_last_time";
    static final String KEY_BTN_PKG = "btn_pkg";              // app the steering button opens
    static final String KEY_BTN_DOUBLE = "btn_double_google"; // quick 2nd press opens Google voice
    static final String KEY_BTN_LAST_PRESS = "btn_last_press";
    static final String KEY_BTN_LAST_ACTION = "btn_last_action";

    static final int MODE_WAKE = 0;
    static final int MODE_MOVING = 1;

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static boolean master(Context c) { return get(c).getBoolean(KEY_MASTER, true); }
    /** Dua auto-play is active only if the master switch and its own switch are on. */
    static boolean enabled(Context c) { return master(c) && get(c).getBoolean(KEY_ENABLED, true); }
    static boolean btnEnabled(Context c) { return master(c) && get(c).getBoolean(KEY_BTN_ENABLED, true); }
    static String uri(Context c) { return get(c).getString(KEY_URI, null); }
    static int mode(Context c) { return get(c).getInt(KEY_MODE, MODE_WAKE); }
    static boolean onBoot(Context c) { return get(c).getBoolean(KEY_ON_BOOT, true); }
    static boolean onScreen(Context c) { return get(c).getBoolean(KEY_ON_SCREEN, true); }
    static int delaySec(Context c) { return get(c).getInt(KEY_DELAY, 4); }
    static int cooldownMin(Context c) { return get(c).getInt(KEY_COOLDOWN, 10); }
    static int speedKmh(Context c) { return get(c).getInt(KEY_SPEED, 10); }
    static int volumePct(Context c) { return get(c).getInt(KEY_VOLUME, 0); }
    static long lastPlayed(Context c) { return get(c).getLong(KEY_LAST, 0L); }
    static boolean brightOn(Context c) { return master(c) && get(c).getBoolean(KEY_BRIGHT_ON, false); }
    static int dayLevel(Context c) { return get(c).getInt(KEY_BRIGHT_DAY, 16); }
    static int nightLevel(Context c) { return get(c).getInt(KEY_BRIGHT_NIGHT, 5); }
    static int dayStart(Context c) { return get(c).getInt(KEY_DAY_START, 6 * 60); }
    static int nightStart(Context c) { return get(c).getInt(KEY_NIGHT_START, 17 * 60 + 30); }
    /** Service must run if the dua or the brightness schedule is on. */
    static boolean serviceNeeded(Context c) { return enabled(c) || brightOn(c); }
    static String btnPkg(Context c) { return get(c).getString(KEY_BTN_PKG, null); }
    static boolean btnDouble(Context c) { return get(c).getBoolean(KEY_BTN_DOUBLE, true); }
    static String btnLastAction(Context c) { return get(c).getString(KEY_BTN_LAST_ACTION, null); }
}
