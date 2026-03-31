package com.sleepguard;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Transparent status bar to let gradient show through
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
        updateLabels();

        // Tap bedtime box to pick time
        tvSleepTime.setOnClickListener(v -> {
            new TimePickerDialog(this, (view, h, m) -> {
                sleepHour = h; sleepMinute = m;
                prefs.edit().putInt("sleepHour", h).putInt("sleepMinute", m).apply();
                updateLabels();
            }, sleepHour, sleepMinute, true).show();
        });

        // Tap wake time box to pick time
        tvWakeTime.setOnClickListener(v -> {
            new TimePickerDialog(this, (view, h, m) -> {
                wakeHour = h; wakeMinute = m;
                prefs.edit().putInt("wakeHour", h).putInt("wakeMinute", m).apply();
                updateLabels();
            }, wakeHour, wakeMinute, true).show();
        });

        // Activate
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

        // Stop
        findViewById(R.id.btnStop).setOnClickListener(v -> {
            Intent service = new Intent(this, TimerService.class);
            service.setAction("STOP");
            startService(service);
            prefs.edit().putBoolean("active", false).apply();
            Toast.makeText(this, "SleepGuard stopped.", Toast.LENGTH_SHORT).show();
        });
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
        if (requestCode == 1234) {
            if (Settings.canDrawOverlays(this)) {
                startTimerService();
            } else {
                Toast.makeText(this,
                    "Permission needed for lock screen display",
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateLabels() {
        tvSleepTime.setText(String.format(Locale.UK, "%02d:%02d", sleepHour, sleepMinute));
        tvWakeTime.setText(String.format(Locale.UK,  "%02d:%02d", wakeHour,  wakeMinute));
    }
}
