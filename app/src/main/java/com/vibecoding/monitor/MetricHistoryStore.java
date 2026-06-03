package com.vibecoding.monitor;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class MetricHistoryStore {
    private static final String PREFS = "metric_history";
    private static final String KEY_SAMPLES = "samples";
    private static final int MAX_SAMPLES = 1440;
    private static final long MIN_SAMPLE_INTERVAL_MS = 55000L;

    private final SharedPreferences preferences;

    MetricHistoryStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void append(MetricSnapshot snapshot) {
        List<MetricSnapshot> snapshots = load();
        long lastTimestamp = snapshots.isEmpty() ? 0L : snapshots.get(snapshots.size() - 1).timestampMs;
        if (Math.abs(snapshot.timestampMs - lastTimestamp) < MIN_SAMPLE_INTERVAL_MS) {
            return;
        }
        snapshots.add(snapshot);
        long cutoffMs = snapshot.timestampMs - 24L * 60L * 60L * 1000L;
        while (!snapshots.isEmpty() && snapshots.get(0).timestampMs < cutoffMs) {
            snapshots.remove(0);
        }
        while (snapshots.size() > MAX_SAMPLES) {
            snapshots.remove(0);
        }
        save(snapshots);
    }

    synchronized List<MetricSnapshot> load() {
        ArrayList<MetricSnapshot> snapshots = new ArrayList<>();
        String raw = preferences.getString(KEY_SAMPLES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                snapshots.add(fromJson(object));
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(KEY_SAMPLES).apply();
        }
        return snapshots;
    }

    private void save(List<MetricSnapshot> snapshots) {
        JSONArray array = new JSONArray();
        for (MetricSnapshot snapshot : snapshots) {
            array.put(toJson(snapshot));
        }
        preferences.edit().putString(KEY_SAMPLES, array.toString()).apply();
    }

    private JSONObject toJson(MetricSnapshot snapshot) {
        JSONObject object = new JSONObject();
        try {
            object.put("timestampMs", snapshot.timestampMs);
            object.put("cpuPercent", snapshot.cpuPercent);
            object.put("memoryPercent", snapshot.memoryPercent);
            object.put("storagePercent", snapshot.storagePercent);
            object.put("batteryPercent", snapshot.batteryPercent);
            object.put("batteryTempC", snapshot.batteryTempC);
            object.put("deviceTempC", snapshot.deviceTempC);
            object.put("tempSource", snapshot.tempSource);
            object.put("coreCount", snapshot.coreCount);
            object.put("batteryState", snapshot.batteryState);
        } catch (JSONException ignored) {
            // JSONObject backed by memory should not fail for primitive values.
        }
        return object;
    }

    private MetricSnapshot fromJson(JSONObject object) {
        return new MetricSnapshot(
                object.optLong("timestampMs", System.currentTimeMillis()),
                (float) object.optDouble("cpuPercent", 0d),
                (float) object.optDouble("memoryPercent", 0d),
                (float) object.optDouble("storagePercent", 0d),
                (float) object.optDouble("batteryPercent", 0d),
                optFloat(object, "batteryTempC", Float.NaN),
                optFloat(object, "deviceTempC", Float.NaN),
                object.optString("tempSource", "暂无"),
                object.optInt("coreCount", Runtime.getRuntime().availableProcessors()),
                object.optString("batteryState", "未知"));
    }

    private float optFloat(JSONObject object, String key, float fallback) {
        if (!object.has(key) || object.isNull(key)) {
            return fallback;
        }
        return (float) object.optDouble(key, fallback);
    }
}
