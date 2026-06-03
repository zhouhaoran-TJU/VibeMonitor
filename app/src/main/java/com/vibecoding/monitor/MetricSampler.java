package com.vibecoding.monitor;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

final class MetricSampler {
    private final Context context;
    private CpuTimes previousCpuTimes;

    MetricSampler(Context context) {
        this.context = context.getApplicationContext();
    }

    MetricSnapshot sample() {
        BatteryData batteryData = readBatteryData();
        ThermalData thermalData = readThermalData();
        float deviceTemp = !Float.isNaN(thermalData.temperatureC)
                ? thermalData.temperatureC
                : batteryData.temperatureC;
        String source = !Float.isNaN(thermalData.temperatureC)
                ? thermalData.source
                : "电池";

        return new MetricSnapshot(
                System.currentTimeMillis(),
                readCpuPercent(),
                readMemoryPercent(),
                readStoragePercent(),
                batteryData.percent,
                batteryData.temperatureC,
                deviceTemp,
                source,
                Runtime.getRuntime().availableProcessors(),
                batteryData.state);
    }

    private float readCpuPercent() {
        CpuTimes current = readCpuTimes();
        if (current == null) {
            return 0f;
        }
        if (previousCpuTimes == null) {
            previousCpuTimes = current;
            return 0f;
        }

        long idleDelta = current.idle - previousCpuTimes.idle;
        long totalDelta = current.total - previousCpuTimes.total;
        previousCpuTimes = current;
        if (totalDelta <= 0L) {
            return 0f;
        }
        return (totalDelta - idleDelta) * 100f / totalDelta;
    }

    private CpuTimes readCpuTimes() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) {
                return null;
            }
            String[] parts = line.trim().split("\\s+");
            long user = parseLong(parts, 1);
            long nice = parseLong(parts, 2);
            long system = parseLong(parts, 3);
            long idle = parseLong(parts, 4);
            long iowait = parseLong(parts, 5);
            long irq = parseLong(parts, 6);
            long softIrq = parseLong(parts, 7);
            long steal = parseLong(parts, 8);
            long idleAll = idle + iowait;
            long total = user + nice + system + idle + iowait + irq + softIrq + steal;
            return new CpuTimes(total, idleAll);
        } catch (IOException ignored) {
            return null;
        }
    }

    private float readMemoryPercent() {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return 0f;
        }
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        if (info.totalMem <= 0L) {
            return 0f;
        }
        return (info.totalMem - info.availMem) * 100f / info.totalMem;
    }

    private float readStoragePercent() {
        File path = Environment.getDataDirectory();
        StatFs statFs = new StatFs(path.getPath());
        long total = statFs.getBlockCountLong() * statFs.getBlockSizeLong();
        long available = statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
        if (total <= 0L) {
            return 0f;
        }
        return (total - available) * 100f / total;
    }

    private BatteryData readBatteryData() {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) {
            return new BatteryData(0f, Float.NaN, "未知");
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        float percent = scale > 0 ? level * 100f / scale : 0f;
        float temp = tempTenths == Integer.MIN_VALUE ? Float.NaN : tempTenths / 10f;
        return new BatteryData(percent, temp, formatBatteryStatus(status));
    }

    private ThermalData readThermalData() {
        File thermalRoot = new File("/sys/class/thermal");
        File[] zones = thermalRoot.listFiles();
        if (zones == null) {
            return ThermalData.empty();
        }

        ThermalData best = ThermalData.empty();
        for (File zone : zones) {
            if (!zone.getName().startsWith("thermal_zone")) {
                continue;
            }
            String type = readFirstLine(new File(zone, "type"));
            String rawTemp = readFirstLine(new File(zone, "temp"));
            if (rawTemp == null) {
                continue;
            }
            float temp = normalizeThermalTemp(rawTemp);
            if (Float.isNaN(temp) || temp < -20f || temp > 130f) {
                continue;
            }
            if (isBetterThermalSource(type, best.source)) {
                best = new ThermalData(temp, cleanThermalType(type));
            }
        }
        return best;
    }

    private boolean isBetterThermalSource(String candidate, String current) {
        int candidateScore = thermalScore(candidate);
        int currentScore = thermalScore(current);
        return candidateScore > currentScore || current == null;
    }

    private int thermalScore(String type) {
        if (type == null) {
            return 0;
        }
        String lower = type.toLowerCase(Locale.US);
        if (lower.contains("cpu") || lower.contains("soc") || lower.contains("ap")) {
            return 4;
        }
        if (lower.contains("skin") || lower.contains("board") || lower.contains("case")) {
            return 3;
        }
        if (lower.contains("battery") || lower.contains("batt")) {
            return 2;
        }
        return 1;
    }

    private float normalizeThermalTemp(String rawTemp) {
        try {
            float value = Float.parseFloat(rawTemp.trim());
            if (Math.abs(value) > 1000f) {
                return value / 1000f;
            }
            if (Math.abs(value) > 150f) {
                return value / 10f;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return Float.NaN;
        }
    }

    private String cleanThermalType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "thermal";
        }
        return type.trim().replace('_', ' ');
    }

    private String readFirstLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (IOException ignored) {
            return null;
        }
    }

    private String formatBatteryStatus(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "放电中";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "已充满";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "未充电";
            default:
                return "未知";
        }
    }

    private long parseLong(String[] parts, int index) {
        if (index >= parts.length) {
            return 0L;
        }
        try {
            return Long.parseLong(parts[index]);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static final class CpuTimes {
        final long total;
        final long idle;

        CpuTimes(long total, long idle) {
            this.total = total;
            this.idle = idle;
        }
    }

    private static final class BatteryData {
        final float percent;
        final float temperatureC;
        final String state;

        BatteryData(float percent, float temperatureC, String state) {
            this.percent = percent;
            this.temperatureC = temperatureC;
            this.state = state;
        }
    }

    private static final class ThermalData {
        final float temperatureC;
        final String source;

        ThermalData(float temperatureC, String source) {
            this.temperatureC = temperatureC;
            this.source = source;
        }

        static ThermalData empty() {
            return new ThermalData(Float.NaN, null);
        }
    }
}
