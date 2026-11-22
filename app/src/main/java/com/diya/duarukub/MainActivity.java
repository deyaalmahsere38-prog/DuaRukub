package com.diya.duarukub;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 1;
    private static final int REQ_LOCATION = 2;

    private TextView fileLabel;
    private TextView btnLabel;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Features.apply(this);

        int pad = dp(20);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("دعاء الركوب");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // ---- Master controls ----
        header("التحكم العام");
        Switch master = toggle("كل التطبيق شغّال", Prefs.master(this), Prefs.KEY_MASTER);
        Switch duaOn = toggle("دعاء الركوب التلقائي", Prefs.get(this).getBoolean(Prefs.KEY_ENABLED, true), Prefs.KEY_ENABLED);
        Switch btnOn = toggle("كبسة الستيرنج بتفتح تطبيقي", Prefs.get(this).getBoolean(Prefs.KEY_BTN_ENABLED, true), Prefs.KEY_BTN_ENABLED);
        TextView masterNote = new TextView(this);
        masterNote.setText("لما تطفي إشي، بيرجع زي ما كان قبل التطبيق: الكبسة بترجع تفتح Google، والدعاء ما بيشتغل.");
        root.addView(masterNote);

        // ---- File ----
        header("ملف الدعاء");
        fileLabel = new TextView(this);
        fileLabel.setTextSize(16);
        root.addView(fileLabel);
        refreshFileLabel();
        button("اختيار ملف الدعاء (MP3)", v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            startActivityForResult(i, PICK_AUDIO);
        });
        button("تجربة التشغيل", v -> {
            if (Prefs.uri(this) == null) toast("اختار ملف الدعاء أول");
            else DuaPlayer.play(this);
        });
        button("إيقاف", v -> DuaPlayer.stop(this));

        // ---- When ----
        header("متى يشتغل");
        RadioGroup mode = new RadioGroup(this);
        RadioButton wake = new RadioButton(this);
        wake.setId(View.generateViewId());
        wake.setText("أول ما تشتغل الشاشة (بعد التأخير)");
        RadioButton moving = new RadioButton(this);
        moving.setId(View.generateViewId());
        moving.setText("لما السيارة تبلّش تمشي (GPS)");
        mode.addView(wake);
        mode.addView(moving);
        mode.check(Prefs.mode(this) == Prefs.MODE_MOVING ? moving.getId() : wake.getId());
        root.addView(mode);

        CheckBox onBoot = check("عند إقلاع الشاشة من طفي كامل", Prefs.onBoot(this));
        CheckBox onScreen = check("عند صحيان الشاشة من النوم", Prefs.onScreen(this));

        // ---- Numbers ----
        header("الأرقام");
        EditText delay = numberField("التأخير قبل التشغيل (ثواني)", Prefs.delaySec(this));
        EditText cooldown = numberField("ما يعيد الدعاء قبل مرور (دقائق)", Prefs.cooldownMin(this));
        EditText speed = numberField("السرعة اللي بيعتبر فيها السيارة ماشية (كم/س)", Prefs.speedKmh(this));
        EditText volume = numberField("صوت الدعاء % (0 = ما يغيّر الصوت)", Prefs.volumePct(this));

        button("حفظ الإعدادات", v -> {
            boolean isMoving = mode.getCheckedRadioButtonId() == moving.getId();
            Prefs.get(this).edit()
                    .putInt(Prefs.KEY_MODE, isMoving ? Prefs.MODE_MOVING : Prefs.MODE_WAKE)
                    .putBoolean(Prefs.KEY_ON_BOOT, onBoot.isChecked())
                    .putBoolean(Prefs.KEY_ON_SCREEN, onScreen.isChecked())
                    .putInt(Prefs.KEY_DELAY, parse(delay, 4))
                    .putInt(Prefs.KEY_COOLDOWN, parse(cooldown, 10))
                    .putInt(Prefs.KEY_SPEED, Math.max(1, parse(speed, 10)))
                    .putInt(Prefs.KEY_VOLUME, Math.min(100, parse(volume, 0)))
                    .apply();
            if (isMoving && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            }
            toast("انحفظت");
        });

        // ---- Keep alive ----
        header("عشان يضل شغّال");
        button("استثناء التطبيق من توفير البطارية", v -> requestBatteryExemption());
        TextView note = new TextView(this);
        note.setText("إذا شاشتك فيها قائمة تطبيقات بتشتغل مع التشغيل أو Keep alive، ضيف هالتطبيق عليها.");
        root.addView(note);

        // ---- Steering button ----
        header("كبسة الستيرنج (كبسة المساعد)");
        btnLabel = new TextView(this);
        btnLabel.setTextSize(16);
        root.addView(btnLabel);
        refreshBtnLabel();
        button("اختيار التطبيق اللي بتفتحه الكبسة", v -> pickApp());
        button("تفعيل لقط الكبسة (إمكانية الوصول)", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                toast("اختار «كبسة الستيرنج - دعاء الركوب» وفعّله");
            } catch (Exception e) {
                toast("ما قدرت أفتح الإعدادات");
            }
        });
        CheckBox dbl = check("كبستين ورا بعض = يفتح Google بدل التطبيق", Prefs.btnDouble(this));
        dbl.setOnCheckedChangeListener((b, on) ->
                Prefs.get(this).edit().putBoolean(Prefs.KEY_BTN_DOUBLE, on).apply());

        // ---- Brightness schedule ----
        header("السطوع حسب الوقت");
        Switch brightOn = new Switch(this);
        brightOn.setText("تفعيل جدول السطوع");
        brightOn.setTextSize(17);
        brightOn.setChecked(Prefs.get(this).getBoolean(Prefs.KEY_BRIGHT_ON, false));
        root.addView(brightOn);
        EditText dayLv = numberField("سطوع النهار (0 - 20)", Prefs.dayLevel(this));
        EditText nightLv = numberField("سطوع الليل (0 - 20)", Prefs.nightLevel(this));
        EditText dayStart = timeField("بداية النهار (مثلاً 06:00)", Prefs.dayStart(this));
        EditText nightStart = timeField("بداية الليل (مثلاً 17:30)", Prefs.nightStart(this));
        CheckBox alt = check("طريقة إرسال بديلة (جرّبها إذا التجربة ما غيّرت إشي)",
                Prefs.get(this).getBoolean(Prefs.KEY_BRIGHT_ALT, false));
        alt.setOnCheckedChangeListener((b, on) ->
                Prefs.get(this).edit().putBoolean(Prefs.KEY_BRIGHT_ALT, on).apply());
        button("جرّب سطوع النهار", v -> Backlight.apply(this, parse(dayLv, 16), (ok, msg) -> toast(msg)));
        button("جرّب سطوع الليل", v -> Backlight.apply(this, parse(nightLv, 5), (ok, msg) -> toast(msg)));
        TextView brightStatus = new TextView(this);
        brightStatus.setPadding(0, dp(6), 0, dp(6));
        root.addView(brightStatus);
        refreshBrightStatus(brightStatus);
        button("حفظ جدول السطوع", v -> {
            Prefs.get(this).edit()
                    .putBoolean(Prefs.KEY_BRIGHT_ON, brightOn.isChecked())
                    .putInt(Prefs.KEY_BRIGHT_DAY, Math.min(20, parse(dayLv, 16)))
                    .putInt(Prefs.KEY_BRIGHT_NIGHT, Math.min(20, parse(nightLv, 5)))
                    .putInt(Prefs.KEY_DAY_START, parseTime(dayStart, 6 * 60))
                    .putInt(Prefs.KEY_NIGHT_START, parseTime(nightStart, 17 * 60 + 30))
                    .putInt(Prefs.KEY_BRIGHT_LAST, -1)
                    .apply();
            Features.apply(this);
            if (Prefs.brightOn(this)) {
                int lvNow = Backlight.scheduledLevel(this);
                Backlight.apply(this, lvNow, (ok, msg) -> {
                    if (ok) BrightAlarm.markApplied(this, lvNow);
                    toast("انحفظ. " + (Backlight.isDay(this) ? "هلأ نهار: " : "هلأ ليل: ") + msg);
                    refreshBrightStatus(brightStatus);
                });
            } else {
                toast("انحفظ");
            }
        });

        // ---- Brightness diagnostics ----
        header("السطوع");
        button("فحص السطوع (تقرير)", v -> startActivity(new Intent(this, DiagActivity.class)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_LOCATION && (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)) {
            toast("بدون صلاحية الموقع رح يشتغل الدعاء أول ما تشتغل الشاشة");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_AUDIO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            toast("ما قدرت أحفظ صلاحية الملف، انسخه على ذاكرة الشاشة واختاره من هناك");
            return;
        }
        Prefs.get(this).edit().putString(Prefs.KEY_URI, uri.toString()).apply();
        refreshFileLabel();
    }

    private void requestBatteryExemption() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            toast("مستثنى أصلاً");
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            toast("الشاشة ما بتدعم هالإعداد");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (btnLabel != null) refreshBtnLabel();
    }

    private void refreshBtnLabel() {
        String pkg = Prefs.btnPkg(this);
        String name = "ما في تطبيق مختار";
        if (pkg != null) {
            try {
                name = getPackageManager().getApplicationLabel(
                        getPackageManager().getApplicationInfo(pkg, 0)).toString();
            } catch (Exception e) {
                name = pkg;
            }
        }
        String last = Prefs.btnLastAction(this);
        btnLabel.setText("بتفتح: " + name + "\nآخر كبسة وصلت: " + (last == null ? "لسا ما وصل إشي" : last));
    }

    private void pickApp() {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = getPackageManager().queryIntentActivities(q, 0);
        List<String[]> apps = new ArrayList<>();
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            apps.add(new String[]{ri.loadLabel(getPackageManager()).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i)[0];
        new AlertDialog.Builder(this)
                .setTitle("اختار التطبيق")
                .setItems(labels, (d, which) -> {
                    Prefs.get(this).edit().putString(Prefs.KEY_BTN_PKG, apps.get(which)[1]).apply();
                    refreshBtnLabel();
                })
                .show();
    }

    private void refreshFileLabel() {
        String u = Prefs.uri(this);
        fileLabel.setText(u == null ? "ما في ملف مختار" : "الملف: " + Uri.parse(u).getLastPathSegment());
    }

    /** A switch that saves immediately and re-applies which features are active. */
    private Switch toggle(String text, boolean value, String key) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextSize(17);
        s.setPadding(0, dp(8), 0, dp(8));
        s.setChecked(value);
        s.setOnCheckedChangeListener((b, on) -> {
            Prefs.get(this).edit().putBoolean(key, on).apply();
            Features.apply(this);
            toast(on ? "اشتغل" : "انطفى");
        });
        root.addView(s);
        return s;
    }

    private void header(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(19);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(22), 0, dp(6));
        root.addView(t);
    }

    private void button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        root.addView(b);
    }

    private CheckBox check(String text, boolean value) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setChecked(value);
        root.addView(c);
        return c;
    }

    private EditText numberField(String label, int value) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setPadding(0, dp(8), 0, 0);
        root.addView(t);
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(value));
        root.addView(e);
        return e;
    }

    private void refreshBrightStatus(TextView t) {
        String fmt = "%02d:%02d";
        int ds = Prefs.dayStart(this), ns = Prefs.nightStart(this);
        long lastT = Prefs.get(this).getLong(Prefs.KEY_BRIGHT_LAST_TIME, 0);
        String last = lastT == 0 ? "لسا" :
                android.text.format.DateFormat.format("HH:mm", lastT) + " (سطوع " + Prefs.get(this).getInt(Prefs.KEY_BRIGHT_LAST, -1) + ")";
        t.setText("الجدول: نهار من " + String.format(java.util.Locale.ROOT, fmt, ds / 60, ds % 60)
                + " لـ " + String.format(java.util.Locale.ROOT, fmt, ns / 60, ns % 60)
                + "\nهلأ: " + (Backlight.isDay(this) ? "نهار" : "ليل") + " — آخر تطبيق: " + last
                + (Prefs.brightOn(this) ? "" : "\n(الجدول مطفي)"));
    }

    private EditText timeField(String label, int minutes) {
        EditText e = numberField(label, 0);
        e.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        e.setText(String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60));
        return e;
    }

    private static int parseTime(EditText e, int fallback) {
        try {
            String s = e.getText().toString().trim();
            if (s.matches("\\d{3,4}")) s = s.substring(0, s.length() - 2) + ":" + s.substring(s.length() - 2);
            String[] p = s.split("[:.]");
            int h = Integer.parseInt(p[0].trim());
            int m = p.length > 1 ? Integer.parseInt(p[1].trim()) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) return fallback;
            return h * 60 + m;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static int parse(EditText e, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(e.getText().toString().trim()));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
