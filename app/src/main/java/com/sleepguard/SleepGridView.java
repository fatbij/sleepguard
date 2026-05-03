package com.sleepguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.sleepguard.db.SleepSession;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SleepGridView extends View {

    public interface OnSessionSelectedListener {
        void onSessionSelected(SleepSession session); // null = deselected/empty cell
    }

    private static final int WEEKS = 8;
    private static final int COLS  = 7;
    private static final String[] DAY_LABELS = {"M", "T", "W", "T", "F", "S", "S"};

    private final Map<String, SleepSession> sessionMap = new HashMap<>();
    private OnSessionSelectedListener listener;
    private String selectedDate = null;

    private final Paint emptyPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hlPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float cellSize, gap, headerH, startX, startY;

    public SleepGridView(Context ctx)                     { super(ctx);        init(ctx); }
    public SleepGridView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(ctx); }

    private void init(Context ctx) {
        float dp = ctx.getResources().getDisplayMetrics().density;

        emptyPaint.setColor(0x14AACCEE);
        emptyPaint.setStyle(Paint.Style.FILL);

        hlPaint.setColor(0x44FFFFFF);
        hlPaint.setStyle(Paint.Style.FILL);

        cellPaint.setStyle(Paint.Style.FILL);

        labelPaint.setTextSize(10 * dp);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(0x55AACCEE);
    }

    public void setSessions(List<SleepSession> sessions) {
        sessionMap.clear();
        for (SleepSession s : sessions) {
            if (s.date != null) sessionMap.put(s.date, s);
        }
        invalidate();
    }

    public void setOnSessionSelectedListener(OnSessionSelectedListener l) { listener = l; }

    public void clearSelection() { selectedDate = null; invalidate(); }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        float dp = getContext().getResources().getDisplayMetrics().density;
        float g  = 4 * dp;
        float usable = w - getPaddingStart() - getPaddingEnd();
        float cs = (usable - g * (COLS - 1)) / COLS;
        float h = getPaddingTop() + (float) Math.ceil(18 * dp) + g + WEEKS * (cs + g) + getPaddingBottom();
        setMeasuredDimension(w, (int) h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float dp = getContext().getResources().getDisplayMetrics().density;
        gap     = 4 * dp;
        headerH = 18 * dp;
        float usable = w - getPaddingStart() - getPaddingEnd();
        cellSize = (usable - gap * (COLS - 1)) / COLS;
        startX   = getPaddingStart();
        startY   = getPaddingTop() + headerH + gap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float dp = getContext().getResources().getDisplayMetrics().density;
        float r  = 4 * dp;

        // Day-of-week header
        for (int c = 0; c < COLS; c++) {
            float cx = startX + c * (cellSize + gap) + cellSize / 2f;
            canvas.drawText(DAY_LABELS[c], cx, getPaddingTop() + headerH - 4 * dp, labelPaint);
        }

        // Grid cells — oldest week top-left, current week bottom
        Calendar grid = gridStart();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.UK);

        for (int row = 0; row < WEEKS; row++) {
            for (int col = 0; col < COLS; col++) {
                String dateStr = fmt.format(grid.getTime());
                float left = startX + col * (cellSize + gap);
                float top  = startY + row * (cellSize + gap);
                RectF rect = new RectF(left, top, left + cellSize, top + cellSize);

                SleepSession s = sessionMap.get(dateStr);
                if (s != null) {
                    cellPaint.setColor(cellColor(s.rating));
                    canvas.drawRoundRect(rect, r, r, cellPaint);
                } else {
                    canvas.drawRoundRect(rect, r, r, emptyPaint);
                }

                if (dateStr.equals(selectedDate)) {
                    canvas.drawRoundRect(rect, r, r, hlPaint);
                }

                grid.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_DOWN) return super.onTouchEvent(e);

        float x = e.getX(), y = e.getY();
        if (y < startY) return false;

        int col = (int) ((x - startX) / (cellSize + gap));
        int row = (int) ((y - startY) / (cellSize + gap));
        if (col < 0 || col >= COLS || row < 0 || row >= WEEKS) return false;

        float cellLeft = startX + col * (cellSize + gap);
        float cellTop  = startY + row * (cellSize + gap);
        if (x > cellLeft + cellSize || y > cellTop + cellSize) return false;

        Calendar grid = gridStart();
        grid.add(Calendar.DAY_OF_MONTH, row * COLS + col);

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.UK);
        String dateStr = fmt.format(grid.getTime());
        SleepSession s = sessionMap.get(dateStr);

        if (dateStr.equals(selectedDate)) {
            selectedDate = null;
            if (listener != null) listener.onSessionSelected(null);
        } else {
            selectedDate = dateStr;
            if (listener != null) listener.onSessionSelected(s);
        }
        invalidate();
        return true;
    }

    private Calendar gridStart() {
        Calendar today = Calendar.getInstance();
        int dow = today.get(Calendar.DAY_OF_WEEK);
        // col 0 = Monday, col 6 = Sunday
        int colToday = (dow == Calendar.SUNDAY) ? 6 : dow - 2;
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -(WEEKS * COLS - 1 - (COLS - 1 - colToday)));
        return c;
    }

    private int cellColor(int rating) {
        switch (rating) {
            case 1:  return 0xCC8B3A45;
            case 2:  return 0xCC8B6830;
            case 3:  return 0xCC2E6E96;
            case 4:  return 0xCC1F9080;
            case 5:  return 0xCC2AAA88;
            default: return 0xCC2A5573;
        }
    }
}
