package com.sleepguard;

import android.content.Context;
import android.content.Intent;
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

    private final Executor executor = Executors.newSingleThreadExecutor();

    private TextView tvPhaseTitle, tvPhaseSubtitle, tvElapsed, tvSleepyAgain, tvLeaveBed;
    private LottieAnimationView lottieIcon;
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
            recordEpisodeStart(startTimeMs);
        }

        tvPhaseTitle    = findViewById(R.id.tvPhaseTitle);
        tvPhaseSubtitle = findViewById(R.id.tvPhaseSubtitle);
        tvElapsed       = findViewById(R.id.tvElapsed);
        tvSleepyAgain   = findViewById(R.id.tvSleepyAgain);
        tvLeaveBed      = findViewById(R.id.tvLeaveBed);
        lottieIcon      = findViewById(R.id.lottieIcon);
        dot1            = findViewById(R.id.dot1);
        dot2            = findViewById(R.id.dot2);
        dot3            = findViewById(R.id.dot3);

        lottieIcon.setMinAndMaxFrame(0, 160);
        lottieIcon.setSpeed(0.267f);
        lottieIcon.setRepeatMode(LottieDrawable.RESTART);
        lottieIcon.setRepeatCount(LottieDrawable.INFINITE);
        lottieIcon.playAnimation();

        final boolean[] crossedMid = {false};
        tvPhaseSubtitle.setText("Follow the clouds — Inhale");
        lottieIcon.addAnimatorUpdateListener(animation -> {
            float progress = lottieIcon.getProgress();
            if (progress >= 0.5f && !crossedMid[0]) {
                crossedMid[0] = true;
                if (currentPhase == PHASE_BREATHING)
                    tvPhaseSubtitle.setText("Follow the clouds — Exhale");
            } else if (progress < 0.1f && crossedMid[0]) {
                crossedMid[0] = false;
                if (currentPhase == PHASE_BREATHING)
                    tvPhaseSubtitle.setText("Follow the clouds — Inhale");
            }
        });

        tvSleepyAgain.setOnClickListener(v -> {
            finishEpisode("fell_asleep");
            prefs.edit().remove("wakeEpisodeStart").apply();
            LockScreenActivity.wakeEpisodeActive = false;
            handler.removeCallbacks(ticker);
            finish();
        });

        tvLeaveBed.setOnClickListener(v -> {
            finishEpisode("left_bed");
            prefs.edit().remove("wakeEpisodeStart").apply();
            LockScreenActivity.wakeEpisodeActive = false;
            handler.removeCallbacks(ticker);
            Intent lockScreen = new Intent(this, LockScreenActivity.class);
            lockScreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(lockScreen);
            finish();
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

        if (phase == PHASE_LEAVEBED) tvLeaveBed.setVisibility(View.VISIBLE);
    }

    private void onPhaseChanged(int phase) {
        switch (phase) {
            case PHASE_BREATHING:
                tvPhaseTitle.setText("Breathe slowly");
                tvSleepyAgain.setVisibility(View.VISIBLE);
                tvLeaveBed.setVisibility(View.GONE);
                lottieIcon.setSpeed(0.267f);
                lottieIcon.resumeAnimation();
                break;
            case PHASE_RESTING:
                tvPhaseTitle.setText("Rest quietly");
                tvSleepyAgain.setVisibility(View.VISIBLE);
                tvLeaveBed.setVisibility(View.GONE);
                lottieIcon.setSpeed(0.3f);
                break;
            case PHASE_LEAVEBED:
                tvPhaseTitle.setText("Time to leave the bed");
                tvSleepyAgain.setVisibility(View.GONE);
                tvLeaveBed.setVisibility(View.VISIBLE);
                lottieIcon.pauseAnimation();
                break;
        }
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
