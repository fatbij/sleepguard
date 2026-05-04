package com.sleepguard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.airbnb.lottie.LottieAnimationView;
import com.sleepguard.db.SleepDatabase;
import com.sleepguard.db.SleepSession;
import com.sleepguard.db.WakeEpisodeRecord;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class WakeEpisodeActivity extends AppCompatActivity {

    private static final int PHASE_BREATHING = 0;
    private static final int PHASE_RESTING   = 1;
    private static final int PHASE_LEAVEBED  = 2;

    private static final float NIGHT_FRAME  = 165f;
    private static final float TOTAL_FRAMES = 330f;

    private final Executor executor = Executors.newSingleThreadExecutor();

    private TextView tvPhaseTitle, tvElapsed, tvSleepyAgain, tvLeaveBed;
    private LottieAnimationView lottieBackground;
    private View dot1, dot2, dot3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startTimeMs;
    private int currentPhase = -1;
    private SharedPreferences prefs;

    private long breathingEndSec;
    private long restingEndSec;

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController wic = getWindow().getInsetsController();
            if (wic != null) {
                wic.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                wic.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE          |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN      |
                View.SYSTEM_UI_FLAG_FULLSCREEN             |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION        |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        setContentView(R.layout.activity_wake_episode);

        prefs = getSharedPreferences("sleepguard", Context.MODE_PRIVATE);
        loadThresholds();

        startTimeMs = prefs.getLong("wakeEpisodeStart", 0);
        if (startTimeMs == 0) {
            startTimeMs = System.currentTimeMillis();
            prefs.edit().putLong("wakeEpisodeStart", startTimeMs).apply();
            recordEpisodeStart(startTimeMs);
        }

        tvPhaseTitle  = findViewById(R.id.tvPhaseTitle);
        tvElapsed     = findViewById(R.id.tvElapsed);
        tvSleepyAgain = findViewById(R.id.tvSleepyAgain);
        tvLeaveBed    = findViewById(R.id.tvLeaveBed);
        dot1          = findViewById(R.id.dot1);
        dot2          = findViewById(R.id.dot2);
        dot3          = findViewById(R.id.dot3);

        lottieBackground = findViewById(R.id.lottieBackground);
        lottieBackground.addLottieOnCompositionLoadedListener(
            c -> lottieBackground.setProgress(NIGHT_FRAME / TOTAL_FRAMES));

        tvSleepyAgain.setOnClickListener(v -> {
            finishEpisode("fell_asleep");
            prefs.edit().remove("wakeEpisodeStart").apply();
            LockScreenActivity.wakeEpisodeActive = false;
            handler.removeCallbacks(ticker);
            finish(); // pops back to LockScreenActivity which is kept in the task stack
        });

        tvLeaveBed.setOnClickListener(v -> {
            finishEpisode("left_bed");
            prefs.edit().remove("wakeEpisodeStart").apply();
            LockScreenActivity.wakeEpisodeActive = false;
            handler.removeCallbacks(ticker);
            finish(); // pops back to LockScreenActivity which is kept in the task stack
        });

        handler.post(ticker);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(ticker);
    }

    private void recordEpisodeStart(long startMs) {
        executor.execute(() -> {
            SleepDatabase db = SleepDatabase.getInstance(this);
            SleepSession active = db.sleepDao().getActiveSession();
            if (active == null) return;
            if (db.sleepDao().getActiveEpisode(active.id) != null) return;
            WakeEpisodeRecord ep = new WakeEpisodeRecord();
            ep.sessionId = active.id;
            ep.startMs   = startMs;
            ep.endMs     = 0;
            ep.outcome   = "";
            db.sleepDao().insertEpisode(ep);
        });
    }

    private void finishEpisode(String outcome) {
        long endMs = System.currentTimeMillis();
        executor.execute(() -> {
            SleepDatabase db = SleepDatabase.getInstance(this);
            SleepSession active = db.sleepDao().getActiveSession();
            if (active == null) return;
            WakeEpisodeRecord ep = db.sleepDao().getActiveEpisode(active.id);
            if (ep != null) {
                ep.endMs   = endMs;
                ep.outcome = outcome;
                db.sleepDao().updateEpisode(ep);
            }
        });
    }

    private void loadThresholds() {
        int breathingMins = prefs.getInt("breathingMins", 5);
        int restingMins   = prefs.getInt("restingMins",  10);
        breathingEndSec   = breathingMins * 60L;
        restingEndSec     = breathingEndSec + (restingMins * 60L);
    }

    private void updateDisplay(long elapsedSeconds) {
        long mins = elapsedSeconds / 60, secs = elapsedSeconds % 60;
        tvElapsed.setText(String.format(Locale.UK, "%d:%02d", mins, secs));

        int phase;
        if      (elapsedSeconds < breathingEndSec) phase = PHASE_BREATHING;
        else if (elapsedSeconds < restingEndSec)   phase = PHASE_RESTING;
        else                                       phase = PHASE_LEAVEBED;

        if (phase != currentPhase) {
            currentPhase = phase;
            onPhaseChanged(phase);
            updateDots(phase);
        }
    }

    private void onPhaseChanged(int phase) {
        switch (phase) {
            case PHASE_BREATHING:
                tvPhaseTitle.setText("Breathe slowly");
                tvSleepyAgain.setVisibility(View.VISIBLE);
                tvLeaveBed.setVisibility(View.GONE);
                break;
            case PHASE_RESTING:
                tvPhaseTitle.setText("Rest quietly");
                tvSleepyAgain.setVisibility(View.VISIBLE);
                tvLeaveBed.setVisibility(View.GONE);
                break;
            case PHASE_LEAVEBED:
                tvPhaseTitle.setText("Time to leave the bed");
                tvSleepyAgain.setVisibility(View.GONE);
                tvLeaveBed.setVisibility(View.VISIBLE);
                break;
        }
        slideInFromRight(tvPhaseTitle);
    }

    private void slideInFromRight(View v) {
        float screenW = getResources().getDisplayMetrics().widthPixels;
        v.setTranslationX(screenW);
        v.animate()
            .translationX(0f)
            .setDuration(350)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    private void updateDots(int phase) {
        dot1.setBackgroundResource(R.drawable.dot_inactive);
        dot2.setBackgroundResource(R.drawable.dot_inactive);
        dot3.setBackgroundResource(R.drawable.dot_inactive);
        switch (phase) {
            case PHASE_LEAVEBED:  dot3.setBackgroundResource(R.drawable.dot_active);
            case PHASE_RESTING:   dot2.setBackgroundResource(R.drawable.dot_active);
            case PHASE_BREATHING: dot1.setBackgroundResource(R.drawable.dot_active);
                break;
        }
    }
}
