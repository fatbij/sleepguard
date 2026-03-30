package com.sleepguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;
import java.util.Locale;

public class LockScreenActivity extends AppCompatActivity {

    private TextView tvCountdown, tvStatus, tvInstruction;
    private View rootLayout;

    private BroadcastReceiver timerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long secondsLeft = intent.getLongExtra("secondsLeft", 0);
            updateDisplay(secondsLeft);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make activity show on lock screen and keep screen on briefly
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setContentView(R.layout.activity_lock_screen);

        rootLayout    = findViewById(R.id.rootLayout);
        tvCountdown   = findViewById(R.id.tvCountdown);
        tvStatus      = findViewById(R.id.tvStatus);
        tvInstruction = findViewById(R.id.tvInstruction);

        // Tap anywhere opens Calm if in sleep window, does nothing if outside
        rootLayout.setOnClickListener(v -> {
            if (isSleepWindow()) {
                openCalm();
            }
        });

        updateDisplay(TimerService.getSecondsLeft());
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(timerReceiver,
            new IntentFilter(TimerService.ACTION_TICK),
            Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(timerReceiver); } catch (Exception ignored) {}
    }

    private void updateDisplay(long secondsLeft) {
        long mins = secondsLeft / 60;
        long secs = secondsLeft % 60;
        tvCountdown.setText(String.format(Locale.UK, "%02d:%02d", mins, secs));

        if (isSleepWindow()) {
            rootLayout.setBackgroundColor(0xFF1A2B5E);   // deep sleep blue
            tvStatus.setText("Sleep Window");
            tvInstruction.setText("Breathe slowly · tap for sleep story");
        } else {
            rootLayout.setBackgroundColor(0xFFE07B2A);   // warm wake orange
            tvStatus.setText("Good Morning");
            tvInstruction.setText("Time to rise ☀");
        }
    }

    private boolean isSleepWindow() {
        SharedPreferences prefs = getSharedPreferences("sleepguard", Context.MODE_PRIVATE);
        int sleepH = prefs.getInt("sleepHour",   22);
        int sleepM = prefs.getInt("sleepMinute",  30);
        int wakeH  = prefs.getInt("wakeHour",      7);
        int wakeM  = prefs.getInt("wakeMinute",    0);

        Calendar now   = Calendar.getInstance();
        int nowMins    = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int sleepMins  = sleepH * 60 + sleepM;
        int wakeMins   = wakeH  * 60 + wakeM;

        // Handle overnight window (e.g. 22:30 → 07:00)
        if (sleepMins > wakeMins) {
            return nowMins >= sleepMins || nowMins < wakeMins;
        } else {
            return nowMins >= sleepMins && nowMins < wakeMins;
        }
    }

    private void openCalm() {
        // Try to open Calm app; fall back to Play Store
        Intent calm = getPackageManager().getLaunchIntentForPackage("com.calm.android");
        if (calm != null) {
            calm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(calm);
        } else {
            Intent store = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.calm.android"));
            store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(store);
        }
    }
}
