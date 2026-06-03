package com.vibecoding.monitor;

final class MetricSnapshot {
    final long timestampMs;
    final float cpuPercent;
    final float memoryPercent;
    final float storagePercent;
    final float batteryPercent;
    final float batteryTempC;
    final float deviceTempC;
    final String tempSource;
    final int coreCount;
    final String batteryState;

    MetricSnapshot(
            long timestampMs,
            float cpuPercent,
            float memoryPercent,
            float storagePercent,
            float batteryPercent,
            float batteryTempC,
            float deviceTempC,
            String tempSource,
            int coreCount,
            String batteryState) {
        this.timestampMs = timestampMs;
        this.cpuPercent = clamp(cpuPercent);
        this.memoryPercent = clamp(memoryPercent);
        this.storagePercent = clamp(storagePercent);
        this.batteryPercent = clamp(batteryPercent);
        this.batteryTempC = batteryTempC;
        this.deviceTempC = deviceTempC;
        this.tempSource = tempSource;
        this.coreCount = coreCount;
        this.batteryState = batteryState;
    }

    static MetricSnapshot empty() {
        return new MetricSnapshot(
                System.currentTimeMillis(),
                0f,
                0f,
                0f,
                0f,
                Float.NaN,
                Float.NaN,
                "暂无",
                Runtime.getRuntime().availableProcessors(),
                "未知");
    }

    float displayTempC() {
        if (!Float.isNaN(deviceTempC)) {
            return deviceTempC;
        }
        return batteryTempC;
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(100f, value));
    }
}
