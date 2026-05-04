package com.sleepguard;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_OVERLAY       = 1234;
    private static final int REQ_NOTIFICATIONS = 2345;

    private SharedPreferences prefs;
    private int sleepHour = 22, sleepMinute = 30;
    private int wakeHour  = 7,  wakeMinute  = 0;
    private TextView tvSleepTime, tvWakeTime;
    private TextView tvBreathingMins, tvRestingMins;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        prefs = getSharedPreferences("sleepguard", Context.MODE_PRIVATE);

        checkBatteryOptOnStartup();

        sleepHour   = prefs.getInt("sleepHour",   22);
        sleepMinute = prefs.getInt("sleepMinute",  30);
        wakeHour    = prefs.getInt("wakeHour",      7);
        wakeMinute  = prefs.getInt("wakeMinute",    0);

        tvSleepTime = findViewById(R.id.tvSleepTime);
        tvWakeTime  = findViewById(R.id.tvWakeTime);
        updateTimeLabels();

        tvSleepTime.setOnClickListener(v ->
            new TimePickerDialog(this, (view, h, m) -> {
                sleepHour = h; sleepMinute = m;
                prefs.edit().putInt("sleepHour", h).putInt("sleepMinute", m).apply();
                updateTimeLabels();
            }, sleepHour, sleepMinute, true).show());

        tvWakeTime.setOnClickListener(v ->
            new TimePickerDialog(this, (view, h, m) -> {
                wakeHour = h; wakeMinute = m;
                prefs.edit().putInt("wakeHour", h).putInt("wakeMinute", m).apply();
                updateTimeLabels();
            }, wakeHour, wakeMinute, true).show());

        tvBreathingMins = findViewById(R.id.tvBreathingMins);
        tvRestingMins   = findViewById(R.id.tvRestingMins);
        updatePlanLabels();

        tvBreathingMins.setOnClickListener(v ->
                showNumberPicker("Breathing minutes", "breathingMins", 0, 30, tvBreathingMins));
        tvRestingMins.setOnClickListener(v ->
                showNumberPicker("Resting minutes", "restingMins", 0, 60, tvRestingMins));

        findViewById(R.id.btnActivate).setOnClickListener(v -> checkPermissionsAndStart());

        findViewById(R.id.btnDiary).setOnClickListener(v ->
            startActivity(new Intent(this, SleepLogActivity.class)));

        findViewById(R.id.btnStop).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to stop SleepGuard?")
                .setPositiveButton("Yes, stop", (dialog, which) -> {
                    Intent service = new Intent(this, TimerService.class);
                    service.setAction("STOP");
                    startService(service);
                    prefs.edit().putBoolean("active", false).apply();
                    Toast.makeText(this, "SleepGuard stopped.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No, keep active", null)
                .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Fallback for when the OS killed the process while the user was in overlay settings.
        // onActivityResult won't fire in that case, so we re-check here.
        if (prefs.getBoolean("pendingActivate", false) && Settings.canDrawOverlays(this)) {
            prefs.edit().remove("pendingActivate").apply();
            checkPermissionsAndStart();
        }
    }

    private void checkBatteryOptOnStartup() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        new AlertDialog.Builder(this)
            .setTitle("Allow unrestricted battery use")
            .setMessage("SleepGuard needs to run overnight without being stopped by the system.\n\nTap 'Open settings' and choose Unrestricted for SleepGuard.")
            .setPositiveButton("Open settings", (d, w) -> {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
                } catch (Exception ignored) {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            })
            .setNegativeButton("Not now", null)
            .show();
    }

    private void checkPermissionsAndStart() {
        // Step 1: notification permission (Android 13+).
        // The health foreground service type on Android 14 requires a visible notification,
        // so POST_NOTIFICATIONS must be granted before the service can start.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIFICATIONS);
                return;
            }
        }

        // Step 2: overlay permission.
        if (!Settings.canDrawOverlays(this)) {
            prefs.edit().putBoolean("pendingActivate", true).apply();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
            Toast.makeText(this,
                    "Please allow Display over other apps then tap Activate again",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Step 3: battery optimisation (best-effort — user can skip).
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        }

        prefs.edit().remove("pendingActivate").apply();
        startTimerService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkPermissionsAndStart();
            } else {
                Toast.makeText(this,
                        "Notification permission is required to show the sleep monitor.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showNumberPicker(String title, String prefKey, int min, int max, TextView label) {
        android.widget.NumberPicker picker = new android.widget.NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(prefs.getInt(prefKey, prefKey.equals("breathingMins") ? 5 : 10));
        picker.setWrapSelectorWheel(false);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setPadding(0, 32, 0, 32);
        layout.addView(picker);

        new AlertDialog.Builder(this).setTitle(title).setView(layout)
            .setPositiveButton("Done", (dialog, which) -> {
                prefs.edit().putInt(prefKey, picker.getValue()).apply();
                updatePlanLabels();
            })
            .setNegativeButton("Cancel", null).show();
    }

    private void updatePlanLabels() {
        tvBreathingMins.setText(prefs.getInt("breathingMins", 5)  + " mins");
        tvRestingMins.setText(  prefs.getInt("restingMins",   10) + " mins");
    }

    private void startTimerService() {
        Intent service = new Intent(this, TimerService.class);
        service.setAction("START");
        startForegroundService(service);
        prefs.edit().putBoolean("active", true).apply();
        Toast.makeText(this, "SleepGuard is active. Sleep well 🌙", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY && Settings.canDrawOverlays(this)) {
            prefs.edit().remove("pendingActivate").apply();
            checkPermissionsAndStart();
        }
    }

    private void updateTimeLabels() {
        tvSleepTime.setText(String.format(Locale.UK, "%02d:%02d", sleepHour, sleepMinute));
        tvWakeTime.setText( String.format(Locale.UK, "%02d:%02d", wakeHour,  wakeMinute));
    }
}
