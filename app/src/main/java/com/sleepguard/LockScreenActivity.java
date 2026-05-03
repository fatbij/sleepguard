package com.sleepguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
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
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.airbnb.lottie.LottieAnimationView;
import com.sleepguard.db.SleepDatabase;
import com.sleepguard.db.SleepSession;
import com.sleepguard.db.WakeEpisodeRecord;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LockScreenActivity extends AppCompatActivity {

    // Day-and-Night-Cycle.json  330 frames @ 60 fps
    private static final float FRAME_MOON_HOLD   = 165f;
    private static final float FRAME_TRANS_START = 165f;
    private static final float FRAME_TRANS_END   = 329f;
    private static final float FRAME_SUN_HOLD    = 329f;
    private static final float TOTAL_FRAMES      = 330f;

    private static final long SUPPRESS_MS = 5_000;
    public  static long    suppressUntil    = 0;
    public  static boolean wakeEpisodeActive = false;
    public  static boolean isShowing         = false;

    // Wake episode phases
    private static final int PHASE_BREATHING = 0;
    private static final int PHASE_RESTING   = 1;
    private static final int PHASE_LEAVEBED  = 2;

    private final Executor executor = Executors.newSingleThreadExecutor();

    // Sleep-mode views
    private LottieAnimationView lottieAnimation;
    private TextView  tvUnlock, tvBeginGently;
    private TextView  tvMorningGreeting, tvMorningMessage;
    private TextView  tvClose;
    private View      settingsPanel;
    private ImageButton btnGear;
    private TextView  tvSettingsSleep, tvSettingsWake;
    private TextView  tvSettingsBreathing, tvSettingsResting;

    // Panels
    private View sleepPanel, wakePanel;

    // Wake episode views
    private TextView tvPhaseTitle, tvElapsed, tvSleepyAgain, tvLeaveBed;
    private View dot1, dot2, dot3;

    // Wake episode state
    private int currentPhase = -1;
    private long breathingEndSec, restingEndSec;
    private long wakeStartMs;
    private Runnable episodeTicker;

    // Lottie transition state
    private final Handler handler         = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private boolean isTransitioning  = false;
    private boolean transitionPlayed = false;
    private boolean firstDisplay     = true;
    private Runnable transitionFallback = null;

    private int sleepHour, sleepMinute, wakeHour, wakeMinute;

    private final Runnable wakeTransitionTrigger = () -> {
        if (!isTransitioning && !transitionPlayed && !isSleepWindow()) {
            playTransition();
        }
    };

    private final BroadcastReceiver timerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            if (!isTransitioning && !wakeEpisodeActive) updateDisplay();
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
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   |
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = getWindow().getDecorView();
        int flags =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE          |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN      |
            View.SYSTEM_UI_FLAG_FULLSCREEN             |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION        |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decor.setSystemUiVisibility(flags);
        decor.setOnSystemUiVisibilityChangeListener(v -> {
            if ((v & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                decor.setSystemUiVisibility(flags);
        });

        setContentView(R.layout.activity_lock_screen);
        prefs = getSharedPreferences("sleepguard", Context.MODE_PRIVATE);
        loadTimes();

        lottieAnimation   = findViewById(R.id.lottieAnimation);
        tvUnlock          = findViewById(R.id.tvUnlock);
        tvBeginGently     = findViewById(R.id.tvBeginGently);
        tvMorningGreeting = findViewById(R.id.tvMorningGreeting);
        tvMorningMessage  = findViewById(R.id.tvMorningMessage);
        tvClose           = findViewById(R.id.tvClose);
        btnGear           = findViewById(R.id.btnGear);
        settingsPanel     = findViewById(R.id.settingsPanel);
        tvSettingsSleep   = findViewById(R.id.tvSettingsSleep);
        tvSettingsWake    = findViewById(R.id.tvSettingsWake);
        tvSettingsBreathing = findViewById(R.id.tvSettingsBreathing);
        tvSettingsResting   = findViewById(R.id.tvSettingsResting);

        sleepPanel    = findViewById(R.id.sleepPanel);
        wakePanel     = findViewById(R.id.wakePanel);
        tvPhaseTitle  = findViewById(R.id.tvPhaseTitle);
        tvElapsed     = findViewById(R.id.tvElapsed);
        tvSleepyAgain = findViewById(R.id.tvSleepyAgain);
        tvLeaveBed    = findViewById(R.id.tvLeaveBed);
        dot1          = findViewById(R.id.dot1);
        dot2          = findViewById(R.id.dot2);
        dot3          = findViewById(R.id.dot3);

        updateSettingsLabels();

        lottieAnimation.addLottieOnCompositionLoadedListener(c -> {
            if (!isTransitioning && !wakeEpisodeActive) updateDisplay();
        });

        tvClose.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Dismiss SleepGuard")
                .setMessage("Are you sure?")
                .setPositiveButton("Yes", (d, w) -> {
                    Intent stop = new Intent(this, TimerService.class);
                    stop.setAction("STOP");
                    startService(stop);
                    suppressUntil = System.currentTimeMillis() + SUPPRESS_MS;
                    finish();
                })
                .setNegativeButton("Cancel", null).show());

        tvUnlock.setOnClickListener(v -> {
            suppressUntil = System.currentTimeMillis() + SUPPRESS_MS;
            finish();
        });

        tvBeginGently.setOnClickListener(v -> startWakeEpisode());

        tvSleepyAgain.setOnClickListener(v -> endWakeEpisode("fell_asleep"));

        tvLeaveBed.setOnClickListener(v -> endWakeEpisode("left_bed"));

        btnGear.setOnClickListener(v -> {
            updateSettingsLabels();
            settingsPanel.setVisibility(View.VISIBLE);
        });

        findViewById(R.id.tvSettingsDone).setOnClickListener(v -> {
            settingsPanel.setVisibility(View.GONE);
            loadTimes();
            transitionPlayed = false;
            if (!wakeEpisodeActive) updateDisplay();
        });

        tvSettingsSleep.setOnClickListener(v ->
            new TimePickerDialog(this, (vw, h, m) -> {
                sleepHour = h; sleepMinute = m;
                prefs.edit().putInt("sleepHour", h).putInt("sleepMinute", m).apply();
                updateSettingsLabels();
            }, sleepHour, sleepMinute, true).show());

        tvSettingsWake.setOnClickListener(v ->
            new TimePickerDialog(this, (vw, h, m) -> {
                wakeHour = h; wakeMinute = m;
                prefs.edit().putInt("wakeHour", h).putInt("wakeMinute", m).apply();
                updateSettingsLabels();
            }, wakeHour, wakeMinute, true).show());

        tvSettingsBreathing.setOnClickListener(v ->
            showNumberPicker("Breathing minutes", "breathingMins", 0, 30, tvSettingsBreathing));
        tvSettingsResting.setOnClickListener(v ->
            showNumberPicker("Quiet rest minutes", "restingMins",  0, 60, tvSettingsResting));

        // Restore wake episode if it was active when activity was recreated
        if (wakeEpisodeActive) {
            sleepPanel.setVisibility(View.GONE);
            wakePanel.setVisibility(View.VISIBLE);
            loadThresholds();
            wakeStartMs = prefs.getLong("wakeEpisodeStart", System.currentTimeMillis());
            startEpisodeTicker();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        isShowing = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        isShowing = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isSuppressed()) { finish(); return; }
        registerReceiver(timerReceiver,
            new IntentFilter(TimerService.ACTION_TICK),
            Context.RECEIVER_NOT_EXPORTED);
        if (!wakeEpisodeActive && !isTransitioning) updateDisplay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(wakeTransitionTrigger);
        if (transitionFallback != null) handler.removeCallbacks(transitionFallback);
        try { unregisterReceiver(timerReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (episodeTicker != null) handler.removeCallbacks(episodeTicker);
    }

    // ── Wake episode ───────────────────────────────────────────────────────

    private void startWakeEpisode() {
        wakeEpisodeActive = true;
        loadThresholds();
        currentPhase = -1;

        wakeStartMs = prefs.getLong("wakeEpisodeStart", 0);
        if (wakeStartMs == 0) {
            wakeStartMs = System.currentTimeMillis();
            prefs.edit().putLong("wakeEpisodeStart", wakeStartMs).apply();
            recordEpisodeStart(wakeStartMs);
        }

        sleepPanel.setVisibility(View.GONE);
        wakePanel.setVisibility(View.VISIBLE);
        startEpisodeTicker();
    }

    private void endWakeEpisode(String outcome) {
        if (episodeTicker != null) {
            handler.removeCallbacks(episodeTicker);
            episodeTicker = null;
        }
        finishEpisode(outcome);
        prefs.edit().remove("wakeEpisodeStart").apply();
        wakeEpisodeActive = false;
        currentPhase = -1;

        wakePanel.setVisibility(View.GONE);
        sleepPanel.setVisibility(View.VISIBLE);
        updateDisplay();
    }

    private void startEpisodeTicker() {
        if (episodeTicker != null) handler.removeCallbacks(episodeTicker);
        episodeTicker = new Runnable() {
            @Override public void run() {
                long elapsed = (System.currentTimeMillis() - wakeStartMs) / 1000;
                updateEpisodeDisplay(elapsed);
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(episodeTicker);
    }

    private void updateEpisodeDisplay(long elapsedSeconds) {
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
            // fall through
            case PHASE_RESTING:   dot2.setBackgroundResource(R.drawable.dot_active);
            // fall through
            case PHASE_BREATHING: dot1.setBackgroundResource(R.drawable.dot_active);
                break;
        }
    }

    private void loadThresholds() {
        int breathingMins = prefs.getInt("breathingMins", 5);
        int restingMins   = prefs.getInt("restingMins",  10);
        breathingEndSec   = breathingMins * 60L;
        restingEndSec     = breathingEndSec + (restingMins * 60L);
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

    // ── Lottie display ─────────────────────────────────────────────────────

    private void updateDisplay() {
        if (isTransitioning) return;
        if (isSleepWindow()) {
            transitionPlayed = false;
            showSleepMode();
        } else {
            if (!transitionPlayed) {
                if (firstDisplay) { transitionPlayed = true; showWakeMode(); }
                else              { playTransition(); }
            } else {
                showWakeMode();
            }
        }
        firstDisplay = false;
    }

    private void showSleepMode() {
        tvMorningGreeting.setVisibility(View.GONE);
        tvMorningMessage.setVisibility(View.GONE);
        tvUnlock.setVisibility(View.VISIBLE);
        tvBeginGently.setVisibility(View.VISIBLE);

        lottieAnimation.setMinAndMaxProgress(0f, 1f);
        lottieAnimation.setProgress(FRAME_MOON_HOLD / TOTAL_FRAMES);

        scheduleExactWakeTransition();
    }

    private void playTransition() {
        isTransitioning = true;
        final boolean[] done = {false};

        lottieAnimation.removeAllAnimatorListeners();

        float startP = FRAME_TRANS_START / TOTAL_FRAMES;
        float endP   = FRAME_TRANS_END   / TOTAL_FRAMES;
        lottieAnimation.setMinAndMaxProgress(startP, endP);
        lottieAnimation.setSpeed(0.3f);
        lottieAnimation.setRepeatCount(0);

        long safeMs = (long)(((FRAME_TRANS_END - FRAME_TRANS_START) / (60f * 0.3f)) * 1000L) + 3000L;
        if (transitionFallback != null) handler.removeCallbacks(transitionFallback);
        transitionFallback = () -> {
            if (!done[0]) { done[0] = true; onTransitionDone(); }
        };
        handler.postDelayed(transitionFallback, safeMs);

        lottieAnimation.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (!done[0]) {
                    done[0] = true;
                    if (transitionFallback != null) handler.removeCallbacks(transitionFallback);
                    onTransitionDone();
                }
            }
            @Override public void onAnimationCancel(Animator a) { /* fallback handles it */ }
        });

        lottieAnimation.post(() -> lottieAnimation.playAnimation());
    }

    private void onTransitionDone() {
        lottieAnimation.removeAllAnimatorListeners();
        isTransitioning  = false;
        transitionPlayed = true;
        showWakeMode();
    }

    private void showWakeMode() {
        tvUnlock.setVisibility(View.GONE);
        tvBeginGently.setVisibility(View.GONE);
        tvMorningGreeting.setVisibility(View.VISIBLE);
        tvMorningMessage.setVisibility(View.VISIBLE);

        lottieAnimation.setMinAndMaxProgress(0f, 1f);
        lottieAnimation.setProgress(FRAME_SUN_HOLD / TOTAL_FRAMES);
    }

    // ── Wake boundary timer ────────────────────────────────────────────────

    private void scheduleExactWakeTransition() {
        handler.removeCallbacks(wakeTransitionTrigger);
        Calendar wake = Calendar.getInstance();
        wake.set(Calendar.HOUR_OF_DAY, wakeHour);
        wake.set(Calendar.MINUTE,      wakeMinute);
        wake.set(Calendar.SECOND,      0);
        wake.set(Calendar.MILLISECOND, 0);
        long delay = wake.getTimeInMillis() - System.currentTimeMillis();
        if (delay <= 0) { wake.add(Calendar.DAY_OF_MONTH, 1); delay = wake.getTimeInMillis() - System.currentTimeMillis(); }
        if (delay > 0 && delay < 24L * 3600 * 1000) handler.postDelayed(wakeTransitionTrigger, delay);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void showNumberPicker(String title, String key, int min, int max, TextView lbl) {
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(min); p.setMaxValue(max);
        p.setValue(prefs.getInt(key, key.equals("breathingMins") ? 5 : 10));
        p.setWrapSelectorWheel(false);
        LinearLayout l = new LinearLayout(this);
        l.setGravity(android.view.Gravity.CENTER);
        l.setPadding(0, 32, 0, 32);
        l.addView(p);
        new AlertDialog.Builder(this).setTitle(title).setView(l)
            .setPositiveButton("Done", (d, w) -> { prefs.edit().putInt(key, p.getValue()).apply(); updateSettingsLabels(); })
            .setNegativeButton("Cancel", null).show();
    }

    private void loadTimes() {
        sleepHour   = prefs.getInt("sleepHour",   22);
        sleepMinute = prefs.getInt("sleepMinute",  30);
        wakeHour    = prefs.getInt("wakeHour",      7);
        wakeMinute  = prefs.getInt("wakeMinute",    0);
    }

    private void updateSettingsLabels() {
        tvSettingsSleep.setText(String.format(Locale.UK, "%02d:%02d", sleepHour, sleepMinute));
        tvSettingsWake.setText(String.format(Locale.UK, "%02d:%02d", wakeHour,  wakeMinute));
        tvSettingsBreathing.setText(prefs.getInt("breathingMins",  5) + " mins");
        tvSettingsResting.setText(  prefs.getInt("restingMins",   10) + " mins");
    }

    private boolean isSleepWindow() {
        int now   = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60
                  + Calendar.getInstance().get(Calendar.MINUTE);
        int sleep = sleepHour * 60 + sleepMinute;
        int wake  = wakeHour  * 60 + wakeMinute;
        return (sleep > wake) ? (now >= sleep || now < wake)
                              : (now >= sleep && now < wake);
    }
}
