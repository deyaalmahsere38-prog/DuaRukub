package com.diya.duarukub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the service (and plays the dua) when the head unit boots. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Features.apply(context);
        if (!Prefs.serviceNeeded(context)) return;
        boolean isBoot = !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction());
        DuaService.start(context, isBoot);
    }
}
