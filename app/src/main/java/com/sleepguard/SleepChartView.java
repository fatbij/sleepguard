package com.sleepguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.sleepguard.db.SleepSession;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SleepChartView extends View {

    public interface OnDayTappedListener {
        void onDayTapped(SleepSession session); // null if no session that day
    }

    // Y-axis: 8pm (20h) to 10am next day (34h) = 14-hour span
    private static final float CHART_START = 20f;
    private static final float CHART_SPAN  = 14f;
    private static final int   DAYS        = 7;

    private final List<String>              dates      = new ArrayList<>();
    private final Map<String, SleepSession> sessionMap = new HashMap<>();
    private OnDayTappedListener listener;

    private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sleepLine   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wakeLine    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sleepDot    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wakeDot     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisLabel   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float leftPad, rightPad, topPad, botPad;

    public SleepChartView(Context ctx)                     { super(ctx);        init(ctx); }
    public SleepChartView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(ctx); }

    private void init(Context ctx) {
        float dp = ctx.getResources().getDisplayMetrics().density;

        fillPaint.setColor(0x147EB8FF);
        fillPaint.setStyle(Paint.Style.FILL);

        sleepLine.setColor(0xAA7EB8FF);
        sleepLine.setStyle(Paint.Style.STROKE);
        sleepLine.setStrokeWidth(1.5f * dp);
        sleepLine.setStrokeCap(Paint.Cap.ROUND);

        wakeLine.setColor(0xAA4DCCAA);
        wakeLine.setStyle(Paint.Style.STROKE);
        wakeLine.setStrokeWidth(1.5f * dp);
        wakeLine.setStrokeCap(Paint.Cap.ROUND);

        sleepDot.setColor(0xFF7EB8FF);
        sleepDot.setStyle(Paint.Style.FILL);

        wakeDot.setColor(0xFF4DCCAA);
        wakeDot.setStyle(Paint.Style.FILL);

        labelPaint.setTextSize(9 * dp);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(0x55AACCEE);

        axisLabel.setTextSize(9 * dp);
        axisLabel.setTextAlign(Paint.Align.RIGHT);
        axisLabel.setColor(0x44AACCEE);

        gridPaint.setColor(0x10FFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(0.5f * dp);

        leftPad  = 38 * dp;
        rightPad = 10 * dp;
        topPad   = 10 * dp;
        botPad   = 20 * dp;
    }

    public void setSessions(List<SleepSession> sessions) {
        sessionMap.clear();
        for (SleepSession s : sessions) {
            if (s.date != null) sessionMap.put(s.date, s);
        }
        buildDates();
        invalidate();
    }

    public void setOnDayTappedListener(OnDayTappedListener l) { listener = l; }

    private void buildDates() {
        dates.clear();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.UK);
        for (int i = DAYS - 1; i >= 0; i--) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH, -i);
            dates.add(fmt.format(c.getTime()));
        }
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        float dp = getContext().getResources().getDisplayMetrics().density;
        setMeasuredDimension(w, (int)(196 * dp));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (dates.isEmpty()) buildDates();
        float dp   = getContext().getResources().getDisplayMetrics().density;
        float cw   = getWidth()  - leftPad - rightPad;
        float ch   = getHeight() - topPad  - botPad;
        float colW = cw / DAYS;

        // Horizontal grid lines + Y labels
        String[] yLabels = {"8pm","10pm","12am","2am","4am","6am","8am","10am"};
        float[]  yNorms  = {0f, 2f/14f, 4f/14f, 6f/14f, 8f/14f, 10f/14f, 12f/14f, 1f};
        for (int i = 0; i < yLabels.length; i++) {
            float y = topPad + yNorms[i] * ch;
            canvas.drawLine(leftPad, y, leftPad + cw, y, gridPaint);
            canvas.drawText(yLabels[i], leftPad - 4 * dp, y + 3 * dp, axisLabel);
        }

        // X labels (day initials)
        SimpleDateFormat parseFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.UK);
        SimpleDateFormat dayFmt   = new SimpleDateFormat("EEE", Locale.UK);
        for (int i = 0; i < DAYS; i++) {
            float cx = leftPad + i * colW + colW / 2f;
            try {
                java.util.Date d = parseFmt.parse(dates.get(i));
                String label = dayFmt.format(d).substring(0, 1);
                canvas.drawText(label, cx, topPad + ch + botPad - 5 * dp, labelPaint);
            } catch (Exception ignored) {}
        }

        // Collect chart points
        float[] sY = new float[DAYS];
        float[] wY = new float[DAYS];
        float[] cx = new float[DAYS];
        boolean[] hasSleep = new boolean[DAYS];
        boolean[] hasWake  = new boolean[DAYS];

        for (int i = 0; i < DAYS; i++) {
            cx[i] = leftPad + i * colW + colW / 2f;
            SleepSession s = sessionMap.get(dates.get(i));
            if (s == null) continue;
            float sn = timeToNorm(s.sleepTime);
            float wn = timeToNorm(s.wakeTime);
            if (!Float.isNaN(sn)) { sY[i] = topPad + sn * ch; hasSleep[i] = true; }
            if (!Float.isNaN(wn)) { wY[i] = topPad + wn * ch; hasWake[i]  = true; }
        }

        // Filled window between bedtime and wake
        Path fill = new Path();
        boolean started = false;
        for (int i = 0; i < DAYS; i++) {
            if (hasSleep[i] && hasWake[i]) {
                if (!started) { fill.moveTo(cx[i], sY[i]); started = true; }
                else          fill.lineTo(cx[i], sY[i]);
            }
        }
        if (started) {
            for (int i = DAYS - 1; i >= 0; i--) {
                if (hasSleep[i] && hasWake[i]) fill.lineTo(cx[i], wY[i]);
            }
            fill.close();
            canvas.drawPath(fill, fillPaint);
        }

        // Lines
        drawSegments(canvas, cx, sY, hasSleep, sleepLine);
        drawSegments(canvas, cx, wY, hasWake,  wakeLine);

        // Dots
        float r = 3.5f * dp;
        for (int i = 0; i < DAYS; i++) {
            if (hasSleep[i]) canvas.drawCircle(cx[i], sY[i], r, sleepDot);
            if (hasWake[i])  canvas.drawCircle(cx[i], wY[i], r, wakeDot);
        }
    }

    private void drawSegments(Canvas c, float[] xs, float[] ys, boolean[] valid, Paint p) {
        int prev = -1;
        for (int i = 0; i < DAYS; i++) {
            if (valid[i]) {
                if (prev >= 0) c.drawLine(xs[prev], ys[prev], xs[i], ys[i], p);
                prev = i;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_DOWN) return false;
        float cw   = getWidth() - leftPad - rightPad;
        float colW = cw / DAYS;
        float x    = e.getX();
        if (x < leftPad || x > leftPad + cw) return false;
        int idx = (int)((x - leftPad) / colW);
        if (idx < 0 || idx >= DAYS || idx >= dates.size()) return false;
        if (listener != null) listener.onDayTapped(sessionMap.get(dates.get(idx)));
        return true;
    }

    private float timeToNorm(String t) {
        if (t == null || t.isEmpty()) return Float.NaN;
        String[] parts = t.split(":");
        if (parts.length != 2) return Float.NaN;
        try {
            float h = Float.parseFloat(parts[0]) + Float.parseFloat(parts[1]) / 60f;
            float offset;
            if (h >= CHART_START) {
                offset = h - CHART_START;
            } else if (h <= 10f) {
                offset = h + 24f - CHART_START;
            } else {
                return Float.NaN;
            }
            return offset / CHART_SPAN;
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }
}
