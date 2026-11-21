package com.diya.duarukub;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Invisible. Receives the steering-wheel voice button's request and opens the chosen app.
 * A second press within 2 seconds opens Google voice search instead (if enabled),
 * so the assistant is still reachable.
 */
public class ButtonActivity extends Activity {
    private static final long DOUBLE_PRESS_MS = 2000L;
    private static final String GOOGLE = "com.google.android.googlequicksearchbox";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handle(getIntent());
        finish();
        overridePendingTransition(0, 0);
    }

    private void handle(Intent incoming) {
        if (!Prefs.btnEnabled(this)) {   // safety net; normally this screen is disabled entirely
            openGoogleVoice();
            return;
        }
        long now = System.currentTimeMillis();
        long last = Prefs.get(this).getLong(Prefs.KEY_BTN_LAST_PRESS, 0L);
        String action = incoming != null ? incoming.getAction() : null;
        Prefs.get(this).edit()
                .putLong(Prefs.KEY_BTN_LAST_PRESS, now)
                .putString(Prefs.KEY_BTN_LAST_ACTION, action + " @ " + android.text.format.DateFormat.format("HH:mm:ss", now))
                .apply();

        if (Prefs.btnDouble(this) && now - last < DOUBLE_PRESS_MS) {
            if (openGoogleVoice()) return;
        }

        String pkg = Prefs.btnPkg(this);
        if (pkg == null) {
            Toast.makeText(this, "الكبسة وصلت ✓ اختار التطبيق من الإعدادات", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            Toast.makeText(this, "التطبيق المختار مش موجود", Toast.LENGTH_LONG).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(launch);
    }

    private boolean openGoogleVoice() {
        String[] actions = {Intent.ACTION_VOICE_COMMAND, "android.speech.action.VOICE_SEARCH_HANDS_FREE",
                "android.speech.action.WEB_SEARCH"};
        for (String a : actions) {
            try {
                startActivity(new Intent(a).setPackage(GOOGLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (Exception ignored) { }
        }
        return false;
    }
}
