package com.diya.duarukub;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Turns features on/off at the system level according to the saved switches. */
final class Features {
    private Features() {}

    static void apply(Context ctx) {
        Context c = ctx.getApplicationContext();

        // Steering button: when off, our handler is removed from the system entirely,
        // so the button goes back to opening Google like before.
        c.getPackageManager().setComponentEnabledSetting(
                new ComponentName(c, ButtonActivity.class),
                Prefs.btnEnabled(c) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        // Dua: background service runs only when the dua feature is on.
        if (!Prefs.enabled(c)) DuaPlayer.stop(c);
        BrightAlarm.schedule(c);
        VoiceKey.ensure(c);
        if (Prefs.serviceNeeded(c)) {
            DuaService.start(c, false);
        } else {
            c.stopService(new Intent(c, DuaService.class));
        }
    }
}
