package com.vibecoding.monitor;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

final class HighTempOverlay {
    private static final long AUTO_DISMISS_MS = 30000L;
    private static View currentView;

    private HighTempOverlay() {
    }

    static boolean show(Context context, float temp, float threshold) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return false;
        }
        final Context appContext = context.getApplicationContext();
        final WindowManager manager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        if (manager == null) {
            return false;
        }
        dismiss(appContext);
        final View view = buildView(appContext, temp, threshold);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(appContext, 28);
        try {
            manager.addView(view, params);
            currentView = view;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    dismiss(appContext);
                }
            }, AUTO_DISMISS_MS);
            return true;
        } catch (RuntimeException ignored) {
            currentView = null;
            return false;
        }
    }

    static void dismiss(Context context) {
        if (currentView == null) {
            return;
        }
        WindowManager manager = (WindowManager) context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        if (manager != null) {
            try {
                manager.removeView(currentView);
            } catch (RuntimeException ignored) {
            }
        }
        currentView = null;
    }

    private static View buildView(final Context context, float temp, float threshold) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xee111827);
        bg.setCornerRadius(dp(context, 12));
        bg.setStroke(dp(context, 1), 0xffff4757);
        root.setBackground(bg);

        TextView icon = new TextView(context);
        icon.setText("!");
        icon.setTextColor(0xffffffff);
        icon.setTextSize(24f);
        icon.setGravity(Gravity.CENTER);
        icon.setTypeface(null, Typeface.BOLD);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(0xffff4757);
        icon.setBackground(iconBg);
        root.addView(icon, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));

        TextView message = new TextView(context);
        message.setText("高温警告  " + formatTemp(temp) + "\n超过 "
                + TemperatureWarningSettings.formatThreshold(threshold) + "，请立即降载散热");
        message.setTextColor(0xffffffff);
        message.setTextSize(15f);
        message.setLineSpacing(dp(context, 2), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        messageParams.leftMargin = dp(context, 12);
        root.addView(message, messageParams);

        Button close = new Button(context);
        close.setText("关闭");
        close.setTextSize(12f);
        close.setAllCaps(false);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss(context);
            }
        });
        root.addView(close, new LinearLayout.LayoutParams(dp(context, 64), dp(context, 42)));

        LinearLayout outer = new LinearLayout(context);
        outer.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        outer.addView(root, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return outer;
    }

    private static int overlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private static String formatTemp(float temp) {
        if (Float.isNaN(temp)) {
            return "--°C";
        }
        return String.format(Locale.CHINA, "%.1f°C", temp);
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
