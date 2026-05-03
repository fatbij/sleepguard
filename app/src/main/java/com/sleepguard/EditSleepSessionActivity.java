package com.sleepguard;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
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

    private TextView tvTitle, tvBedtime, tvWakeTime, tvWakings;

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
        session               = s;
        originalEpisodeCount  = episodeCount;
        wakings               = episodeCount;
        bedtime               = s.sleepTime != null ? s.sleepTime : "00:00";
        waketime              = s.wakeTime  != null ? s.wakeTime  : "00:00";

        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.UK).parse(s.date);
            if (d != null) tvTitle.setText(new SimpleDateFormat("EEEE, d MMM", Locale.UK).format(d));
        } catch (Exception ignored) { tvTitle.setText(s.date != null ? s.date : ""); }

        tvBedtime.setText(bedtime);
        tvWakeTime.setText(waketime);
        tvWakings.setText(String.valueOf(wakings));
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

    private void save() {
        if (session == null) { finish(); return; }
        session.sleepTime = bedtime;
        session.wakeTime  = waketime;
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
}
