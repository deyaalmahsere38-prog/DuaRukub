package com.diya.duarukub;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Brightness diagnostics: takes a snapshot of settings / system properties / backlight files,
 * the user changes brightness from the head unit's own UI, takes a second snapshot,
 * and the app reports what changed. The report is saved to a text file to send for analysis.
 */
public class DiagActivity extends Activity {
    private static final int SAVE_REPORT = 10;
    private static final String[] HINTS = {"bright", "light", "lcd", "backlight", "dim", "night", "day", "lum", "pwm"};
    private static final String[] VENDOR_HINTS = {"syu", "fyt", "microntek", "hzbhd", "ts.", "tsit", "autochips",
            "xy", "carsetting", "carsettings", "canbus", "mcu", "zxw", "topway", "ainavi", "navi", "car"};

    private Map<String, String> snap1;
    private Map<String, String> snap2;
    private TextView out;
    private String report = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView steps = new TextView(this);
        steps.setTextSize(16);
        steps.setText("١. نزّل السطوع لأقل إشي من واجهة الشاشة.\n"
                + "٢. ارجع هون واضغط «لقطة ١».\n"
                + "٣. ارفع السطوع لأعلى إشي من واجهة الشاشة.\n"
                + "٤. ارجع هون واضغط «لقطة ٢».\n"
                + "٥. اضغط «حفظ التقرير» وابعته.");
        root.addView(steps);

        Button b1 = new Button(this);
        b1.setText("لقطة ١ (سطوع منخفض)");
        b1.setOnClickListener(v -> {
            snap1 = snapshot();
            toast("تم: " + snap1.size() + " قيمة");
        });
        root.addView(b1);

        Button b2 = new Button(this);
        b2.setText("لقطة ٢ (سطوع عالي)");
        b2.setOnClickListener(v -> {
            if (snap1 == null) { toast("اعمل لقطة ١ أول"); return; }
            snap2 = snapshot();
            report = buildReport();
            out.setText(report);
        });
        root.addView(b2);

        Button save = new Button(this);
        save.setText("حفظ التقرير كملف");
        save.setOnClickListener(v -> {
            if (report.isEmpty()) report = buildReport();
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TITLE, "brightness_report.txt");
            startActivityForResult(i, SAVE_REPORT);
        });
        root.addView(save);

        out = new TextView(this);
        out.setTextIsSelectable(true);
        out.setTextSize(12);
        out.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.addView(out);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_REPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
            os.write(report.getBytes(StandardCharsets.UTF_8));
            toast("انحفظ التقرير");
        } catch (Exception e) {
            toast("ما قدرت أحفظ: " + e.getMessage());
        }
    }

    // ---------- snapshot ----------

    private Map<String, String> snapshot() {
        Map<String, String> m = new TreeMap<>();
        readSettingsTable("system", m);
        readSettingsTable("secure", m);
        readSettingsTable("global", m);
        readProps(m);
        readSysfs(new File("/sys/class/backlight"), m);
        readSysfs(new File("/sys/class/leds/lcd-backlight"), m);
        return m;
    }

    private void readSettingsTable(String table, Map<String, String> m) {
        try (Cursor c = getContentResolver().query(Uri.parse("content://settings/" + table),
                new String[]{"name", "value"}, null, null, null)) {
            if (c == null) return;
            while (c.moveToNext()) {
                m.put(table + ":" + c.getString(0), String.valueOf(c.getString(1)));
            }
        } catch (Exception e) {
            m.put(table + ":<error>", e.toString());
        }
    }

    private void readProps(Map<String, String> m) {
        try {
            Process p = new ProcessBuilder("getprop").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int sep = line.indexOf("]: [");
                    if (line.startsWith("[") && sep > 0 && line.endsWith("]")) {
                        m.put("prop:" + line.substring(1, sep), line.substring(sep + 4, line.length() - 1));
                    }
                }
            }
            p.waitFor();
        } catch (Exception e) {
            m.put("prop:<error>", e.toString());
        }
    }

    private void readSysfs(File dir, Map<String, String> m) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File f : entries) {
            if (f.isDirectory()) {
                for (String name : new String[]{"brightness", "actual_brightness", "max_brightness"}) {
                    readFile(new File(f, name), m);
                }
            } else if (f.getName().contains("brightness")) {
                readFile(f, m);
            }
        }
    }

    private void readFile(File f, Map<String, String> m) {
        if (!f.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            m.put("sysfs:" + f.getPath(), String.valueOf(r.readLine()).trim());
        } catch (Exception e) {
            m.put("sysfs:" + f.getPath(), "<not readable>");
        }
    }

    // ---------- report ----------

    private String buildReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("== DEVICE ==\n")
                .append("Manufacturer: ").append(Build.MANUFACTURER).append('\n')
                .append("Model: ").append(Build.MODEL).append('\n')
                .append("Brand: ").append(Build.BRAND).append('\n')
                .append("Board: ").append(Build.BOARD).append('\n')
                .append("Hardware: ").append(Build.HARDWARE).append('\n')
                .append("Display: ").append(Build.DISPLAY).append('\n')
                .append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        try {
            sb.append("Android brightness setting: ")
                    .append(Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS)).append('\n');
        } catch (Exception ignored) { }

        sb.append("\n== CHANGED BETWEEN SNAPSHOT 1 AND 2 ==\n");
        if (snap1 != null && snap2 != null) {
            TreeSet<String> keys = new TreeSet<>(snap1.keySet());
            keys.addAll(snap2.keySet());
            int changes = 0;
            for (String k : keys) {
                String a = snap1.get(k), b = snap2.get(k);
                if (a == null || b == null || !a.equals(b)) {
                    sb.append(k).append(" : ").append(a).append("  ->  ").append(b).append('\n');
                    changes++;
                }
            }
            if (changes == 0) sb.append("(nothing changed)\n");
        } else {
            sb.append("(snapshots not taken)\n");
        }

        Map<String, String> current = snap2 != null ? snap2 : snapshot();
        sb.append("\n== KEYS THAT LOOK BRIGHTNESS-RELATED ==\n");
        for (Map.Entry<String, String> e : current.entrySet()) {
            String k = e.getKey().toLowerCase(Locale.ROOT);
            for (String h : HINTS) {
                if (k.contains(h)) { sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n'); break; }
            }
        }

        sb.append("\n== VENDOR / CAR APPS ==\n");
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : apps) {
                String p = ai.packageName.toLowerCase(Locale.ROOT);
                if (p.startsWith("com.android.") || p.startsWith("com.google.") || p.startsWith("android")) continue;
                boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean hint = false;
                for (String h : VENDOR_HINTS) if (p.contains(h)) { hint = true; break; }
                if (system || hint) sb.append(ai.packageName).append(system ? "  [system]" : "").append('\n');
            }
        } catch (Exception e) {
            sb.append(e).append('\n');
        }
        return sb.toString();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
