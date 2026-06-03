package com.vibecoding.monitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

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
        setContentView(buildContentView());
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

    private View buildContentView() {
        FrameLayout root = new FrameLayout(this);
        root.addView(dashboardView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        Button addWidgetButton = new Button(this);
        addWidgetButton.setText("添加小组件");
        addWidgetButton.setTextSize(13f);
        addWidgetButton.setAllCaps(false);
        addWidgetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestAddWidget();
            }
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(116), dp(42));
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.topMargin = dp(14);
        params.rightMargin = dp(18);
        root.addView(addWidgetButton, params);
        return root;
    }

    private void requestAddWidget() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            showManualWidgetGuide();
            return;
        }

        AppWidgetManager manager = (AppWidgetManager) getSystemService(Context.APPWIDGET_SERVICE);
        if (manager == null || !manager.isRequestPinAppWidgetSupported()) {
            showManualWidgetGuide();
            return;
        }

        ComponentName provider = new ComponentName(this, MonitorWidgetProvider.class);
        boolean requested = manager.requestPinAppWidget(provider, null, null);
        Toast.makeText(this, requested ? "请在桌面弹窗中确认添加" : "当前桌面暂不支持直接添加", Toast.LENGTH_LONG).show();
    }

    private void showManualWidgetGuide() {
        new AlertDialog.Builder(this)
                .setTitle("添加小组件")
                .setMessage("当前桌面不支持 App 内直接添加。请长按桌面空白处，进入小组件列表，查找“性能监视器”。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
