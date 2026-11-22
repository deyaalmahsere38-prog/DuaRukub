package com.diya.duarukub;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Stays alive in the background, listens for wake-ups and (optionally) for the car starting to move. */
public class DuaService extends Service {
    static final String EXTRA_FROM_BOOT = "from_boot";
    static final String EXTRA_FROM_AUTOSTART = "from_autostart";
    private static final String CHANNEL = "dua_service";
    private static final long GPS_WATCH_MS = 30 * 60_000L;   // stop waiting for movement after 30 min

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager lm;
    private boolean watchingGps;

    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Re-apply brightness a few seconds after wake: the head unit restores its own value on wake.
            handler.postDelayed(() -> applyBrightness(true), 5000L);
            if (Prefs.onScreen(context)) onWake();
        }
    };

    private final LocationListener speedListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location loc) {
            if (loc.hasSpeed() && loc.getSpeed() * 3.6f >= Prefs.speedKmh(DuaService.this)) {
                stopGps();
                DuaPlayer.trigger(DuaService.this, false);
            }
        }
        @Override public void onStatusChanged(String p, int s, Bundle b) { }
        @Override public void onProviderEnabled(String p) { }
        @Override public void onProviderDisabled(String p) { }
    };

    private final Runnable gpsTimeout = this::stopGps;

    /** Checks every minute whether the day/night brightness period changed. */
    private final Runnable brightTick = new Runnable() {
        @Override public void run() {
            applyBrightness(false);
            handler.postDelayed(this, 60_000L);
        }
    };

    private void applyBrightness(boolean force) {
        if (!Prefs.brightOn(this)) return;
        int level = Backlight.scheduledLevel(this);
        int last = Prefs.get(this).getInt(Prefs.KEY_BRIGHT_LAST, -1);
        if (!force && level == last) return;
        Backlight.apply(this, level, (ok, msg) -> {
            if (ok) Prefs.get(this).edit().putInt(Prefs.KEY_BRIGHT_LAST, level).apply();
        });
    }

    static void start(Context c, boolean fromBoot) {
        Intent i = new Intent(c, DuaService.class);
        i.putExtra(EXTRA_FROM_BOOT, fromBoot);
        c.startForegroundService(i);
    }

    static void startFromAutostart(Context c) {
        Intent i = new Intent(c, DuaService.class);
        i.putExtra(EXTRA_FROM_AUTOSTART, true);
        c.startForegroundService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        lm = getSystemService(LocationManager.class);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "دعاء الركوب", NotificationManager.IMPORTANCE_MIN));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("دعاء الركوب")
                .setContentText("جاهز")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        startForeground(1, n);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(wakeReceiver, f);
        handler.post(brightTick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            boolean boot = intent.getBooleanExtra(EXTRA_FROM_BOOT, false) && Prefs.onBoot(this);
            boolean autostart = intent.getBooleanExtra(EXTRA_FROM_AUTOSTART, false);
            if (boot || autostart) {
                handler.postDelayed(() -> applyBrightness(true), 5000L);
                onWake();
            }
        }
        return START_STICKY;
    }

    private void onWake() {
        if (!Prefs.enabled(this) || !DuaPlayer.cooldownPassed(this)) return;
        if (Prefs.mode(this) == Prefs.MODE_MOVING) {
            startGps();
        } else {
            DuaPlayer.trigger(this, true);
        }
    }

    private void startGps() {
        if (watchingGps || lm == null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // No GPS permission: fall back to playing on wake so the dua still plays.
            DuaPlayer.trigger(this, true);
            return;
        }
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, speedListener, Looper.getMainLooper());
            watchingGps = true;
            handler.postDelayed(gpsTimeout, GPS_WATCH_MS);
        } catch (Exception e) {
            DuaPlayer.trigger(this, true);
        }
    }

    private void stopGps() {
        handler.removeCallbacks(gpsTimeout);
        if (watchingGps && lm != null) {
            try { lm.removeUpdates(speedListener); } catch (Exception ignored) { }
        }
        watchingGps = false;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(brightTick);
        stopGps();
        try { unregisterReceiver(wakeReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
