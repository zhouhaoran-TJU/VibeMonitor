package com.vibecoding.monitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int PIN_WIDGET_REQUEST_CODE = 1001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MetricSampler sampler;
    private MonitorDashboardView dashboardView;
    private UpdateManager updateManager;

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
        updateManager = new UpdateManager(this);
        setContentView(buildContentView());
        updateManager.checkOnLaunch();
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

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);

        Button updateButton = buildActionButton("检查更新");
        updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateManager.checkManually();
            }
        });
        actions.addView(updateButton, new LinearLayout.LayoutParams(dp(116), dp(42)));

        Button addWidgetButton = buildActionButton("添加小组件");
        addWidgetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestAddWidget();
            }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(116), dp(42));
        buttonParams.topMargin = dp(6);
        actions.addView(addWidgetButton, buttonParams);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(116), dp(90));
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.topMargin = dp(14);
        params.rightMargin = dp(18);
        root.addView(actions, params);
        return root;
    }

    private Button buildActionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13f);
        button.setAllCaps(false);
        return button;
    }

    private void requestAddWidget() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            showManualWidgetGuide();
            return;
        }

        AppWidgetManager manager = (AppWidgetManager) getSystemService(Context.APPWIDGET_SERVICE);
        if (manager == null || !manager.isRequestPinAppWidgetSupported()) {
            showWidgetDiagnostics(false);
            return;
        }

        showWidgetPinConfirm(manager);
    }

    private void showWidgetPinConfirm(final AppWidgetManager manager) {
        new AlertDialog.Builder(this)
                .setTitle("添加桌面小组件")
                .setMessage(buildWidgetDiagnostics(true)
                        + "\n\n点击“请求添加”后，如果桌面支持，会弹出系统确认框。若没有弹窗，请使用手动方式添加。")
                .setNegativeButton("手动方式", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showManualWidgetGuide();
                    }
                })
                .setPositiveButton("请求添加", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        requestPinWidget(manager);
                    }
                })
                .show();
    }

    private void requestPinWidget(AppWidgetManager manager) {
        ComponentName provider = new ComponentName(this, MonitorWidgetProvider.class);
        Intent callbackIntent = new Intent(this, WidgetPinCallbackReceiver.class);
        PendingIntent callback = PendingIntent.getBroadcast(
                this,
                PIN_WIDGET_REQUEST_CODE,
                callbackIntent,
                pendingIntentFlags());
        boolean requested = manager.requestPinAppWidget(provider, null, callback);
        if (requested) {
            new AlertDialog.Builder(this)
                    .setTitle("已发送添加请求")
                    .setMessage("系统已接收请求。请查看桌面是否弹出确认框，或返回桌面查看小组件是否已添加。\n\n如果仍无反应，说明当前桌面对第三方 App 的直接添加接口有限制，请使用手动方式。")
                    .setNegativeButton("手动方式", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            showManualWidgetGuide();
                        }
                    })
                    .setPositiveButton("回到桌面", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            openHome();
                        }
                    })
                    .show();
        } else {
            showWidgetDiagnostics(false);
        }
    }

    private void showManualWidgetGuide() {
        new AlertDialog.Builder(this)
                .setTitle("添加小组件")
                .setMessage(buildWidgetDiagnostics(false)
                        + "\n\n手动添加步骤：\n1. 回到桌面\n2. 双指捏合或长按空白处\n3. 进入“小组件/添加工具”\n4. 搜索或查找“性能监视器”\n5. 选择 4x2 小组件添加")
                .setNegativeButton("关闭", null)
                .setPositiveButton("回到桌面", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openHome();
                    }
                })
                .show();
    }

    private void showWidgetDiagnostics(boolean supported) {
        new AlertDialog.Builder(this)
                .setTitle("小组件添加诊断")
                .setMessage(buildWidgetDiagnostics(supported))
                .setNegativeButton("手动方式", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showManualWidgetGuide();
                    }
                })
                .setPositiveButton("知道了", null)
                .show();
    }

    private String buildWidgetDiagnostics(boolean pinSupported) {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName provider = new ComponentName(this, MonitorWidgetProvider.class);
        int widgetCount = manager.getAppWidgetIds(provider).length;
        return "应用包名：" + getPackageName()
                + "\n桌面包名：" + resolveHomePackage()
                + "\n系统直接添加接口：" + (pinSupported ? "支持" : "不支持或被桌面拦截")
                + "\n当前已添加数量：" + widgetCount
                + "\n小组件名称：性能监视器"
                + "\n小组件规格：4x2";
    }

    private String resolveHomePackage() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (info == null || info.activityInfo == null) {
            return "未知";
        }
        return info.activityInfo.packageName;
    }

    private void openHome() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
