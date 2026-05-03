package com.sleepguard;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.sleepguard.db.SleepDatabase;
import com.sleepguard.db.SleepSession;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SleepLogActivity extends AppCompatActivity {

    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler  handler  = new Handler(Looper.getMainLooper());

    private SleepChartView  chart;
    private SleepDayAdapter adapter;
    private TextView        tvEmptyState;
    private TextView        tvStat1, tvStat2, tvStat3, tvStat4, tvStat5, tvStat6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sleep_log);

        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        chart        = findViewById(R.id.sleepChart);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        tvStat1      = findViewById(R.id.tvStat1);
        tvStat2      = findViewById(R.id.tvStat2);
        tvStat3      = findViewById(R.id.tvStat3);
        tvStat4      = findViewById(R.id.tvStat4);
        tvStat5      = findViewById(R.id.tvStat5);
        tvStat6      = findViewById(R.id.tvStat6);

        RecyclerView recycler = findViewById(R.id.recyclerDays);
        adapter = new SleepDayAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        adapter.setOnSessionClickListener(s -> openSession(s.id));
        chart.setOnDayTappedListener(s -> { if (s != null) openSession(s.id); });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void openSession(long sessionId) {
        Intent i = new Intent(this, EditSleepSessionActivity.class);
        i.putExtra("session_id", sessionId);
        startActivity(i);
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
        chart.setSessions(sessions);
        adapter.setSessions(sessions);
        tvEmptyState.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);

        int nights = sessions.size();
        tvStat3.setText(String.valueOf(nights));

        long sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        long totalInBedMs = 0; int n7 = 0;
        double qualSum = 0;    int qualCount = 0;
        double onsetSum = 0;   int onsetCount = 0;
        double restedSum = 0;  int restedCount = 0;

        for (SleepSession s : sessions) {
            if (s.sessionEnd > s.sessionStart) {
                if (s.sessionStart >= sevenDaysAgo) { totalInBedMs += s.sessionEnd - s.sessionStart; n7++; }
            }
            if (s.rating       > 0) { qualSum   += s.rating;       qualCount++;   }
            if (s.onsetMins    > 0) { onsetSum  += s.onsetMins;    onsetCount++;  }
            if (s.restedRating > 0) { restedSum += s.restedRating; restedCount++; }
        }

        tvStat1.setText(n7 > 0
            ? String.format(Locale.UK, "%.1fh", (totalInBedMs / (double) n7) / 3_600_000.0) : "—");
        tvStat2.setText(qualCount > 0
            ? String.format(Locale.UK, "%.1f★", qualSum / qualCount) : "—");
        tvStat4.setText(onsetCount > 0
            ? Math.round(onsetSum / onsetCount) + "m" : "—");
        tvStat5.setText(nights > 0
            ? String.format(Locale.UK, "%.1f", (double) totalEpisodes / nights) : "—");
        tvStat6.setText(restedCount > 0
            ? String.format(Locale.UK, "%.1f★", restedSum / restedCount) : "—");
    }
}
