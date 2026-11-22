package com.diya.duarukub;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Catches the "assistant" key that the head unit injects when the steering voice button is pressed
 * (KEYCODE_ASSIST = 219, also VOICE_ASSIST = 231) and opens the app chosen in settings.
 */
public class KeyService extends AccessibilityService {
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_ASSIST && code != KeyEvent.KEYCODE_VOICE_ASSIST) return false;
        if (!Prefs.btnEnabled(this)) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            Intent i = new Intent(this, ButtonActivity.class);
            i.setAction("com.diya.duarukub.KEY_" + code);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
        return true;   // consume both down and up so the system doesn't also act on it
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
