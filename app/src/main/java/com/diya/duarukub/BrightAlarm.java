package com.diya.duarukub;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

/**
 * Exact alarm at the next day/night boundary. Works even if the background service was killed,
 * because Android wakes the app to deliver the alarm.
 */
public class BrightAlarm extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = context.getApplicationContext();
        PendingResult pr = goAsync();
        if (Prefs.brightOn(c)) {
            int level = Backlight.scheduledLevel(c);
            Backlight.apply(c, level, (ok, msg) -> {
                if (ok) markApplied(c, level);
                schedule(c);
                pr.finish();
            });
        } else {
            pr.finish();
        }
    }

    static void markApplied(Context c, int level) {
        Prefs.get(c).edit()
                .putInt(Prefs.KEY_BRIGHT_LAST, level)
                .putLong(Prefs.KEY_BRIGHT_LAST_TIME, System.currentTimeMillis())
                .apply();
    }

    static void schedule(Context ctx) {
        Context c = ctx.getApplicationContext();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 7, new Intent(c, BrightAlarm.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (am == null) return;
        am.cancel(pi);
        if (!Prefs.brightOn(c)) return;
        long next = Long.MAX_VALUE;
        long now = System.currentTimeMillis();
        for (int d = 0; d <= 1; d++) {
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DAY_OF_YEAR, d);
            for (int m : new int[]{Prefs.dayStartFor(c, day), Prefs.nightStartFor(c, day)}) {
                long at = at(day, m);
                if (at > now && at < next) next = at;
            }
        }
        if (next == Long.MAX_VALUE) return;
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
    }

    /** The given minutes-since-midnight on that day, plus 3 s margin. */
    private static long at(Calendar day, int minutes) {
        Calendar cal = (Calendar) day.clone();
        cal.set(Calendar.HOUR_OF_DAY, minutes / 60);
        cal.set(Calendar.MINUTE, minutes % 60);
        cal.set(Calendar.SECOND, 3);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
