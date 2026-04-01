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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.airbnb.lottie.LottieAnimationView;
import java.util.Calendar;
import java.util.Locale;

public class LockScreenActivity extends AppCompatActivity {

    // Day-and-Night-Cycle.json  330 frames @ 60 fps
    // Frame 165  = mid-night hold
    // Frame 165→329 = full sunrise arc to end of composition
    private static final float FRAME_MOON_HOLD   = 165f;
    private static final float FRAME_TRANS_START = 165f;
    private static final float FRAME_TRANS_END   = 329f;
    private static final float FRAME_SUN_HOLD    = 329f;
    private static final float TOTAL_FRAMES      = 330f;

    private static final long SUPPRESS_MS = 5_000;
    public  static long    suppressUntil    = 0;
    public  static boolean wakeEpisodeActive = false;

    // Views
    private LottieAnimationView lottieAnimation;
    private TextView  tvUnlock, tvBeginGently;
    private TextView  tvMorningGreeting, tvMorningMessage;
    private TextView  tvClose;
    private View      settingsPanel;
    private ImageButton btnGear;
    private TextView  tvSettingsSleep, tvSettingsWake;
    private TextView  tvSettingsBreathing, tvSettingsResting;

    // State
    private final Handler handler         = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private boolean isTransitioning  = false;
    private boolean transitionPlayed = false;
    private Runnable transitionFallback = null;

    private int sleepHour, sleepMinute, wakeHour, wakeMinute;

    private final Runnable wakeTransitionTrigger = () -> {
        if (!isTransitioning && !transitionPlayed && !isSleepWindow()) {
            playTransition();
        }
    };

    private final BroadcastReceiver timerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            if (!isTransitioning) updateDisplay();
        }
    };

    public static boolean isSuppressed() {
        return System.currentTimeMillis() < suppressUntil;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isSuppressed() || wakeEpisodeActive) { finish(); return; }

        // Full immersive
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

        updateSettingsLabels();

        // Composition must be loaded before any frame calls
        lottieAnimation.addLottieOnCompositionLoadedListener(c -> {
            if (!isTransitioning) updateDisplay();
        });

        tvClose.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Dismiss SleepGuard")
                .setMessage("Are you sure?")
                .setPositiveButton("Yes", (d, w) -> {
                    suppressUntil = System.currentTimeMillis() + SUPPRESS_MS;
                    finish();
                })
                .setNegativeButton("Cancel", null).show());

        tvUnlock.setOnClickListener(v -> {
            suppressUntil = System.currentTimeMillis() + SUPPRESS_MS;
            finish();
        });

        tvBeginGently.setOnClickListener(v -> {
            suppressUntil    = System.currentTimeMillis() + SUPPRESS_MS;
            wakeEpisodeActive = true;
            startActivity(new Intent(this, WakeEpisodeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });

        btnGear.setOnClickListener(v -> {
            updateSettingsLabels();
            settingsPanel.setVisibility(View.VISIBLE);
        });

        findViewById(R.id.tvSettingsDone).setOnClickListener(v -> {
            settingsPanel.setVisibility(View.GONE);
            loadTimes();
            transitionPlayed = false;
            updateDisplay();
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isSuppressed() || wakeEpisodeActive) { finish(); return; }
        registerReceiver(timerReceiver,
            new IntentFilter(TimerService.ACTION_TICK),
            Context.RECEIVER_NOT_EXPORTED);
        if (!isTransitioning) updateDisplay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(wakeTransitionTrigger);
        if (transitionFallback != null) handler.removeCallbacks(transitionFallback);
        // Do NOT call lottieAnimation.cancelAnimation() here — it breaks
        // playAnimation() if the activity quickly resumes
        try { unregisterReceiver(timerReceiver); } catch (Exception ignored) {}
    }

    // ── Display ────────────────────────────────────────────────────────────

    private void updateDisplay() {
        if (isTransitioning) return;
        if (isSleepWindow()) {
            transitionPlayed = false;
            showSleepMode();
        } else {
            if (!transitionPlayed) playTransition();
            else                   showWakeMode();
        }
    }

    private void showSleepMode() {
        tvMorningGreeting.setVisibility(View.GONE);
        tvMorningMessage.setVisibility(View.GONE);
        tvUnlock.setVisibility(View.VISIBLE);
        tvBeginGently.setVisibility(View.VISIBLE);

        // Freeze at mid-night WITHOUT calling cancelAnimation().
        // cancelAnimation() sets an internal flag that makes the subsequent
        // playAnimation() call in playTransition() a silent no-op in many
        // Lottie versions.  Simply setting progress is enough to freeze —
        // the animator isn't running so there is nothing to cancel.
        lottieAnimation.setMinAndMaxProgress(0f, 1f);
        lottieAnimation.setProgress(FRAME_MOON_HOLD / TOTAL_FRAMES);

        scheduleExactWakeTransition();
    }

    private void playTransition() {
        isTransitioning = true;
        final boolean[] done = {false};

        // Remove any stale listeners — do NOT call cancelAnimation().
        lottieAnimation.removeAllAnimatorListeners();

        float startP = FRAME_TRANS_START / TOTAL_FRAMES;  // 0.500
        float endP   = FRAME_TRANS_END   / TOTAL_FRAMES;  // 0.997
        lottieAnimation.setMinAndMaxProgress(startP, endP);
        lottieAnimation.setSpeed(0.3f);       // ~165 frames / (60 * 0.3) ≈ 9 s
        lottieAnimation.setRepeatCount(0);

        // Fallback: if onAnimationEnd never fires, complete after expected
        // duration + 3 s buffer so the screen never gets stuck
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

        // post() ensures the min/max progress change is committed to the
        // drawable before playAnimation() runs — eliminates frame-0 flash
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

        // Freeze at full-day frame — again, no cancelAnimation()
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
