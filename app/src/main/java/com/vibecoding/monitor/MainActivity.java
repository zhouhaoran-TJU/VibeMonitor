package com.vibecoding.monitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.Manifest;
import android.content.SharedPreferences;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int PIN_WIDGET_REQUEST_CODE = 1001;
    private static final String KEY_DISPLAY_STYLE = "display_style";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MetricSampler sampler;
    private MetricHistoryStore historyStore;
    private MonitorDashboardView dashboardView;
    private UpdateManager updateManager;
    private SharedPreferences uiPrefs;
    private DisplayStyle displayStyle = DisplayStyle.DEFAULT;
    private Button styleButton;
    private ScrollView dashboardScrollView;
    private boolean highTempWarningShown;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            MetricSnapshot snapshot = sampler.sample();
            historyStore.append(snapshot);
            dashboardView.setSnapshot(snapshot);
            dashboardView.setHistory(historyStore.load());
            MonitorWidgetProvider.updateAllWidgets(MainActivity.this, snapshot);
            maybeShowHighTempWarning(snapshot);
            handler.postDelayed(this, 2000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uiPrefs = getSharedPreferences(TemperatureWarningSettings.PREFS, MODE_PRIVATE);
        displayStyle = loadDisplayStyle();
        sampler = new MetricSampler(this);
        historyStore = new MetricHistoryStore(this);
        dashboardView = new MonitorDashboardView(this);
        dashboardView.setDisplayStyle(displayStyle);
        updateManager = new UpdateManager(this);
        setContentView(buildContentView());
        requestNotificationPermission();
        startMonitorService();
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(displayStyle == DisplayStyle.NIGHT ? 0xff101722 : 0xfff6f7fb);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(12), dp(8), dp(12), dp(6));

        Button updateButton = buildActionButton("更新");
        updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateManager.checkManually();
            }
        });
        actions.addView(updateButton, new LinearLayout.LayoutParams(0, dp(40), 1f));

        Button addWidgetButton = buildActionButton("小组件");
        addWidgetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestAddWidget();
            }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        buttonParams.leftMargin = dp(8);
        actions.addView(addWidgetButton, buttonParams);

        Button thresholdButton = buildActionButton("阈值");
        thresholdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showThresholdDialog();
            }
        });
        LinearLayout.LayoutParams thresholdParams = new LinearLayout.LayoutParams(0, dp(40), 0.8f);
        thresholdParams.leftMargin = dp(8);
        actions.addView(thresholdButton, thresholdParams);

        styleButton = buildActionButton(styleLabel());
        styleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleDisplayStyle();
            }
        });
        LinearLayout.LayoutParams styleParams = new LinearLayout.LayoutParams(0, dp(40), 0.8f);
        styleParams.leftMargin = dp(8);
        actions.addView(styleButton, styleParams);

        root.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)));
        dashboardScrollView = new ScrollView(this);
        dashboardScrollView.setFillViewport(false);
        dashboardScrollView.setBackgroundColor(displayStyle == DisplayStyle.NIGHT ? 0xff101722 : 0xfff6f7fb);
        dashboardView.setMinimumHeight(dp(800));
        dashboardScrollView.addView(dashboardView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                dp(800)));
        root.addView(dashboardScrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        return root;
    }

    private Button buildActionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13f);
        button.setAllCaps(false);
        return button;
    }

    private DisplayStyle loadDisplayStyle() {
        String raw = uiPrefs.getString(KEY_DISPLAY_STYLE, DisplayStyle.DEFAULT.name());
        try {
            return DisplayStyle.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return DisplayStyle.DEFAULT;
        }
    }

    private void toggleDisplayStyle() {
        displayStyle = displayStyle == DisplayStyle.DEFAULT ? DisplayStyle.NIGHT : DisplayStyle.DEFAULT;
        uiPrefs.edit().putString(KEY_DISPLAY_STYLE, displayStyle.name()).apply();
        dashboardView.setDisplayStyle(displayStyle);
        if (styleButton != null) {
            styleButton.setText(styleLabel());
        }
        View root = dashboardView.getRootView();
        if (root != null) {
            root.setBackgroundColor(displayStyle == DisplayStyle.NIGHT ? 0xff101722 : 0xfff6f7fb);
        }
        if (dashboardScrollView != null) {
            dashboardScrollView.setBackgroundColor(displayStyle == DisplayStyle.NIGHT ? 0xff101722 : 0xfff6f7fb);
        }
    }

    private String styleLabel() {
        return displayStyle == DisplayStyle.NIGHT ? "暗夜" : "默认";
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001);
    }

    private void startMonitorService() {
        Intent intent = new Intent(this, MonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void maybeShowHighTempWarning(MetricSnapshot snapshot) {
        float temp = snapshot.displayTempC();
        if (Float.isNaN(temp)) {
            return;
        }
        float threshold = highTempWarningThreshold();
        if (temp < threshold - TemperatureWarningSettings.HIGH_TEMP_RESET_GAP_C) {
            highTempWarningShown = false;
            return;
        }
        if (temp < threshold || highTempWarningShown || isFinishing()) {
            return;
        }
        highTempWarningShown = true;
        new AlertDialog.Builder(this)
                .setTitle("温度过高警告")
                .setMessage("当前温度已达到 " + Math.round(temp)
                        + "°C，超过 " + TemperatureWarningSettings.formatThreshold(threshold)
                        + " 阈值。\n\n建议立即停止高负载任务、关闭充电或让设备散热。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showThresholdDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(TemperatureWarningSettings.formatThresholdValue(highTempWarningThreshold()));
        input.setSelectAllOnFocus(true);
        input.setHint("例如 70");
        int padding = dp(20);
        input.setPadding(padding, dp(8), padding, dp(8));

        new AlertDialog.Builder(this)
                .setTitle("高温告警阈值")
                .setMessage("输入温度阈值，单位 °C。当前温度达到该值时会弹出告警。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveThreshold(input.getText().toString());
                    }
                })
                .show();
    }

    private void saveThreshold(String raw) {
        try {
            float value = Float.parseFloat(raw.trim());
            if (value < 30f || value > 120f) {
                Toast.makeText(this, "请输入 30 到 120 之间的温度", Toast.LENGTH_LONG).show();
                return;
            }
            TemperatureWarningSettings.saveThreshold(this, value);
            highTempWarningShown = false;
            Toast.makeText(this, "告警阈值已设为 "
                    + TemperatureWarningSettings.formatThreshold(value), Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException error) {
            Toast.makeText(this, "请输入有效数字", Toast.LENGTH_LONG).show();
        }
    }

    private float highTempWarningThreshold() {
        return TemperatureWarningSettings.threshold(this);
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
