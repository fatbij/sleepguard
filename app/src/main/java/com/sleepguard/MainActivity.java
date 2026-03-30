package com.sleepguard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private int sleepHour = 22, sleepMinute = 30;   // default: 10:30 PM
    private int wakeHour  = 7,  wakeMinute  = 0;    // default: 7:00 AM

    private TextView tvSleepTime, tvWakeTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("sleepguard", Context.MODE_PRIVATE);

        // Load saved times
        sleepHour   = prefs.getInt("sleepHour",   22);
        sleepMinute = prefs.getInt("sleepMinute",  30);
        wakeHour    = prefs.getInt("wakeHour",      7);
        wakeMinute  = prefs.getInt("wakeMinute",    0);

        tvSleepTime = findViewById(R.id.tvSleepTime);
        tvWakeTime  = findViewById(R.id.tvWakeTime);
        updateLabels();

        findViewById(R.id.btnSetSleep).setOnClickListener(v -> {
            new TimePickerDialog(this, (view, h, m) -> {
                sleepHour = h; sleepMinute = m;
                prefs.edit().putInt("sleepHour", h).putInt("sleepMinute", m).apply();
                updateLabels();
            }, sleepHour, sleepMinute, true).show();
        });

        findViewById(R.id.btnSetWake).setOnClickListener(v -> {
            new TimePickerDialog(this, (view, h, m) -> {
                wakeHour = h; wakeMinute = m;
                prefs.edit().putInt("wakeHour", h).putInt("wakeMinute", m).apply();
                updateLabels();
            }, wakeHour, wakeMinute, true).show();
        });

        findViewById(R.id.btnActivate).setOnClickListener(v -> {
            Intent service = new Intent(this, TimerService.class);
            service.setAction("START");
            startForegroundService(service);
            Toast.makeText(this, "SleepGuard is active. Sleep well 🌙", Toast.LENGTH_LONG).show();
            finish();
        });

        findViewById(R.id.btnStop).setOnClickListener(v -> {
            Intent service = new Intent(this, TimerService.class);
            service.setAction("STOP");
            startService(service);
            Toast.makeText(this, "SleepGuard stopped.", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateLabels() {
        tvSleepTime.setText(String.format(Locale.UK, "%02d:%02d", sleepHour, sleepMinute));
        tvWakeTime.setText(String.format(Locale.UK,  "%02d:%02d", wakeHour,  wakeMinute));
    }
}
