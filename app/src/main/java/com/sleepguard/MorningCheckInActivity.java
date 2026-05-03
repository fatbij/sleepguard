package com.sleepguard;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.sleepguard.db.SleepDatabase;
import com.sleepguard.db.SleepSession;
import com.sleepguard.db.WakeEpisodeRecord;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MorningCheckInActivity extends AppCompatActivity {

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler  handler  = new Handler(Looper.getMainLooper());

    private SleepSession session;
    private int onsetMins  = 15;
    private int quality    = 0;
    private int restedness = 0;
    private int mood       = 0;

    private TextView tvSubtitle, tvBedtime, tvWakeTime, tvWakings, tvOnsetMins;
    private final TextView[] qualityStars = new TextView[5];
    private final TextView[] restedStars  = new TextView[5];
    private final TextView[] moodStars    = new TextView[5];
    private EditText etNotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_morning_check_in);

        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        tvSubtitle  = findViewById(R.id.tvSubtitle);
        tvBedtime   = findViewById(R.id.tvBedtime);
        tvWakeTime  = findViewById(R.id.tvWakeTime);
        tvWakings   = findViewById(R.id.tvWakings);
        tvOnsetMins = findViewById(R.id.tvOnsetMins);
        etNotes     = findViewById(R.id.etNotes);

        int[] qIds = {R.id.quality1, R.id.quality2, R.id.quality3, R.id.quality4, R.id.quality5};
        int[] rIds = {R.id.rested1,  R.id.rested2,  R.id.rested3,  R.id.rested4,  R.id.rested5};
        int[] mIds = {R.id.mood1,    R.id.mood2,    R.id.mood3,    R.id.mood4,    R.id.mood5};
        for (int i = 0; i < 5; i++) {
            final int val = i + 1;
            qualityStars[i] = findViewById(qIds[i]);
            qualityStars[i].setOnClickListener(v -> {
                quality = (quality == val) ? 0 : val;
                refreshStars(qualityStars, quality);
            });
            restedStars[i] = findViewById(rIds[i]);
            restedStars[i].setOnClickListener(v -> {
                restedness = (restedness == val) ? 0 : val;
                refreshStars(restedStars, restedness);
            });
            moodStars[i] = findViewById(mIds[i]);
            moodStars[i].setOnClickListener(v -> {
                mood = (mood == val) ? 0 : val;
                refreshStars(moodStars, mood);
            });
        }
        refreshStars(qualityStars, 0);
        refreshStars(restedStars, 0);
        refreshStars(moodStars, 0);

        tvOnsetMins.setOnClickListener(v -> showOnsetPicker());
        findViewById(R.id.btnSave).setOnClickListener(v -> submit());
        findViewById(R.id.btnSkip).setOnClickListener(v -> finish());

        long sessionId = getIntent().getLongExtra(CheckInAlarmReceiver.EXTRA_SESSION, 0);
        loadSession(sessionId);
    }

    private void loadSession(long sessionId) {
        executor.execute(() -> {
            SleepDatabase db = SleepDatabase.getInstance(this);
            SleepSession s = sessionId > 0
                ? db.sleepDao().getSessionById(sessionId)
                : db.sleepDao().getLatestSession();
            if (s == null) { handler.post(this::finish); return; }
            List<WakeEpisodeRecord> eps = db.sleepDao().getEpisodesForSession(s.id);
            handler.post(() -> bindSession(s, eps.size()));
        });
    }

    private void bindSession(SleepSession s, int wakingCount) {
        session    = s;
        onsetMins  = s.onsetMins > 0 ? s.onsetMins : 15;
        quality    = s.rating;
        restedness = s.restedRating;
        mood       = s.moodRating;

        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.UK).parse(s.date);
            if (d != null) tvSubtitle.setText(
                new SimpleDateFormat("EEEE, d MMMM", Locale.UK).format(d));
        } catch (Exception ignored) {}

        tvBedtime.setText(s.sleepTime != null && !s.sleepTime.isEmpty() ? s.sleepTime : "--:--");
        tvWakeTime.setText(s.wakeTime  != null && !s.wakeTime.isEmpty()  ? s.wakeTime  : "--:--");
        tvWakings.setText(String.valueOf(wakingCount));
        tvOnsetMins.setText(onsetMins + " min");

        refreshStars(qualityStars, quality);
        refreshStars(restedStars, restedness);
        refreshStars(moodStars, mood);

        if (s.notes != null && !s.notes.isEmpty()) etNotes.setText(s.notes);
    }

    private void showOnsetPicker() {
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(0); p.setMaxValue(120);
        p.setValue(onsetMins);
        p.setWrapSelectorWheel(false);
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, 32, 0, 32);
        l.addView(p);
        new AlertDialog.Builder(this)
            .setTitle("Time to fall asleep (minutes)")
            .setView(l)
            .setPositiveButton("Done", (d, w) -> {
                onsetMins = p.getValue();
                tvOnsetMins.setText(onsetMins + " min");
            })
            .setNegativeButton("Cancel", null).show();
    }

    private void submit() {
        if (session == null) { finish(); return; }
        session.rating       = quality;
        session.restedRating = restedness;
        session.moodRating   = mood;
        session.onsetMins    = onsetMins;
        session.notes        = etNotes.getText().toString().trim();
        executor.execute(() -> {
            SleepDatabase.getInstance(this).sleepDao().updateSession(session);
            handler.post(this::finish);
        });
    }

    private void refreshStars(TextView[] stars, int rating) {
        for (int i = 0; i < 5; i++) {
            stars[i].setTextColor(i < rating ? 0xFFDDEEFF : 0x33FFFFFF);
        }
    }
}
