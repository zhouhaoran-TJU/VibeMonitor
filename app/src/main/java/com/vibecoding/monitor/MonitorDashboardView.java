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
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

final class MonitorDashboardView extends View {
    private static final int BG = Color.rgb(246, 247, 251);
    private static final int SURFACE = Color.WHITE;
    private static final int TEXT = Color.rgb(23, 32, 51);
    private static final int MUTED = Color.rgb(102, 112, 133);
    private static final int LINE = Color.rgb(229, 231, 235);
    private static final int BLUE = Color.rgb(37, 99, 235);
    private static final int GREEN = Color.rgb(14, 159, 110);
    private static final int AMBER = Color.rgb(245, 158, 11);
    private static final int RED = Color.rgb(225, 29, 72);
    private static final int CYAN = Color.rgb(8, 145, 178);
    private static final int MAX_HISTORY = 48;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final ArrayDeque<Float> cpuHistory = new ArrayDeque<>();
    private final ArrayDeque<Float> tempHistory = new ArrayDeque<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
    private MetricSnapshot snapshot = MetricSnapshot.empty();

    MonitorDashboardView(Context context) {
        super(context);
        setBackgroundColor(BG);
        setPadding(dp(18), dp(14), dp(18), dp(16));
    }

    void setSnapshot(MetricSnapshot snapshot) {
        this.snapshot = snapshot;
        push(cpuHistory, snapshot.cpuPercent);
        float temp = snapshot.displayTempC();
        push(tempHistory, Float.isNaN(temp) ? 0f : temp);
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

        float heroHeight = dp(205);
        drawHero(canvas, left, y, right - left, heroHeight);
        y += heroHeight + dp(12);

        float gap = dp(10);
        float cardWidth = (right - left - gap) / 2f;
        drawMetricCard(canvas, left, y, cardWidth, dp(104), "CPU", formatPercent(snapshot.cpuPercent),
                snapshot.coreCount + " 核", BLUE, snapshot.cpuPercent);
        drawMetricCard(canvas, left + cardWidth + gap, y, cardWidth, dp(104), "内存",
                formatPercent(snapshot.memoryPercent), "已使用", GREEN, snapshot.memoryPercent);
        y += dp(116);
        drawMetricCard(canvas, left, y, cardWidth, dp(104), "电量",
                formatPercent(snapshot.batteryPercent), snapshot.batteryState, AMBER, snapshot.batteryPercent);
        drawMetricCard(canvas, left + cardWidth + gap, y, cardWidth, dp(104), "存储",
                formatPercent(snapshot.storagePercent), "内部空间", CYAN, snapshot.storagePercent);
        y += dp(120);

        drawTrend(canvas, left, y, right - left, Math.max(dp(160), getHeight() - y - getPaddingBottom()));
    }

    private void drawHeader(Canvas canvas, int left, int right, float y) {
        textPaint.setShader(null);
        textPaint.setColor(TEXT);
        textPaint.setTextSize(sp(27));
        textPaint.setFakeBoldText(true);
        canvas.drawText("手机性能监视器", left, y + dp(30), textPaint);

        textPaint.setFakeBoldText(false);
        textPaint.setColor(MUTED);
        textPaint.setTextSize(sp(13));
        canvas.drawText("实时温度、CPU、内存、电量与存储状态", left, y + dp(55), textPaint);

        drawPill(canvas, right - dp(220), y + dp(12), dp(82), dp(28), "实时", GREEN);
    }

    private void drawHero(Canvas canvas, float x, float y, float width, float height) {
        drawRoundRect(canvas, x, y, width, height, dp(8), SURFACE);
        float temp = snapshot.displayTempC();
        int tempColor = tempColor(temp);

        float centerX = x + width * 0.33f;
        float centerY = y + height * 0.53f;
        float radius = Math.min(width * 0.24f, height * 0.36f);
        drawGauge(canvas, centerX, centerY, radius, tempToPercent(temp), tempColor);

        textPaint.setShader(null);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(TEXT);
        textPaint.setTextSize(sp(32));
        canvas.drawText(Float.isNaN(temp) ? "--" : Math.round(temp) + "°", centerX, centerY + dp(9), textPaint);
        textPaint.setTextSize(sp(12));
        textPaint.setFakeBoldText(false);
        textPaint.setColor(MUTED);
        canvas.drawText(snapshot.tempSource + " 温度", centerX, centerY + dp(35), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);

        float infoX = x + width * 0.58f;
        textPaint.setFakeBoldText(true);
        textPaint.setColor(TEXT);
        textPaint.setTextSize(sp(18));
        canvas.drawText(temperatureState(temp), infoX, y + dp(53), textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(MUTED);
        textPaint.setTextSize(sp(12));
        canvas.drawText("上次刷新 " + timeFormat.format(new Date(snapshot.timestampMs)), infoX, y + dp(76), textPaint);

        drawCompactRow(canvas, infoX, y + dp(105), width * 0.34f, "电池温度", formatTemp(snapshot.batteryTempC), AMBER);
        drawCompactRow(canvas, infoX, y + dp(143), width * 0.34f, "CPU 占用", formatPercent(snapshot.cpuPercent), BLUE);
    }

    private void drawGauge(Canvas canvas, float cx, float cy, float radius, float percent, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(14));
        paint.setColor(LINE);
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
        drawRoundRect(canvas, x, y, width, height, dp(8), SURFACE);

        textPaint.setShader(null);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(MUTED);
        textPaint.setTextSize(sp(12));
        canvas.drawText(title, x + dp(14), y + dp(25), textPaint);

        textPaint.setFakeBoldText(true);
        textPaint.setColor(TEXT);
        textPaint.setTextSize(sp(25));
        canvas.drawText(value, x + dp(14), y + dp(58), textPaint);

        textPaint.setFakeBoldText(false);
        textPaint.setColor(MUTED);
        textPaint.setTextSize(sp(11));
        canvas.drawText(subtitle, x + dp(14), y + dp(79), textPaint);

        float barX = x + dp(14);
        float barY = y + height - dp(15);
        float barW = width - dp(28);
        paint.setColor(LINE);
        rect.set(barX, barY, barX + barW, barY + dp(5));
        canvas.drawRoundRect(rect, dp(3), dp(3), paint);
        paint.setColor(color);
        rect.set(barX, barY, barX + barW * clamp(percent) / 100f, barY + dp(5));
        canvas.drawRoundRect(rect, dp(3), dp(3), paint);
    }

    private void drawTrend(Canvas canvas, float x, float y, float width, float height) {
        drawRoundRect(canvas, x, y, width, height, dp(8), SURFACE);
        textPaint.setShader(null);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(TEXT);
        textPaint.setTextSize(sp(17));
        canvas.drawText("最近趋势", x + dp(14), y + dp(30), textPaint);

        drawLegend(canvas, x + width - dp(126), y + dp(20), BLUE, "CPU");
        drawLegend(canvas, x + width - dp(70), y + dp(20), RED, "温度");

        float chartX = x + dp(16);
        float chartY = y + dp(48);
        float chartW = width - dp(32);
        float chartH = height - dp(66);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(LINE);
        for (int i = 0; i <= 3; i++) {
            float lineY = chartY + chartH * i / 3f;
            canvas.drawLine(chartX, lineY, chartX + chartW, lineY, paint);
        }
        drawHistory(canvas, cpuHistory, chartX, chartY, chartW, chartH, BLUE, 100f);
        drawHistory(canvas, tempHistory, chartX, chartY, chartW, chartH, RED, 80f);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHistory(
            Canvas canvas,
            ArrayDeque<Float> values,
            float x,
            float y,
            float width,
            float height,
            int color,
            float maxValue) {
        if (values.size() < 2) {
            return;
        }
        path.reset();
        int index = 0;
        int count = values.size();
        for (Float value : values) {
            float px = x + width * index / (count - 1f);
            float py = y + height - height * clamp(value * 100f / maxValue) / 100f;
            if (index == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
            index++;
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

    private void drawCompactRow(Canvas canvas, float x, float y, float width, String label, String value, int color) {
        paint.setColor(Color.argb(18, Color.red(color), Color.green(color), Color.blue(color)));
        rect.set(x, y, x + width, y + dp(28));
        canvas.drawRoundRect(rect, dp(8), dp(8), paint);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(MUTED);
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
        textPaint.setColor(MUTED);
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

    private void push(ArrayDeque<Float> history, float value) {
        if (history.size() >= MAX_HISTORY) {
            history.removeFirst();
        }
        history.addLast(value);
    }

    private int tempColor(float temp) {
        if (Float.isNaN(temp)) {
            return MUTED;
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
}
