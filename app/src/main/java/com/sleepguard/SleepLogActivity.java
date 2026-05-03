package com.sleepguard;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.sleepguard.db.SleepDatabase;
import com.sleepguard.db.SleepSession;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SleepLogActivity extends AppCompatActivity {

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler  handler  = new Handler(Looper.getMainLooper());

    private SleepGridView grid;
    private View     detailPanel;
    private TextView tvDetailDate, tvDetailWindow, tvDetailTime, tvDetailStats;
    private TextView tvDetailOnset, tvDetailNotes;
    private View     ratingRow, restedRow;
    private final TextView[] stars      = new TextView[5];
    private final TextView[] restedStars = new TextView[5];
    private TextView tvEmptyState;
    private TextView tvStat1, tvStat2, tvStat3, tvStat4, tvStat5, tvStat6;

    private SleepSession selectedSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sleep_log);

        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        grid           = findViewById(R.id.sleepGrid);
        detailPanel    = findViewById(R.id.detailPanel);
        tvDetailDate   = findViewById(R.id.tvDetailDate);
        tvDetailWindow = findViewById(R.id.tvDetailWindow);
        tvDetailTime   = findViewById(R.id.tvDetailTime);
        tvDetailStats  = findViewById(R.id.tvDetailStats);
        tvDetailOnset  = findViewById(R.id.tvDetailOnset);
        tvDetailNotes  = findViewById(R.id.tvDetailNotes);
        ratingRow      = findViewById(R.id.ratingRow);
        restedRow      = findViewById(R.id.restedRow);
        tvEmptyState   = findViewById(R.id.tvEmptyState);
        tvStat1        = findViewById(R.id.tvStat1);
        tvStat2        = findViewById(R.id.tvStat2);
        tvStat3        = findViewById(R.id.tvStat3);
        tvStat4        = findViewById(R.id.tvStat4);
        tvStat5        = findViewById(R.id.tvStat5);
        tvStat6        = findViewById(R.id.tvStat6);

        int[] starIds  = {R.id.star1,  R.id.star2,  R.id.star3,  R.id.star4,  R.id.star5};
        int[] rstarIds = {R.id.rstar1, R.id.rstar2, R.id.rstar3, R.id.rstar4, R.id.rstar5};
        for (int i = 0; i < 5; i++) {
            stars[i]       = findViewById(starIds[i]);
            restedStars[i] = findViewById(rstarIds[i]);
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnEdit).setOnClickListener(v -> {
            if (selectedSession != null) {
                Intent i = new Intent(this, EditSleepSessionActivity.class);
                i.putExtra("session_id", selectedSession.id);
                startActivity(i);
            }
        });

        grid.setOnSessionSelectedListener(s -> showSession(s));
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        executor.execute(() -> {
            SleepDatabase db = SleepDatabase.getInstance(this);
            List<SleepSession> sessions = db.sleepDao().getAllSessions();
            int totalEpisodes = db.sleepDao().getTotalEpisodeCount();
            handler.post(() -> bindData(sessions, totalEpisodes));
        });
    }

    private void bindData(List<SleepSession> sessions, int totalEpisodes) {
        grid.setSessions(sessions);
        tvEmptyState.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);

        int nights = sessions.size();
        tvStat3.setText(String.valueOf(nights));

        long sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        long totalInBedMs = 0; int n7 = 0;
        double qualSum = 0; int qualCount = 0;
        double onsetSum = 0; int onsetCount = 0;
        double restedSum = 0; int restedCount = 0;

        for (SleepSession s : sessions) {
            if (s.sessionEnd > s.sessionStart) {
                if (s.sessionStart >= sevenDaysAgo) {
                    totalInBedMs += s.sessionEnd - s.sessionStart;
                    n7++;
                }
            }
            if (s.rating > 0)       { qualSum   += s.rating;       qualCount++;   }
            if (s.onsetMins > 0)    { onsetSum  += s.onsetMins;    onsetCount++;  }
            if (s.restedRating > 0) { restedSum += s.restedRating; restedCount++; }
        }

        tvStat1.setText(n7 > 0
            ? String.format(Locale.UK, "%.1fh", (totalInBedMs / (double) n7) / 3_600_000.0)
            : "—");
        tvStat2.setText(qualCount > 0
            ? String.format(Locale.UK, "%.1f★", qualSum / qualCount)
            : "—");
        tvStat4.setText(onsetCount > 0
            ? Math.round(onsetSum / onsetCount) + "m"
            : "—");
        tvStat5.setText(nights > 0
            ? String.format(Locale.UK, "%.1f", (double) totalEpisodes / nights)
            : "—");
        tvStat6.setText(restedCount > 0
            ? String.format(Locale.UK, "%.1f★", restedSum / restedCount)
            : "—");

        if (selectedSession != null) {
            boolean found = false;
            for (SleepSession s : sessions) {
                if (s.id == selectedSession.id) { showSession(s); found = true; break; }
            }
            if (!found) { selectedSession = null; detailPanel.setVisibility(View.GONE); }
        }
    }

    private void showSession(SleepSession s) {
        selectedSession = s;
        if (s == null) { detailPanel.setVisibility(View.GONE); return; }
        detailPanel.setVisibility(View.VISIBLE);

        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.UK).parse(s.date);
            if (d != null) tvDetailDate.setText(
                new SimpleDateFormat("EEE, d MMM yyyy", Locale.UK).format(d));
        } catch (Exception ignored) { tvDetailDate.setText(s.date); }

        String wake = (s.wakeTime != null && !s.wakeTime.isEmpty()) ? s.wakeTime : "ongoing";
        tvDetailWindow.setText(s.sleepTime + " – " + wake);

        if (s.sessionEnd > s.sessionStart) {
            long ms = s.sessionEnd - s.sessionStart;
            long h = ms / 3_600_000L, m = (ms % 3_600_000L) / 60_000L;
            tvDetailTime.setText(h > 0 ? h + "h " + m + "m" : m + "m");
        } else {
            tvDetailTime.setText("ongoing");
        }

        executor.execute(() -> {
            int count = SleepDatabase.getInstance(this).sleepDao().getEpisodeCount(s.id);
            handler.post(() -> tvDetailStats.setText(
                count == 0 ? "No wake episodes"
                    : count + (count == 1 ? " wake episode" : " wake episodes")));
        });

        if (s.rating > 0) {
            ratingRow.setVisibility(View.VISIBLE);
            for (int i = 0; i < 5; i++) stars[i].setTextColor(i < s.rating ? 0xFFDDEEFF : 0x33FFFFFF);
        } else {
            ratingRow.setVisibility(View.GONE);
        }

        if (s.restedRating > 0) {
            restedRow.setVisibility(View.VISIBLE);
            for (int i = 0; i < 5; i++) restedStars[i].setTextColor(i < s.restedRating ? 0xFFDDEEFF : 0x33FFFFFF);
        } else {
            restedRow.setVisibility(View.GONE);
        }

        if (s.onsetMins > 0) {
            tvDetailOnset.setVisibility(View.VISIBLE);
            tvDetailOnset.setText("Fell asleep in " + s.onsetMins + " min");
        } else {
            tvDetailOnset.setVisibility(View.GONE);
        }

        if (s.notes != null && !s.notes.isEmpty()) {
            tvDetailNotes.setVisibility(View.VISIBLE);
            tvDetailNotes.setText(s.notes);
        } else {
            tvDetailNotes.setVisibility(View.GONE);
        }
    }
}
