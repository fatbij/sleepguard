package com.sleepguard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.sleepguard.db.SleepSession;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SleepDayAdapter extends RecyclerView.Adapter<SleepDayAdapter.ViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(SleepSession session);
    }

    private final List<SleepSession> items = new ArrayList<>();
    private OnSessionClickListener listener;

    public void setOnSessionClickListener(OnSessionClickListener l) { listener = l; }

    public void setSessions(List<SleepSession> sessions) {
        items.clear();
        items.addAll(sessions);
        Collections.reverse(items); // newest first
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sleep_day, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        SleepSession s = items.get(position);
        h.bind(s);
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onSessionClick(s); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate, tvWindow, tvDuration, tvQuality;

        ViewHolder(View v) {
            super(v);
            tvDate     = v.findViewById(R.id.tvDate);
            tvWindow   = v.findViewById(R.id.tvWindow);
            tvDuration = v.findViewById(R.id.tvDuration);
            tvQuality  = v.findViewById(R.id.tvQuality);
        }

        void bind(SleepSession s) {
            // Date: "Mon\n3 May"
            try {
                Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.UK).parse(s.date);
                tvDate.setText(new SimpleDateFormat("EEE\nd MMM", Locale.UK).format(d));
            } catch (Exception e) {
                tvDate.setText(s.date != null ? s.date : "—");
            }

            // Sleep window
            String sleep = (s.sleepTime != null && !s.sleepTime.isEmpty()) ? s.sleepTime : "—";
            String wake  = (s.wakeTime  != null && !s.wakeTime.isEmpty())  ? s.wakeTime  : "ongoing";
            tvWindow.setText(sleep + "  –  " + wake);

            // Duration
            if (s.sessionEnd > s.sessionStart) {
                long ms = s.sessionEnd - s.sessionStart;
                long h  = ms / 3_600_000L;
                long m  = (ms % 3_600_000L) / 60_000L;
                tvDuration.setText(h > 0 ? h + "h " + m + "m" : m + "m");
            } else {
                tvDuration.setText(s.sessionEnd == 0 ? "in progress" : "—");
            }

            // Quality
            if (s.rating > 0) {
                tvQuality.setVisibility(View.VISIBLE);
                tvQuality.setText("★" + s.rating);
                tvQuality.setTextColor(qualityColor(s.rating));
            } else {
                tvQuality.setVisibility(View.INVISIBLE);
            }
        }

        private int qualityColor(int r) {
            switch (r) {
                case 1: return 0xFFCC6677;
                case 2: return 0xFFCC9944;
                case 3: return 0xFF5599CC;
                case 4: return 0xFF33BBAA;
                case 5: return 0xFF44DDAA;
                default: return 0x55AACCEE;
            }
        }
    }
}
