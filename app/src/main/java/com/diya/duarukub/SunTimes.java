package com.diya.duarukub;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Sunrise / sunset for the car's real position (NOAA algorithm, official zenith 90.833°, ~1-2 min accuracy).
 * Times are returned in the head unit's own clock (its time zone offset for that exact date),
 * so they line up with the clock the schedule is compared against.
 */
final class SunTimes {
    // Amman, used only until the first GPS fix is known.
    static final double DEFAULT_LAT = 31.9539, DEFAULT_LON = 35.9106;

    private SunTimes() {}

    /** Minutes since local midnight, or -1 if the sun doesn't rise/set that day. */
    static int compute(double lat, double lon, Calendar day, boolean sunrise) {
        int n = day.get(Calendar.DAY_OF_YEAR);
        double lngHour = lon / 15.0;
        double t = n + ((sunrise ? 6 : 18) - lngHour) / 24.0;
        double m = 0.9856 * t - 3.289;
        double l = norm(m + 1.916 * sin(m) + 0.020 * sin(2 * m) + 282.634, 360);
        double ra = norm(Math.toDegrees(Math.atan(0.91764 * tan(l))), 360);
        ra = (ra + (Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90)) / 15.0;
        double sinDec = 0.39782 * sin(l);
        double cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (Math.cos(Math.toRadians(90.833)) - sinDec * sin(lat)) / (cosDec * Math.cos(Math.toRadians(lat)));
        if (cosH > 1 || cosH < -1) return -1;
        double h = sunrise ? 360 - Math.toDegrees(Math.acos(cosH)) : Math.toDegrees(Math.acos(cosH));
        h /= 15.0;
        double tLocalMean = h + ra - 0.06571 * t - 6.622;
        double ut = norm(tLocalMean - lngHour, 24);

        Calendar noon = (Calendar) day.clone();
        noon.set(Calendar.HOUR_OF_DAY, 12);
        noon.set(Calendar.MINUTE, 0);
        double offsetH = TimeZone.getDefault().getOffset(noon.getTimeInMillis()) / 3600000.0;
        double local = norm(ut + offsetH, 24);
        return (int) Math.round(local * 60) % (24 * 60);
    }

    /** Best known position: newest last-known fix, else the saved one, else Amman. Saves new fixes. */
    static double[] position(Context c) {
        Location best = null;
        try {
            LocationManager lm = c.getSystemService(LocationManager.class);
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (SecurityException | IllegalArgumentException ignored) { }
            }
        } catch (Exception ignored) { }
        if (best != null) {
            Prefs.get(c).edit()
                    .putString(Prefs.KEY_LAT, String.valueOf(best.getLatitude()))
                    .putString(Prefs.KEY_LON, String.valueOf(best.getLongitude()))
                    .apply();
            return new double[]{best.getLatitude(), best.getLongitude(), 1};
        }
        String la = Prefs.get(c).getString(Prefs.KEY_LAT, null), lo = Prefs.get(c).getString(Prefs.KEY_LON, null);
        if (la != null && lo != null) {
            try { return new double[]{Double.parseDouble(la), Double.parseDouble(lo), 2}; } catch (NumberFormatException ignored) { }
        }
        return new double[]{DEFAULT_LAT, DEFAULT_LON, 0};
    }

    private static double sin(double deg) { return Math.sin(Math.toRadians(deg)); }
    private static double tan(double deg) { return Math.tan(Math.toRadians(deg)); }
    private static double norm(double v, double mod) { v %= mod; return v < 0 ? v + mod : v; }
}
