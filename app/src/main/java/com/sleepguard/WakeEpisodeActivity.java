package com.sleepguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import java.util.Locale;

public class WakeEpisodeActivity extends AppCompatActivity {

    // 3 phases only — Breathing → Resting → Leave room
    private static final int PHASE_BREATHING = 0;
    private static final int PHASE_RESTING   = 1;
    private static final int PHASE_LEAVEBED  = 2;

    private TextView tvPhaseTitle, tvElapsed, tvSleepyAgain, tvLeaveBed;
    private LottieAnimationView lottieIcon;
    private View dot1, dot2, dot3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startTimeMs;
    private int currentPhase = -1;
    private SharedPreferences prefs;

    // Phase boundary timestamps in seconds from start
    private long breathingEndSec;   // end of breathing phase
    private long restingEndSec;     // end of resting phase → show leave room

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            long elapsed = (System.currentTimeMillis() - startTimeMs) / 1000;
            updateDisplay(elapsed);
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON   |
            WindowManager.LayoutParams.FLAG_FULLSCREEN);

        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE          |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN      |
            View.SYSTEM_UI_FLAG_FULLSCREEN             |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION        |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_wake_episode);

        prefs = getSharedPreferences("sleepguard", Context.MODE_PRIVATE);
        loadThresholds();

        startTimeMs = prefs.getLong("wakeEpisodeStart", 0);
        if (startTimeMs == 0) {
            startTimeMs = System.currentTimeMillis();
            prefs.edit().putLong("wakeEpisodeStart", startTimeMs).apply();
        }

        tvPhaseTitle  = findViewById(R.id.tvPhaseTitle);
        tvElapsed     = findViewById(R.id.tvElapsed);
        tvSleepyAgain = findViewById(R.id.tvSleepyAgain);
        tvLeaveBed    = findViewById(R.id.tvLeaveBed);
        lottieIcon    = findViewById(R.id.lottieIcon);
        dot1          = findViewById(R.id.dot1);
        dot2          = findViewById(R.id.dot2);
        dot3          = findViewById(R.id.dot3);


        // Only 3 phases — hide dot4


        // Start Lottie breathing icon
        lottieIcon.setMinAndMaxFrame(0, 160);
        lottieIcon.setRepeatCount(LottieDrawable.INFINITE);
        lottieIcon.playAnimation();

        // "Fell asleep again" — return to lock screen
        tvSleepyAgain.setOnClickListener(v -> {
            prefs.edit().remove("wakeEpisodeStart").apply();
            LockScreenActivity.wakeEpisodeActive = false;
            handler.removeCallbacks(ticker);
            finish();
        });

        // "Leave room" — dismiss entirely
        tvLeaveBed.setOnClickListener(v -> {
            prefs.edit().remove("wakeEpisodeStart").apply();
            LockScreenActivity.wakeEpisodeActive = false;
            handler.removeCallbacks(ticker);
            finish();
        });

        // Start ticking immediately
        handler.post(ticker);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(ticker);
    }

    private void loadThresholds() {
        int breathingMins = prefs.getInt("breathingMins", 5);
        int restingMins   = prefs.getInt("restingMins",  10);

        breathingEndSec = breathingMins * 60L;
        restingEndSec   = breathingEndSec + (restingMins * 60L);
        // After restingEndSec → PHASE_LEAVEBED immediately
    }

    private void updateDisplay(long elapsedSeconds) {
        // Update clock
        long mins = elapsedSeconds / 60;
        long secs = elapsedSeconds % 60;
        tvElapsed.setText(String.format(Locale.UK, "%d:%02d", mins, secs));

        // Determine phase
        int phase;
        if      (elapsedSeconds < breathingEndSec) phase = PHASE_BREATHING;
        else if (elapsedSeconds < restingEndSec)   phase = PHASE_RESTING;
        else                                       phase = PHASE_LEAVEBED;

        // Only trigger UI changes on phase transitions
        if (phase != currentPhase) {
            currentPhase = phase;
            onPhaseChanged(phase);
            updateDots(phase);
        }

        // Show leave room button once we hit that phase
        if (phase == PHASE_LEAVEBED) {
            tvLeaveBed.setVisibility(View.VISIBLE);
        }
    }

    private void onPhaseChanged(int phase) {
        switch (phase) {
            case PHASE_BREATHING:
                tvPhaseTitle.setText("Breathe slowly");
                tvLeaveBed.setVisibility(View.GONE);
                break;
            case PHASE_RESTING:
                tvPhaseTitle.setText("Rest quietly");
                tvLeaveBed.setVisibility(View.GONE);
                break;
            case PHASE_LEAVEBED:
                tvPhaseTitle.setText("Time to leave the bed");
                tvLeaveBed.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void updateDots(int phase) {
        // Reset all to inactive
        dot1.setBackgroundResource(R.drawable.dot_inactive);
        dot2.setBackgroundResource(R.drawable.dot_inactive);
        dot3.setBackgroundResource(R.drawable.dot_inactive);

        // Fill dots up to and including current phase (fall-through intentional)
        switch (phase) {
            case PHASE_LEAVEBED:  dot3.setBackgroundResource(R.drawable.dot_active);
            // fall through
            case PHASE_RESTING:   dot2.setBackgroundResource(R.drawable.dot_active);
            // fall through
            case PHASE_BREATHING: dot1.setBackgroundResource(R.drawable.dot_active);
                break;
        }
    }
}
