package com.vibecoding.monitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

final class UpdateManager {
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final Activity activity;

    UpdateManager(Activity activity) {
        this.activity = activity;
    }

    void checkOnLaunch() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ReleaseInfo info = fetchReleaseInfo();
                    if (info.versionCode <= BuildConfig.VERSION_CODE) {
                        return;
                    }
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showUpdateDialog(info);
                        }
                    });
                } catch (Exception ignored) {
                    // 更新检查不应影响监控主流程。
                }
            }
        }, "update-check");
        thread.start();
    }

    private ReleaseInfo fetchReleaseInfo() throws Exception {
        String json = readText(BuildConfig.UPDATE_MANIFEST_URL);
        JSONObject object = new JSONObject(json);
        return new ReleaseInfo(
                object.getInt("versionCode"),
                object.getString("versionName"),
                object.getString("apkUrl"),
                object.optLong("apkSize", 0L),
                object.optString("sha256", ""),
                object.optString("notes", "发现新版本"));
    }

    private void showUpdateDialog(final ReleaseInfo info) {
        String message = "版本：" + info.versionName
                + "\n大小：" + formatSize(info.apkSize)
                + "\n\n" + info.notes;
        new AlertDialog.Builder(activity)
                .setTitle("发现新版本")
                .setMessage(message)
                .setNegativeButton("稍后", null)
                .setPositiveButton("下载更新", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        downloadAndInstall(info);
                    }
                })
                .show();
    }

    private void downloadAndInstall(final ReleaseInfo info) {
        Toast.makeText(activity, "开始下载更新", Toast.LENGTH_SHORT).show();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File apk = downloadApk(info.apkUrl);
                    if (!info.sha256.isEmpty()) {
                        String actual = sha256(apk);
                        if (!info.sha256.equalsIgnoreCase(actual)) {
                            throw new IllegalStateException("安装包校验失败");
                        }
                    }
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            install(apk);
                        }
                    });
                } catch (final Exception error) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }, "update-download");
        thread.start();
    }

    private File downloadApk(String apkUrl) throws Exception {
        File dir = new File(activity.getCacheDir(), "downloads");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建下载目录");
        }
        File apk = new File(dir, "VibeMonitor-beta.apk");
        HttpURLConnection connection = open(apkUrl);
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(apk)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
        return apk;
    }

    private void install(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            try {
                activity.startActivity(intent);
            } catch (ActivityNotFoundException ignored) {
                activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            }
            Toast.makeText(activity, "请允许本应用安装未知来源应用后重试", Toast.LENGTH_LONG).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(intent);
    }

    private String readText(String url) throws Exception {
        HttpURLConnection connection = open(url);
        try (InputStream input = connection.getInputStream()) {
            byte[] buffer = new byte[4096];
            StringBuilder builder = new StringBuilder();
            int read;
            while ((read = input.read(buffer)) != -1) {
                builder.append(new String(buffer, 0, read, "UTF-8"));
            }
            return builder.toString();
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("网络请求失败：" + code);
        }
        return connection;
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return builder.toString();
    }

    private String formatSize(long bytes) {
        if (bytes <= 0L) {
            return "未知";
        }
        return String.format(Locale.CHINA, "%.1f MB", bytes / 1024f / 1024f);
    }

    private static final class ReleaseInfo {
        final int versionCode;
        final String versionName;
        final String apkUrl;
        final long apkSize;
        final String sha256;
        final String notes;

        ReleaseInfo(int versionCode, String versionName, String apkUrl, long apkSize, String sha256, String notes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
            this.sha256 = sha256;
            this.notes = notes;
        }
    }
}
