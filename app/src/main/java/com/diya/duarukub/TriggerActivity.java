package com.diya.duarukub;

import android.app.Activity;
import android.os.Bundle;

/**
 * No UI. Launched by the head unit's "start app on power on" option (or the first icon).
 * Starts the background service, which plays the dua per the saved settings, then closes at once.
 */
public class TriggerActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Prefs.enabled(this)) DuaService.startFromAutostart(this);
        finish();
        overridePendingTransition(0, 0);
    }
}
