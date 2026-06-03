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
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class MonitorDashboardView extends View {
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
    private MetricSnapshot snapshot = MetricSnapshot.empty();
    private Palette palette = Palette.defaultStyle();

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
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int left = getPaddingLeft();
        int right = width - getPaddingRight();
        float y = getPaddingTop();

        drawHeader(canvas, left, right, y);
        y += dp(72);

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
        textPaint.setTextSize(sp(23));
        textPaint.setFakeBoldText(true);
        canvas.drawText("手机性能监视器", left, y + dp(27), textPaint);

        textPaint.setFakeBoldText(false);
        textPaint.setColor(palette.muted);
        textPaint.setTextSize(sp(12));
        canvas.drawText("后台记录开启 · 最近 " + history.size() + " 条样本", left, y + dp(49), textPaint);

        drawPill(canvas, left, y + dp(56), dp(88), dp(22), "持续监控", GREEN);
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
        textPaint.setTextSize(sp(18));
        canvas.drawText(temperatureState(temp), infoX, y + dp(46), textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(palette.muted);
        textPaint.setTextSize(sp(12));
        canvas.drawText("刷新 " + timeFormat.format(new Date(snapshot.timestampMs)), infoX, y + dp(68), textPaint);

        drawCompactRow(canvas, infoX, y + dp(93), width * 0.36f, "电池温度", formatTemp(snapshot.batteryTempC), AMBER);
        drawCompactRow(canvas, infoX, y + dp(130), width * 0.36f, "CPU 占用", formatPercent(snapshot.cpuPercent), BLUE);
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
        textPaint.setTextSize(sp(23));
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
        canvas.drawText("最近趋势", x + dp(14), y + dp(30), textPaint);

        drawLegend(canvas, x + width - dp(138), y + dp(20), BLUE, "CPU");
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
        drawHistory(canvas, true, chartX, chartY, chartW, chartH, BLUE, 100f);
        drawHistory(canvas, false, chartX, chartY, chartW, chartH, RED, 80f);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHistory(
            Canvas canvas,
            boolean cpu,
            float x,
            float y,
            float width,
            float height,
            int color,
            float maxValue) {
        if (history.size() < 2) {
            return;
        }
        path.reset();
        int count = history.size();
        for (int index = 0; index < count; index++) {
            MetricSnapshot item = history.get(index);
            float value = cpu ? item.cpuPercent : item.displayTempC();
            float px = x + width * index / (count - 1f);
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
        if (history.isEmpty()) {
            return;
        }
        paint.setColor(palette.line);
        paint.setStrokeWidth(1f);
        canvas.drawLine(x, y, x + width, y, paint);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(palette.muted);
        textPaint.setTextSize(sp(10));
        int last = history.size() - 1;
        drawAxisTime(canvas, x, y + dp(18), history.get(0).timestampMs, Paint.Align.LEFT);
        drawAxisTime(canvas, x + width / 2f, y + dp(18), history.get(last / 2).timestampMs, Paint.Align.CENTER);
        drawAxisTime(canvas, x + width, y + dp(18), history.get(last).timestampMs, Paint.Align.RIGHT);
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
