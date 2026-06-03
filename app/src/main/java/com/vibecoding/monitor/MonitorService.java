package com.vibecoding.monitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public final class MonitorService extends Service {
    private static final long SAMPLE_INTERVAL_MS = 60000L;
    private static final int NOTIFICATION_ID = 2101;
    private static final int HIGH_TEMP_NOTIFICATION_ID = 2102;
    private static final String CHANNEL_ID = "monitor_recording";
    private static final String ALERT_CHANNEL_ID = "temperature_alerts";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MetricSampler sampler;
    private MetricHistoryStore historyStore;
    private boolean highTempAlertActive;

    private final Runnable sampleRunnable = new Runnable() {
        @Override
        public void run() {
            MetricSnapshot snapshot = sampler.sample();
            historyStore.append(snapshot);
            MonitorWidgetProvider.updateAllWidgets(MonitorService.this, snapshot);
            maybeAlertHighTemp(snapshot);
            handler.postDelayed(this, SAMPLE_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sampler = new MetricSampler(this);
        historyStore = new MetricHistoryStore(this);
        createNotificationChannel();
        createAlertChannel();
        startForeground(NOTIFICATION_ID, buildNotification("正在记录性能数据"));
        handler.removeCallbacks(sampleRunnable);
        sampleRunnable.run();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(sampleRunnable);
        sampleRunnable.run();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(sampleRunnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("性能监视器")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private void maybeAlertHighTemp(MetricSnapshot snapshot) {
        float temp = snapshot.displayTempC();
        if (Float.isNaN(temp)) {
            return;
        }
        float threshold = TemperatureWarningSettings.threshold(this);
        if (temp < threshold - TemperatureWarningSettings.HIGH_TEMP_RESET_GAP_C) {
            highTempAlertActive = false;
            return;
        }
        if (temp < threshold || highTempAlertActive) {
            return;
        }
        highTempAlertActive = true;
        showHighTempAlert(temp, threshold);
    }

    private void showHighTempAlert(float temp, float threshold) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        Intent alertIntent = new Intent(this, HighTempAlertActivity.class);
        alertIntent.putExtra(HighTempAlertActivity.EXTRA_TEMP_C, temp);
        alertIntent.putExtra(HighTempAlertActivity.EXTRA_THRESHOLD_C, threshold);
        alertIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent alertPendingIntent = PendingIntent.getActivity(this, 1, alertIntent, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, ALERT_CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("! 高温警告")
                .setContentText(Math.round(temp) + "°C 已超过 "
                        + TemperatureWarningSettings.formatThreshold(threshold))
                .setContentIntent(alertPendingIntent)
                .setFullScreenIntent(alertPendingIntent, true)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build();
        manager.notify(HIGH_TEMP_NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "性能监控记录",
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void createAlertChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                ALERT_CHANNEL_ID,
                "高温告警",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("温度超过阈值时弹出全局提醒");
        channel.setShowBadge(true);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
