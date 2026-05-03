package com.sleepguard;

import android.app.TimePickerDialog;
import android.content.Intent;
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

public class EditSleepSessionActivity extends AppCompatActivity {

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler  handler  = new Handler(Looper.getMainLooper());

    private SleepSession session;
    private int originalEpisodeCount = 0;
    private int wakings = 0;
    private String bedtime, waketime;

    private int quality    = 0;
    private int restedness = 0;
    private int mood       = 0;
    private int onsetMins  = 0;

    private TextView tvTitle, tvBedtime, tvWakeTime, tvWakings, tvOnsetMins;
    private final TextView[] qualityStars = new TextView[5];
    private final TextView[] restedStars  = new TextView[5];
    private final TextView[] moodStars    = new TextView[5];
    private EditText etNotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_sleep_session);

        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        tvTitle    = findViewById(R.id.tvTitle);
        tvBedtime  = findViewById(R.id.tvBedtime);
        tvWakeTime = findViewById(R.id.tvWakeTime);
        tvWakings  = findViewById(R.id.tvWakings);
        tvOnsetMins = findViewById(R.id.tvOnsetMins);
        etNotes    = findViewById(R.id.etNotes);

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

        tvOnsetMins.setOnClickListener(v -> showOnsetPicker());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        findViewById(R.id.rowBedtime).setOnClickListener(v -> pickBedtime());
        findViewById(R.id.rowWakeTime).setOnClickListener(v -> pickWakeTime());
        findViewById(R.id.btnWakingsDown).setOnClickListener(v -> {
            if (wakings > 0) { wakings--; tvWakings.setText(String.valueOf(wakings)); }
        });
        findViewById(R.id.btnWakingsUp).setOnClickListener(v -> {
            wakings++;
            tvWakings.setText(String.valueOf(wakings));
        });
        findViewById(R.id.btnRedoCheckin).setOnClickListener(v -> redoCheckin());
        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());

        long sessionId = getIntent().getLongExtra("session_id", 0);
        loadSession(sessionId);
    }

    private void loadSession(long sessionId) {
        executor.execute(() -> {
            SleepDatabase db = SleepDatabase.getInstance(this);
            SleepSession s = db.sleepDao().getSessionById(sessionId);
            if (s == null) { handler.post(this::finish); return; }
            List<WakeEpisodeRecord> eps = db.sleepDao().getEpisodesForSession(s.id);
            handler.post(() -> bindSession(s, eps.size()));
        });
    }

    private void bindSession(SleepSession s, int episodeCount) {
        session              = s;
        originalEpisodeCount = episodeCount;
        wakings              = episodeCount;
        bedtime              = s.sleepTime != null ? s.sleepTime : "00:00";
        waketime             = s.wakeTime  != null ? s.wakeTime  : "00:00";
        quality              = s.rating;
        restedness           = s.restedRating;
        mood                 = s.moodRating;
        onsetMins            = s.onsetMins > 0 ? s.onsetMins : 15;

        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.UK).parse(s.date);
            if (d != null) tvTitle.setText(new SimpleDateFormat("EEEE, d MMM", Locale.UK).format(d));
        } catch (Exception ignored) { tvTitle.setText(s.date != null ? s.date : ""); }

        tvBedtime.setText(bedtime);
        tvWakeTime.setText(waketime);
        tvWakings.setText(String.valueOf(wakings));
        tvOnsetMins.setText(onsetMins + " min");

        refreshStars(qualityStars, quality);
        refreshStars(restedStars, restedness);
        refreshStars(moodStars, mood);

        if (s.notes != null && !s.notes.isEmpty()) etNotes.setText(s.notes);
    }

    private void pickBedtime() {
        int h = 0, m = 0;
        try { String[] p = bedtime.split(":"); h = Integer.parseInt(p[0]); m = Integer.parseInt(p[1]); }
        catch (Exception ignored) {}
        new TimePickerDialog(this, (tp, hour, min) -> {
            bedtime = String.format(Locale.UK, "%02d:%02d", hour, min);
            tvBedtime.setText(bedtime);
        }, h, m, true).show();
    }

    private void pickWakeTime() {
        int h = 0, m = 0;
        try { String[] p = waketime.split(":"); h = Integer.parseInt(p[0]); m = Integer.parseInt(p[1]); }
        catch (Exception ignored) {}
        new TimePickerDialog(this, (tp, hour, min) -> {
            waketime = String.format(Locale.UK, "%02d:%02d", hour, min);
            tvWakeTime.setText(waketime);
        }, h, m, true).show();
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

    private void save() {
        if (session == null) { finish(); return; }
        session.sleepTime    = bedtime;
        session.wakeTime     = waketime;
        session.rating       = quality;
        session.restedRating = restedness;
        session.moodRating   = mood;
        session.onsetMins    = onsetMins;
        session.notes        = etNotes.getText().toString().trim();
        int target = wakings;
        executor.execute(() -> {
            SleepDatabase db = SleepDatabase.getInstance(this);
            db.sleepDao().updateSession(session);

            int current = db.sleepDao().getEpisodeCount(session.id);
            if (target > current) {
                for (int i = current; i < target; i++) {
                    WakeEpisodeRecord ep = new WakeEpisodeRecord();
                    ep.sessionId = session.id;
                    ep.startMs   = session.sessionStart;
                    ep.endMs     = session.sessionStart + 60_000L;
                    ep.outcome   = "fell_asleep";
                    db.sleepDao().insertEpisode(ep);
                }
            } else if (target < current) {
                for (int i = target; i < current; i++) {
                    db.sleepDao().deleteLatestEpisode(session.id);
                }
            }
            handler.post(this::finish);
        });
    }

    private void redoCheckin() {
        if (session == null) return;
        Intent i = new Intent(this, MorningCheckInActivity.class);
        i.putExtra(CheckInAlarmReceiver.EXTRA_SESSION, session.id);
        startActivity(i);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
            .setTitle("Delete session?")
            .setMessage("This will permanently remove this night's data.")
            .setPositiveButton("Delete", (d, w) -> deleteSession())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void deleteSession() {
        if (session == null) { finish(); return; }
        executor.execute(() -> {
            SleepDatabase db = SleepDatabase.getInstance(this);
            db.sleepDao().deleteEpisodesForSession(session.id);
            db.sleepDao().deleteSession(session);
            handler.post(this::finish);
        });
    }

    private void refreshStars(TextView[] stars, int rating) {
        for (int i = 0; i < 5; i++) {
            stars[i].setTextColor(i < rating ? 0xFFDDEEFF : 0x33FFFFFF);
        }
    }
}
