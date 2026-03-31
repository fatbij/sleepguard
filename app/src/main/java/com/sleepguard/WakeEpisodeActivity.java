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
import java.util.Locale;

public class WakeEpisodeActivity extends AppCompatActivity {

    private static final int PHASE_BREATHING  = 0;
    private static final int PHASE_RESTING    = 1;
    private static final int PHASE_LEAVE_BED  = 2;
    private static final int PHASE_ESCALATION = 3;

    private TextView tvPhaseTitle, tvPhaseSubtitle, tvElapsed,
                     tvSleepyAgain, tvLeaveBed, tvWakeIcon;
    private View dot1, dot2, dot3, dot4;

    private Handler handler = new Handler(Looper.getMainLooper());
    private long startTimeMs;
    private int currentPhase = -1;
    private SharedPreferences prefs;

    private long breathingEnd;
    private long restingEnd;
    private long leaveBedStart;
    private long escalationStart;

    private Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long elapsedSeconds = (System.currentTimeMillis() - startTimeMs) / 1000;
            updateDisplay(elapsedSeconds);
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON  |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        setContentView(R.layout.activity_wake_episode);

        prefs = getSharedPreferences("sleepguard", Context.MODE_PRIVATE);
        loadThresholds();

        startTimeMs = System.currentTimeMillis();

        tvPhaseTitle    = findViewById(R.id.tvPhaseTitle);
        tvPhaseSubtitle = findViewById(R.id.tvPhaseSubtitle);
        tvElapsed       = findViewById(R.id.tvElapsed);
        tvSleepyAgain   = findViewById(R.id.tvSleepyAgain);
        tvLeaveBed      = findViewById(R.id.tvLeaveBed);
        tvWakeIcon      = findViewById(R.id.tvWakeIcon);
        dot1            = findViewById(R.id.dot1);
        dot2            = findViewById(R.id.dot2);
        dot3            = findViewById(R.id.dot3);
        dot4            = findViewById(R.id.dot4);

        // Set corrected button label
        tvLeaveBed.setText("A change of scene may help");

        // I'm sleepy again — end episode
        tvSleepyAgain.setOnClickListener(v -> {
            handler.removeCallbacks(ticker);
            finish();
        });

        // Leave bed — end episode
        tvLeaveBed.setOnClickListener(v -> {
            handler.removeCallbacks(ticker);
            finish();
        });

        handler.post(ticker);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(ticker);
    }

    private void loadThresholds() {
        int breathingMins  = prefs.getInt("breathingMins",  5);
        int restingMins    = prefs.getInt("restingMins",   10);
        int leaveBedMins   = prefs.getInt("leaveBedMins",  15);
        int escalationMins = prefs.getInt("escalationMins",20);

        breathingEnd    = breathingMins * 60L;
        restingEnd      = (breathingMins + restingMins) * 60L;  // FIX: drives phase 2 boundary
        leaveBedStart   = leaveBedMins * 60L;
        escalationStart = escalationMins * 60L;
    }

    private void updateDisplay(long elapsedSeconds) {
        long mins = elapsedSeconds / 60;
        long secs = elapsedSeconds % 60;
        tvElapsed.setText(String.format(Locale.UK, "%d:%02d", mins, secs));

        // FIX: use restingEnd for phase 2 boundary, not leaveBedStart
        int phase;
        if (elapsedSeconds < breathingEnd) {
            phase = PHASE_BREATHING;
        } else if (elapsedSeconds < restingEnd) {
            phase = PHASE_RESTING;
        } else if (elapsedSeconds < escalationStart) {
            phase = PHASE_LEAVE_BED;
        } else {
            phase = PHASE_ESCALATION;
        }

        if (phase != currentPhase) {
            currentPhase = phase;
            onPhaseChanged(phase);
        }

        updateDots(phase);

        if (elapsedSeconds >= leaveBedStart) {
            tvLeaveBed.setVisibility(View.VISIBLE);
        }

        // FIX: remove KEEP_SCREEN_ON at escalation so screen can dim naturally
        if (phase == PHASE_ESCALATION) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void onPhaseChanged(int phase) {
        switch (phase) {
            case PHASE_BREATHING:
                tvPhaseTitle.setText("Breathe slowly");
                tvPhaseSubtitle.setText("No need to check the clock");
                tvWakeIcon.setText("🌙");
                break;
            case PHASE_RESTING:
                tvPhaseTitle.setText("Rest quietly");
                tvPhaseSubtitle.setText("Let sleep return naturally");
                tvWakeIcon.setText("🌙");
                break;
            case PHASE_LEAVE_BED:
                tvPhaseTitle.setText("Still awake?");
                tvPhaseSubtitle.setText("It may help to leave the bed now");
                tvWakeIcon.setText("🌑");
                tvLeaveBed.setVisibility(View.VISIBLE);
                break;
            case PHASE_ESCALATION:
                tvPhaseTitle.setText("Leave the room");
                tvPhaseSubtitle.setText("Return only when you feel sleepy again");
                tvWakeIcon.setText("🌑");
                break;
        }
    }

    private void updateDots(int phase) {
        dot1.setBackgroundResource(R.drawable.dot_inactive);
        dot2.setBackgroundResource(R.drawable.dot_inactive);
        dot3.setBackgroundResource(R.drawable.dot_inactive);
        dot4.setBackgroundResource(R.drawable.dot_inactive);

        switch (phase) {
            case PHASE_ESCALATION:
                dot4.setBackgroundResource(R.drawable.dot_active);
                // fall through
            case PHASE_LEAVE_BED:
                dot3.setBackgroundResource(R.drawable.dot_active);
                // fall through
            case PHASE_RESTING:
                dot2.setBackgroundResource(R.drawable.dot_active);
                // fall through
            case PHASE_BREATHING:
                dot1.setBackgroundResource(R.drawable.dot_active);
                break;
        }
    }
}