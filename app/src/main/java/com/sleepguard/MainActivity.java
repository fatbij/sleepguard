package com.sleepguard;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

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

        sleepHour   = prefs.getInt("sleepHour",   22);
        sleepMinute = prefs.getInt("sleepMinute",  30);
        wakeHour    = prefs.getInt("wakeHour",      7);
        wakeMinute  = prefs.getInt("wakeMinute",    0);

        tvSleepTime = findViewById(R.id.tvSleepTime);
        tvWakeTime  = findViewById(R.id.tvWakeTime);
        updateTimeLabels();

        tvSleepTime.setOnClickListener(v -> {
            new TimePickerDialog(this, (view, h, m) -> {
                sleepHour = h; sleepMinute = m;
                prefs.edit().putInt("sleepHour", h).putInt("sleepMinute", m).apply();
                updateTimeLabels();
            }, sleepHour, sleepMinute, true).show();
        });

        tvWakeTime.setOnClickListener(v -> {
            new TimePickerDialog(this, (view, h, m) -> {
                wakeHour = h; wakeMinute = m;
                prefs.edit().putInt("wakeHour", h).putInt("wakeMinute", m).apply();
                updateTimeLabels();
            }, wakeHour, wakeMinute, true).show();
        });

        tvBreathingMins = findViewById(R.id.tvBreathingMins);
        tvRestingMins   = findViewById(R.id.tvRestingMins);
        updatePlanLabels();

        tvBreathingMins.setOnClickListener(v ->
                showNumberPicker("Breathing minutes", "breathingMins", 1, 30, tvBreathingMins));
        tvRestingMins.setOnClickListener(v ->
                showNumberPicker("Resting minutes", "restingMins", 1, 60, tvRestingMins));

        findViewById(R.id.btnActivate).setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 1234);
                Toast.makeText(this,
                        "Please allow Display over other apps then tap Activate again",
                        Toast.LENGTH_LONG).show();
                return;
            }
            startTimerService();
        });

        findViewById(R.id.btnStop).setOnClickListener(v -> {
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
                .show();
        });
    }

    private void showNumberPicker(String title, String prefKey, int min, int max, TextView label) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        android.widget.NumberPicker picker = new android.widget.NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(prefs.getInt(prefKey, prefKey.equals("breathingMins") ? 5 : 10));
        picker.setWrapSelectorWheel(false);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setPadding(0, 32, 0, 32);
        layout.addView(picker);
        builder.setView(layout);

        builder.setPositiveButton("Done", (dialog, which) -> {
            int val = picker.getValue();
            prefs.edit().putInt(prefKey, val).apply();
            updatePlanLabels();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updatePlanLabels() {
        tvBreathingMins.setText(prefs.getInt("breathingMins", 5)  + " mins");
        tvRestingMins.setText(prefs.getInt("restingMins",    10) + " mins");
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
        if (requestCode == 1234 && Settings.canDrawOverlays(this)) {
            startTimerService();
        }
    }

    private void updateTimeLabels() {
        tvSleepTime.setText(String.format(Locale.UK, "%02d:%02d", sleepHour, sleepMinute));
        tvWakeTime.setText(String.format(Locale.UK,  "%02d:%02d", wakeHour,  wakeMinute));
    }
}