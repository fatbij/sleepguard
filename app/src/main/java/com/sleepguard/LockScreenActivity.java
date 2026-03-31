package com.sleepguard;

import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;
import java.util.Locale;

public class LockScreenActivity extends AppCompatActivity {

    private TextView tvCountdown, tvCycleLabel, tvStatus, tvUnlock, tvStop,
                     tvIcon, tvMessage, tvSettingsSleep, tvSettingsWake;
    private LinearLayout settingsPanel;
    private ImageButton btnGear;
    private View rootLayout;
    private Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private static final long SUPPRESS_MS = 5_000;
    private static long suppressUntil = 0;

    private int sleepHour, sleepMinute, wakeHour, wakeMinute;

    private Runnable clockUpdater = new Runnable() {
        @Override
        public void run() {
            updateDisplay(TimerService.getSecondsLeft());
            handler.postDelayed(this, 1000);
        }
    };

    private BroadcastReceiver timerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateDisplay(intent.getLongExtra("secondsLeft", 0));
        }
    };

    public static boolean isSuppressed() {
        return System.currentTimeMillis() < suppressUntil;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isSuppressed()) { finish(); return; }

        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        setContentView(R.layout.activity_lock_screen);

        prefs = getSharedPreferences("sleepguard", Context.MODE_PRIVATE);
        loadTimes();

        rootLayout      = findViewById(R.id.rootLayout);
        tvCountdown     = findViewById(R.id.tvCountdown);
        tvCycleLabel    = findViewById(R.id.tvCycleLabel);
        tvStatus        = findViewById(R.id.tvStatus);
        tvUnlock        = findViewById(R.id.tvUnlock);
        tvStop          = findViewById(R.id.tvStop);
        tvIcon          = findViewById(R.id.tvIcon);
        tvMessage       = findViewById(R.id.tvMessage);
        tvSettingsSleep = findViewById(R.id.tvSettingsSleep);
        tvSettingsWake  = findViewById(R.id.tvSettingsWake);
        settingsPanel   = findViewById(R.id.settingsPanel);
        btnGear         = findViewById(R.id.btnGear);

        updateSettingsLabels();

        // Unlock — suppress 5s then dismiss
        tvUnlock.setOnClickListener(v -> {
            suppressUntil = System.currentTimeMillis() + SUPPRESS_MS;
            finish();
        });

        // Stop SleepGuard entirely
        tvStop.setOnClickListener(v -> {
            Intent stop = new Intent(this, TimerService.class);
            stop.setAction("STOP");
            startService(stop);
            finish();
        });

        // Gear opens settings
        btnGear.setOnClickListener(v -> {
            settingsPanel.setVisibility(View.VISIBLE);
        });

        // Done closes settings
        findViewById(R.id.tvSettingsDone).setOnClickListener(v -> {
            settingsPanel.setVisibility(View.GONE);
            updateDisplay(TimerService.getSecondsLeft());
        });

        // Tap bedtime to change
        tvSettingsSleep.setOnClickListener(v -> {
            new TimePickerDialog(this, (view, h, m) -> {
                sleepHour = h; sleepMinute = m;
                prefs.edit().putInt("sleepHour", h).putInt("sleepMinute", m).apply();
                updateSettingsLabels();
            }, sleepHour, sleepMinute, true).show();
        });

        // Tap wake time to change
        tvSettingsWake.setOnClickListener(v -> {
            new TimePickerDialog(this, (view, h, m) -> {
                wakeHour = h; wakeMinute = m;
                prefs.edit().putInt("wakeHour", h).putInt("wakeMinute", m).apply();
                updateSettingsLabels();
            }, wakeHour, wakeMinute, true).show();
        });

        updateDisplay(TimerService.getSecondsLeft());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isSuppressed()) { finish(); return; }
        registerReceiver(timerReceiver,
            new IntentFilter(TimerService.ACTION_TICK),
            Context.RECEIVER_NOT_EXPORTED);
        handler.post(clockUpdater);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockUpdater);
        try { unregisterReceiver(timerReceiver); } catch (Exception ignored) {}
    }

    private void loadTimes() {
        sleepHour   = prefs.getInt("sleepHour",   22);
        sleepMinute = prefs.getInt("sleepMinute",  30);
        wakeHour    = prefs.getInt("wakeHour",      7);
        wakeMinute  = prefs.getInt("wakeMinute",    0);
    }

    private void updateSettingsLabels() {
        tvSettingsSleep.setText(String.format(Locale.UK, "%02d:%02d", sleepHour, sleepMinute));
        tvSettingsWake.setText(String.format(Locale.UK,  "%02d:%02d", wakeHour,  wakeMinute));
    }

    private void updateDisplay(long secondsLeft) {
        if (isSleepWindow()) {
            rootLayout.setBackgroundResource(R.drawable.bg_sleep);
            tvIcon.setText("🌙");
            tvStatus.setText("Sleep Window");
            tvStatus.setTextColor(0xFFDDEEFF);
            long mins = secondsLeft / 60;
            long secs = secondsLeft % 60;
            tvCountdown.setText(String.format(Locale.UK, "%02d:%02d", mins, secs));
            tvCountdown.setVisibility(View.VISIBLE);
            tvCycleLabel.setVisibility(View.VISIBLE);
            tvMessage.setVisibility(View.GONE);
        } else {
            rootLayout.setBackgroundResource(R.drawable.bg_wake);
            tvIcon.setText("☀️");
            tvStatus.setText("Good Morning");
            tvStatus.setTextColor(0xFFFFF8F0);
            tvCountdown.setVisibility(View.GONE);
            tvCycleLabel.setVisibility(View.GONE);
            tvMessage.setVisibility(View.VISIBLE);
        }
    }

    private boolean isSleepWindow() {
        Calendar now  = Calendar.getInstance();
        int nowMins   = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int sleepMins = sleepHour * 60 + sleepMinute;
        int wakeMins  = wakeHour  * 60 + wakeMinute;

        if (sleepMins > wakeMins) {
            return nowMins >= sleepMins || nowMins < wakeMins;
        } else {
            return nowMins >= sleepMins && nowMins < wakeMins;
        }
    }
}
