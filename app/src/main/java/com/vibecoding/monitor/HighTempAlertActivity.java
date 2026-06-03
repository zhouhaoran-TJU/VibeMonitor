package com.vibecoding.monitor;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public final class HighTempAlertActivity extends Activity {
    static final String EXTRA_TEMP_C = "temp_c";
    static final String EXTRA_THRESHOLD_C = "threshold_c";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float temp = getIntent().getFloatExtra(EXTRA_TEMP_C, Float.NaN);
        float threshold = getIntent().getFloatExtra(
                EXTRA_THRESHOLD_C,
                TemperatureWarningSettings.threshold(this));
        setContentView(buildContentView(temp, threshold));
    }

    private View buildContentView(float temp, float threshold) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        root.setBackgroundColor(0xff111827);

        TextView dangerIcon = new TextView(this);
        dangerIcon.setText("!");
        dangerIcon.setTextColor(0xffffffff);
        dangerIcon.setTextSize(36f);
        dangerIcon.setGravity(Gravity.CENTER);
        dangerIcon.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(0xffff4757);
        dangerIcon.setBackground(iconBg);
        root.addView(dangerIcon, new LinearLayout.LayoutParams(dp(72), dp(72)));

        TextView title = new TextView(this);
        title.setText("高温警告");
        title.setTextColor(0xffffffff);
        title.setTextSize(24f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(18);
        root.addView(title, titleParams);

        TextView value = new TextView(this);
        value.setText(formatTemp(temp));
        value.setTextColor(0xffff4757);
        value.setTextSize(48f);
        value.setGravity(Gravity.CENTER);
        value.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dp(18);
        root.addView(value, valueParams);

        TextView message = new TextView(this);
        message.setText("已超过 " + TemperatureWarningSettings.formatThreshold(threshold)
                + "\n请立即降载散热");
        message.setTextColor(0xffd1d5db);
        message.setTextSize(16f);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(16);
        root.addView(message, messageParams);

        Button button = new Button(this);
        button.setText("知道了");
        button.setAllCaps(false);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48));
        buttonParams.topMargin = dp(28);
        root.addView(button, buttonParams);
        return root;
    }

    private String formatTemp(float temp) {
        if (Float.isNaN(temp)) {
            return "--°C";
        }
        return String.format(Locale.CHINA, "%.1f°C", temp);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
