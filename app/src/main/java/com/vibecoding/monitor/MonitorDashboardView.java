package com.vibecoding.monitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class MonitorDashboardView extends View {
    private static final long DAY_WINDOW_MS = 24L * 60L * 60L * 1000L;
    private static final long TREND_WINDOW_MS = 2L * 60L * 60L * 1000L;
    private static final long MINUTE_MS = 60L * 1000L;
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int GREEN = Color.rgb(14, 159, 110);
    private static final int AMBER = Color.rgb(245, 158, 11);
    private static final int RED = Color.rgb(225, 29, 72);
    private static final int CYAN = Color.rgb(8, 145, 178);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
    private final SimpleDateFormat axisFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private final ArrayList<MetricSnapshot> history = new ArrayList<>();
    private final ArrayList<MetricSnapshot> visibleHistory = new ArrayList<>();
    private MetricSnapshot snapshot = MetricSnapshot.empty();
    private Palette palette = Palette.defaultStyle();
    private long trendEndMs = 0L;
    private float downX;

    MonitorDashboardView(Context context) {
        super(context);
        setBackgroundColor(palette.bg);
        setPadding(dp(16), dp(14), dp(16), dp(16));
    }

    void setDisplayStyle(DisplayStyle style) {
        palette = style == DisplayStyle.NIGHT ? Palette.nightStyle() : Palette.defaultStyle();
        setBackgroundColor(palette.bg);
        invalidate();
    }

    void setSnapshot(MetricSnapshot snapshot) {
        this.snapshot = snapshot;
        invalidate();
    }

    void setHistory(List<MetricSnapshot> snapshots) {
        history.clear();
        if (snapshots != null) {
            history.addAll(snapshots);
        }
        if (trendEndMs == 0L && !history.isEmpty()) {
            trendEndMs = history.get(history.size() - 1).timestampMs;
        }
        clampTrendWindow();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                return true;
            case MotionEvent.ACTION_UP:
                handleSwipe(event.getX() - downX);
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int left = getPaddingLeft();
        int right = width - getPaddingRight();
        float y = getPaddingTop();

        drawHeader(canvas, left, right, y);
        y += dp(76);

        float heroHeight = dp(164);
        drawHero(canvas, left, y, right - left, heroHeight);
        y += heroHeight + dp(12);

        float gap = dp(8);
        float cardWidth = (right - left - gap) / 2f;
        drawMetricCard(canvas, left, y, cardWidth, dp(82), "CPU", formatPercent(snapshot.cpuPercent),
                snapshot.coreCount + " 核", BLUE, snapshot.cpuPercent);
        drawMetricCard(canvas, left + cardWidth + gap, y, cardWidth, dp(82), "内存",
                formatPercent(snapshot.memoryPercent), "已使用", GREEN, snapshot.memoryPercent);
        y += dp(92);
        drawMetricCard(canvas, left, y, cardWidth, dp(82), "电量",
                formatPercent(snapshot.batteryPercent), snapshot.batteryState, AMBER, snapshot.batteryPercent);
        drawMetricCard(canvas, left + cardWidth + gap, y, cardWidth, dp(82), "存储",
                formatPercent(snapshot.storagePercent), "内部空间", CYAN, snapshot.storagePercent);
        y += dp(94);

        drawTrend(canvas, left, y, right - left, Math.max(dp(190), getHeight() - y - getPaddingBottom()));
    }

    private void drawHeader(Canvas canvas, int left, int right, float y) {
        textPaint.setShader(null);
        textPaint.setColor(palette.text);
        textPaint.setTextSize(sp(22));
        textPaint.setFakeBoldText(true);
        canvas.drawText("手机性能监视器", left, y + dp(27), textPaint);

        textPaint.setFakeBoldText(false);
        textPaint.setColor(palette.muted);
        textPaint.setTextSize(sp(11));
        canvas.drawText("24小时记录 · " + history.size() + " 条样本", left, y + dp(47), textPaint);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(RED);
        canvas.drawText("最高 " + formatTemp(maxTemp24h()), left, y + dp(63), textPaint);
        textPaint.setFakeBoldText(false);
    }

    private void drawHero(Canvas canvas, float x, float y, float width, float height) {
        drawRoundRect(canvas, x, y, width, height, dp(8), palette.surface);
        float temp = snapshot.displayTempC();
        int tempColor = tempColor(temp);

        float centerX = x + width * 0.30f;
        float centerY = y + height * 0.53f;
        float radius = Math.min(width * 0.24f, height * 0.36f);
        drawGauge(canvas, centerX, centerY, radius, tempToPercent(temp), tempColor);

        textPaint.setShader(null);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(palette.text);
        textPaint.setTextSize(sp(32));
        canvas.drawText(Float.isNaN(temp) ? "--" : Math.round(temp) + "°", centerX, centerY + dp(9), textPaint);
        textPaint.setTextSize(sp(12));
        textPaint.setFakeBoldText(false);
        textPaint.setColor(palette.muted);
        canvas.drawText(snapshot.tempSource + " 温度", centerX, centerY + dp(35), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);

        float infoX = x + width * 0.55f;
        textPaint.setFakeBoldText(true);
        textPaint.setColor(palette.text);
        textPaint.setTextSize(sp(16));
        canvas.drawText(temperatureState(temp), infoX, y + dp(46), textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(palette.muted);
        textPaint.setTextSize(sp(12));
        canvas.drawText("刷新 " + timeFormat.format(new Date(snapshot.timestampMs)), infoX, y + dp(68), textPaint);

        drawCompactRow(canvas, infoX, y + dp(92), width * 0.38f, "电池温度", formatTemp(snapshot.batteryTempC), AMBER);
        drawCompactRow(canvas, infoX, y + dp(128), width * 0.38f, "CPU 占用", formatPercent(snapshot.cpuPercent), BLUE);
    }

    private void drawGauge(Canvas canvas, float cx, float cy, float radius, float percent, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(14));
        paint.setColor(palette.line);
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(rect, 140f, 260f, false, paint);
        paint.setColor(color);
        canvas.drawArc(rect, 140f, 260f * percent / 100f, false, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawMetricCard(
            Canvas canvas,
            float x,
            float y,
            float width,
            float height,
            String title,
            String value,
            String subtitle,
            int color,
            float percent) {
        drawRoundRect(canvas, x, y, width, height, dp(8), palette.surface);

        textPaint.setShader(null);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(palette.muted);
        textPaint.setTextSize(sp(12));
        canvas.drawText(title, x + dp(12), y + dp(22), textPaint);

        textPaint.setFakeBoldText(true);
        textPaint.setColor(palette.text);
        textPaint.setTextSize(sp(21));
        canvas.drawText(value, x + dp(12), y + dp(52), textPaint);

        textPaint.setFakeBoldText(false);
        textPaint.setColor(palette.muted);
        textPaint.setTextSize(sp(11));
        canvas.drawText(subtitle, x + dp(12), y + dp(70), textPaint);

        float barX = x + dp(12);
        float barY = y + height - dp(15);
        float barW = width - dp(24);
        paint.setColor(palette.line);
        rect.set(barX, barY, barX + barW, barY + dp(5));
        canvas.drawRoundRect(rect, dp(3), dp(3), paint);
        paint.setColor(color);
        rect.set(barX, barY, barX + barW * clamp(percent) / 100f, barY + dp(5));
        canvas.drawRoundRect(rect, dp(3), dp(3), paint);
    }

    private void drawTrend(Canvas canvas, float x, float y, float width, float height) {
        drawRoundRect(canvas, x, y, width, height, dp(8), palette.surface);
        textPaint.setShader(null);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(palette.text);
        textPaint.setTextSize(sp(17));
        canvas.drawText("温度趋势", x + dp(14), y + dp(30), textPaint);

        buildVisibleHistory();
        drawLegend(canvas, x + width - dp(82), y + dp(20), RED, "温度");

        float chartX = x + dp(34);
        float chartY = y + dp(50);
        float chartW = width - dp(50);
        float chartH = Math.max(dp(96), height - dp(84));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(palette.line);
        for (int i = 0; i <= 3; i++) {
            float lineY = chartY + chartH * i / 3f;
            canvas.drawLine(chartX, lineY, chartX + chartW, lineY, paint);
        }
        drawYAxisLabels(canvas, chartX, chartY, chartH);
        drawTimeAxis(canvas, chartX, chartY + chartH, chartW);
        drawHistory(canvas, chartX, chartY, chartW, chartH, RED, 80f);
        drawWindowHint(canvas, x, y, width, height);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHistory(
            Canvas canvas,
            float x,
            float y,
            float width,
            float height,
            int color,
            float maxValue) {
        if (visibleHistory.size() < 2) {
            return;
        }
        path.reset();
        long startMs = trendEndMs - TREND_WINDOW_MS;
        int count = visibleHistory.size();
        for (int index = 0; index < count; index++) {
            MetricSnapshot item = visibleHistory.get(index);
            float value = item.displayTempC();
            float px = x + width * (item.timestampMs - startMs) / (float) TREND_WINDOW_MS;
            float py = y + height - height * clamp(value * 100f / maxValue) / 100f;
            if (index == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        canvas.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawYAxisLabels(Canvas canvas, float x, float y, float height) {
        textPaint.setFakeBoldText(false);
        textPaint.setColor(palette.muted);
        textPaint.setTextSize(sp(10));
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("100", x - dp(6), y + dp(4), textPaint);
        canvas.drawText("50", x - dp(6), y + height / 2f + dp(4), textPaint);
        canvas.drawText("0", x - dp(6), y + height + dp(4), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawTimeAxis(Canvas canvas, float x, float y, float width) {
        if (trendEndMs == 0L) {
            return;
        }
        paint.setColor(palette.line);
        paint.setStrokeWidth(1f);
        canvas.drawLine(x, y, x + width, y, paint);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(palette.muted);
        textPaint.setTextSize(sp(10));
        long startMs = trendEndMs - TREND_WINDOW_MS;
        drawAxisTime(canvas, x, y + dp(18), startMs, Paint.Align.LEFT);
        drawAxisTime(canvas, x + width / 2f, y + dp(18), startMs + TREND_WINDOW_MS / 2L, Paint.Align.CENTER);
        drawAxisTime(canvas, x + width, y + dp(18), trendEndMs, Paint.Align.RIGHT);
    }

    private void drawWindowHint(Canvas canvas, float x, float y, float width, float height) {
        textPaint.setFakeBoldText(false);
        textPaint.setColor(palette.muted);
        textPaint.setTextSize(sp(10));
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("左右滑动查看24小时历史", x + width / 2f, y + height - dp(18), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void buildVisibleHistory() {
        visibleHistory.clear();
        if (history.isEmpty()) {
            return;
        }
        clampTrendWindow();
        long startMs = trendEndMs - TREND_WINDOW_MS;
        for (MetricSnapshot item : history) {
            if (item.timestampMs >= startMs && item.timestampMs <= trendEndMs) {
                visibleHistory.add(item);
            }
        }
    }

    private void handleSwipe(float deltaX) {
        if (Math.abs(deltaX) < dp(36) || history.isEmpty()) {
            return;
        }
        long shift = deltaX > 0f ? -TREND_WINDOW_MS : TREND_WINDOW_MS;
        trendEndMs += shift;
        clampTrendWindow();
        invalidate();
    }

    private void clampTrendWindow() {
        if (history.isEmpty()) {
            trendEndMs = 0L;
            return;
        }
        long latest = history.get(history.size() - 1).timestampMs;
        long earliest = Math.max(latest - DAY_WINDOW_MS, history.get(0).timestampMs);
        long minEnd = earliest + TREND_WINDOW_MS;
        long maxEnd = latest;
        if (trendEndMs == 0L || trendEndMs > maxEnd + MINUTE_MS) {
            trendEndMs = maxEnd;
        }
        if (trendEndMs < minEnd) {
            trendEndMs = minEnd;
        }
        if (trendEndMs > maxEnd) {
            trendEndMs = maxEnd;
        }
    }

    private float maxTemp24h() {
        if (history.isEmpty()) {
            return Float.NaN;
        }
        long latest = history.get(history.size() - 1).timestampMs;
        long cutoff = latest - DAY_WINDOW_MS;
        float max = Float.NaN;
        for (MetricSnapshot item : history) {
            if (item.timestampMs < cutoff) {
                continue;
            }
            float temp = item.displayTempC();
            if (Float.isNaN(temp)) {
                continue;
            }
            if (Float.isNaN(max) || temp > max) {
                max = temp;
            }
        }
        return max;
    }

    private void drawAxisTime(Canvas canvas, float x, float y, long timestampMs, Paint.Align align) {
        textPaint.setTextAlign(align);
        canvas.drawText(axisFormat.format(new Date(timestampMs)), x, y, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCompactRow(Canvas canvas, float x, float y, float width, String label, String value, int color) {
        paint.setColor(Color.argb(18, Color.red(color), Color.green(color), Color.blue(color)));
        rect.set(x, y, x + width, y + dp(28));
        canvas.drawRoundRect(rect, dp(8), dp(8), paint);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(palette.muted);
        textPaint.setTextSize(sp(11));
        canvas.drawText(label, x + dp(10), y + dp(18), textPaint);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(color);
        textPaint.setTextSize(sp(14));
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(value, x + width - dp(10), y + dp(19), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawLegend(Canvas canvas, float x, float y, int color, String label) {
        paint.setColor(color);
        canvas.drawCircle(x, y, dp(4), paint);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(palette.muted);
        textPaint.setTextSize(sp(11));
        canvas.drawText(label, x + dp(8), y + dp(4), textPaint);
    }

    private void drawPill(Canvas canvas, float x, float y, float width, float height, String label, int color) {
        paint.setShader(new LinearGradient(x, y, x + width, y + height, color, BLUE, Shader.TileMode.CLAMP));
        rect.set(x, y, x + width, y + height);
        canvas.drawRoundRect(rect, height / 2f, height / 2f, paint);
        paint.setShader(null);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(sp(12));
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, x + width / 2f, y + dp(18), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawRoundRect(Canvas canvas, float x, float y, float width, float height, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        rect.set(x, y, x + width, y + height);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    private int tempColor(float temp) {
        if (Float.isNaN(temp)) {
            return palette.muted;
        }
        if (temp >= 45f) {
            return RED;
        }
        if (temp >= 38f) {
            return AMBER;
        }
        return GREEN;
    }

    private String temperatureState(float temp) {
        if (Float.isNaN(temp)) {
            return "温度暂不可用";
        }
        if (temp >= 45f) {
            return "高温，需要关注";
        }
        if (temp >= 38f) {
            return "偏热，建议降载";
        }
        return "状态稳定";
    }

    private float tempToPercent(float temp) {
        if (Float.isNaN(temp)) {
            return 0f;
        }
        return clamp((temp - 20f) * 100f / 45f);
    }

    private String formatPercent(float value) {
        return Math.round(clamp(value)) + "%";
    }

    private String formatTemp(float value) {
        if (Float.isNaN(value)) {
            return "--°";
        }
        return Math.round(value) + "°";
    }

    private float clamp(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(100f, value));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static final class Palette {
        final int bg;
        final int surface;
        final int text;
        final int muted;
        final int line;

        Palette(int bg, int surface, int text, int muted, int line) {
            this.bg = bg;
            this.surface = surface;
            this.text = text;
            this.muted = muted;
            this.line = line;
        }

        static Palette defaultStyle() {
            return new Palette(
                    Color.rgb(246, 247, 251),
                    Color.WHITE,
                    Color.rgb(23, 32, 51),
                    Color.rgb(102, 112, 133),
                    Color.rgb(229, 231, 235));
        }

        static Palette nightStyle() {
            return new Palette(
                    Color.rgb(16, 23, 34),
                    Color.rgb(25, 35, 50),
                    Color.rgb(232, 238, 247),
                    Color.rgb(148, 163, 184),
                    Color.rgb(55, 65, 81));
        }
    }
}
