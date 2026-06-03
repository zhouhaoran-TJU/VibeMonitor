package com.vibecoding.monitor;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MetricSampler sampler;
    private MonitorDashboardView dashboardView;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            MetricSnapshot snapshot = sampler.sample();
            dashboardView.setSnapshot(snapshot);
            MonitorWidgetProvider.updateAllWidgets(MainActivity.this, snapshot);
            handler.postDelayed(this, 2000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sampler = new MetricSampler(this);
        dashboardView = new MonitorDashboardView(this);
        setContentView(dashboardView);
        new UpdateManager(this).checkOnLaunch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshRunnable);
        refreshRunnable.run();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }
}
