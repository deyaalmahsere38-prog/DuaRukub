package com.diya.duarukub;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;

import java.util.Calendar;

/**
 * Sets the head unit's real backlight (handled by the MCU, not Android):
 * 1) writes the value into the vendor settings table (so the head unit keeps it after wake/lamp change),
 * 2) asks the vendor EventService to push it to the MCU now (AIDL IEventService.sendBacklight, code 60).
 */
final class Backlight {
    private static final String TAG = "DuaRukub";
    private static final Uri SYSVAR = Uri.parse("content://com.szchoiceway.eventcenter.SysVarProvider/SysVar");
    private static final String DESCRIPTOR = "com.szchoiceway.eventcenter.IEventService";
    private static final int TRANSACTION_sendBacklight = 60;

    interface Result { void done(boolean ok, String msg); }

    private Backlight() {}

    /** Level 0..20, same scale as the head unit's brightness slider. */
    static void apply(Context ctx, int level, Result cb) {
        Context c = ctx.getApplicationContext();
        int lv = Math.max(0, Math.min(20, level));
        boolean dbOk = writeKey(c, "Set_Day_Light", lv) & writeKey(c, "Set_Night_Light", lv);
        boolean altOrder = Prefs.get(c).getBoolean(Prefs.KEY_BRIGHT_ALT, false);

        Intent i = new Intent("com.szchoiceway.eventcenter.EventService");
        i.setPackage("com.szchoiceway.eventcenter");
        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                boolean ok = false;
                String msg;
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (altOrder) { data.writeByte((byte) lv); data.writeByte((byte) 0); }
                    else { data.writeByte((byte) 0); data.writeByte((byte) lv); }
                    binder.transact(TRANSACTION_sendBacklight, data, reply, 0);
                    reply.readException();
                    ok = true;
                    msg = "انبعت السطوع " + lv;
                } catch (Exception e) {
                    msg = "خطأ: " + e;
                    Log.e(TAG, "sendBacklight failed", e);
                } finally {
                    data.recycle();
                    reply.recycle();
                    try { c.unbindService(this); } catch (Exception ignored) { }
                }
                if (cb != null) cb.done(ok, msg + (dbOk ? "" : " (الجدول ما انكتب)"));
            }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        boolean bound;
        try {
            bound = c.bindService(i, conn, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            bound = false;
            Log.e(TAG, "bind failed", e);
        }
        if (!bound && cb != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    cb.done(false, "ما قدرت أتصل بخدمة الشاشة" + (dbOk ? "" : " ولا بالجدول")));
        }
    }

    private static boolean writeKey(Context c, String key, int value) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("keyvalue", String.valueOf(value));
            int n = c.getContentResolver().update(SYSVAR, cv, "keyname=?", new String[]{key});
            return n > 0;
        } catch (Exception e) {
            Log.e(TAG, "write " + key + " failed", e);
            return false;
        }
    }

    /** True if "now" falls in the daytime window [dayStart, nightStart). Minutes since midnight. */
    static boolean isDay(Context c) {
        Calendar cal = Calendar.getInstance();
        int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int ds = Prefs.dayStart(c), ns = Prefs.nightStart(c);
        if (ds == ns) return true;
        return ds < ns ? (now >= ds && now < ns) : (now >= ds || now < ns);
    }

    static int scheduledLevel(Context c) {
        return isDay(c) ? Prefs.dayLevel(c) : Prefs.nightLevel(c);
    }
}
