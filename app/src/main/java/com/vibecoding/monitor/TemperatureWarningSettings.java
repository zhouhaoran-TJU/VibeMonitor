package com.vibecoding.monitor;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

final class TemperatureWarningSettings {
    static final String PREFS = "ui_prefs";
    static final String KEY_HIGH_TEMP_WARNING_C = "high_temp_warning_c";
    static final float DEFAULT_HIGH_TEMP_WARNING_C = 70f;
    static final float HIGH_TEMP_RESET_GAP_C = 5f;

    private TemperatureWarningSettings() {
    }

    static float threshold(Context context) {
        return prefs(context).getFloat(KEY_HIGH_TEMP_WARNING_C, DEFAULT_HIGH_TEMP_WARNING_C);
    }

    static void saveThreshold(Context context, float value) {
        prefs(context).edit().putFloat(KEY_HIGH_TEMP_WARNING_C, value).apply();
    }

    static String formatThreshold(float value) {
        return formatThresholdValue(value) + "°C";
    }

    static String formatThresholdValue(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
