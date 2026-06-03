package com.vibecoding.monitor;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MonitorWidgetProvider extends AppWidgetProvider {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.CHINA);

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        MetricSnapshot snapshot = new MetricSampler(context).sample();
        for (int appWidgetId : appWidgetIds) {
            manager.updateAppWidget(appWidgetId, buildViews(context, snapshot));
        }
    }

    static void updateAllWidgets(Context context, MetricSnapshot snapshot) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, MonitorWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(componentName);
        if (ids == null || ids.length == 0) {
            return;
        }
        for (int id : ids) {
            manager.updateAppWidget(id, buildViews(context, snapshot));
        }
    }

    private static RemoteViews buildViews(Context context, MetricSnapshot snapshot) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_monitor);
        views.setTextViewText(R.id.widget_time, TIME_FORMAT.format(new Date(snapshot.timestampMs)));
        views.setTextViewText(R.id.widget_temp, formatTemp(snapshot.displayTempC()));
        views.setTextViewText(R.id.widget_cpu, formatPercent(snapshot.cpuPercent));
        views.setTextViewText(R.id.widget_memory, formatPercent(snapshot.memoryPercent));
        views.setProgressBar(R.id.widget_cpu_bar, 100, Math.round(snapshot.cpuPercent), false);
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context));
        return views;
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, 0, intent, flags);
    }

    private static String formatPercent(float value) {
        return Math.round(clamp(value)) + "%";
    }

    private static String formatTemp(float value) {
        if (Float.isNaN(value)) {
            return "--°";
        }
        return Math.round(value) + "°";
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(100f, value));
    }
}
