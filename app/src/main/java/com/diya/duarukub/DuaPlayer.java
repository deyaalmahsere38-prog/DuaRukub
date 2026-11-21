package com.diya.duarukub;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Plays the selected dua file once, pausing other audio while it plays. */
final class DuaPlayer {
    private static final String TAG = "DuaRukub";
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static MediaPlayer player;
    private static AudioFocusRequest focusRequest;
    private static boolean pending;
    private static int savedVolume = -1;

    private DuaPlayer() {}

    static boolean cooldownPassed(Context c) {
        long cooldownMs = Prefs.cooldownMin(c) * 60_000L;
        return System.currentTimeMillis() - Prefs.lastPlayed(c) >= cooldownMs;
    }

    /** Automatic trigger: respects the enabled switch and the cooldown. */
    static void trigger(Context ctx, boolean withDelay) {
        Context c = ctx.getApplicationContext();
        if (!Prefs.enabled(c) || Prefs.uri(c) == null || pending || !cooldownPassed(c)) return;
        pending = true;
        long delayMs = withDelay ? Prefs.delaySec(c) * 1000L : 0L;
        HANDLER.postDelayed(() -> {
            pending = false;
            play(c);
        }, delayMs);
    }

    /** Plays immediately (test button, or after the delay). */
    static synchronized void play(Context ctx) {
        Context c = ctx.getApplicationContext();
        String uriStr = Prefs.uri(c);
        if (uriStr == null) return;
        stop(c);

        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(change -> { })
                .build();
        if (am != null) {
            am.requestAudioFocus(focusRequest);
            int pct = Prefs.volumePct(c);
            if (pct > 0) {
                try {
                    savedVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, Math.round(max * Math.min(pct, 100) / 100f)), 0);
                } catch (Exception e) {
                    savedVolume = -1;
                }
            }
        }

        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(attrs);
            mp.setDataSource(c, Uri.parse(uriStr));
            mp.setOnCompletionListener(m -> stop(c));
            mp.setOnErrorListener((m, what, extra) -> { stop(c); return true; });
            mp.prepare();
            mp.start();
            player = mp;
            Prefs.get(c).edit().putLong(Prefs.KEY_LAST, System.currentTimeMillis()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Could not play dua", e);
            stop(c);
        }
    }

    static synchronized void stop(Context c) {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am != null && savedVolume >= 0) {
            try { am.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0); } catch (Exception ignored) { }
            savedVolume = -1;
        }
        if (am != null && focusRequest != null) {
            am.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        }
    }
}
