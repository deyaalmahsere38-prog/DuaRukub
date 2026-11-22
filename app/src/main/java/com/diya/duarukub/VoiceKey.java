package com.diya.duarukub;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

/**
 * Keeps the steering voice button pointed at this app (or back at Google when the feature is off).
 * The head unit reads sys_set_customized_voice_key; our value makes it fall back to the system
 * "assist" key, which Android routes to this app as the default assistant.
 */
final class VoiceKey {
    private static final String TAG = "DuaRukub";
    private static final Uri SYSVAR = Uri.parse("content://com.szchoiceway.eventcenter.SysVarProvider/SysVar");
    private static final String KEY = "sys_set_customized_voice_key";
    static final String OURS = "com.diya.duarukub/com.diya.duarukub.ButtonActivity";
    static final String GOOGLE = "com.google.android.googlequicksearchbox/com.google.android.googlequicksearchbox.SearchActivity";

    private VoiceKey() {}

    static String read(Context c) {
        try (Cursor cur = c.getContentResolver().query(SYSVAR, new String[]{"keyvalue"},
                "keyname=?", new String[]{KEY}, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception e) {
            Log.e(TAG, "read voice key failed", e);
        }
        return null;
    }

    static boolean write(Context c, String value) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("keyvalue", value);
            return c.getContentResolver().update(SYSVAR, cv, "keyname=?", new String[]{KEY}) > 0;
        } catch (Exception e) {
            Log.e(TAG, "write voice key failed", e);
            return false;
        }
    }

    static boolean canFixAssistant(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean assistantIsOurs(Context c) {
        return OURS.equals(Settings.Secure.getString(c.getContentResolver(), "assistant"));
    }

    static boolean isLinked(Context c) {
        return OURS.equals(read(c));
    }

    /** Puts the button where the switches say it should be. Returns true if everything is in place. */
    static boolean ensure(Context ctx) {
        Context c = ctx.getApplicationContext();
        String want = Prefs.btnEnabled(c) ? OURS : GOOGLE;
        boolean ok = want.equals(read(c)) || write(c, want);
        if (Prefs.btnEnabled(c) && !assistantIsOurs(c) && canFixAssistant(c)) {
            try {
                Settings.Secure.putString(c.getContentResolver(), "assistant", OURS);
            } catch (Exception e) {
                Log.e(TAG, "set assistant failed", e);
            }
        }
        return ok && (!Prefs.btnEnabled(c) || assistantIsOurs(c));
    }
}
