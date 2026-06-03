package com.vibecoding.monitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
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
        check(false);
    }

    void checkManually() {
        Toast.makeText(activity, "正在检查更新", Toast.LENGTH_SHORT).show();
        check(true);
    }

    private void check(final boolean manual) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ReleaseInfo info = fetchReleaseInfo();
                    if (info.versionCode <= BuildConfig.VERSION_CODE) {
                        if (manual) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showLatestDialog(info);
                                }
                            });
                        }
                        return;
                    }
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showUpdateDialog(info);
                        }
                    });
                } catch (final Exception error) {
                    if (manual) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showCheckFailedDialog(error);
                            }
                        });
                    }
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
                object.optString("notes", "发现新版本"),
                object.optString("publishedAt", ""));
    }

    private void showUpdateDialog(final ReleaseInfo info) {
        new AlertDialog.Builder(activity)
                .setTitle("发现新版本")
                .setMessage(buildReleaseMessage(info))
                .setNegativeButton("稍后", null)
                .setPositiveButton("下载更新", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        downloadAndInstall(info);
                    }
                })
                .show();
    }

    private void showLatestDialog(ReleaseInfo info) {
        new AlertDialog.Builder(activity)
                .setTitle("已是最新版本")
                .setMessage(buildReleaseMessage(info))
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showCheckFailedDialog(Exception error) {
        new AlertDialog.Builder(activity)
                .setTitle("检查更新失败")
                .setMessage("当前版本：" + BuildConfig.VERSION_NAME
                        + " (" + BuildConfig.VERSION_CODE + ")"
                        + "\n清单地址：" + BuildConfig.UPDATE_MANIFEST_URL
                        + "\n失败原因：" + readableError(error))
                .setPositiveButton("知道了", null)
                .show();
    }

    private void downloadAndInstall(final ReleaseInfo info) {
        final ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("下载更新");
        progressDialog.setMessage("正在连接服务器");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(info.apkSize <= 0L);
        progressDialog.setMax(info.apkSize > 0L ? 100 : 0);
        progressDialog.setCancelable(false);
        progressDialog.show();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File apk = downloadApk(info, progressDialog);
                    verifyApk(info, apk);
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            Toast.makeText(activity, "下载完成，准备安装", Toast.LENGTH_SHORT).show();
                            install(apk);
                        }
                    });
                } catch (final Exception error) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            showDownloadFailedDialog(error);
                        }
                    });
                }
            }
        }, "update-download");
        thread.start();
    }

    private File downloadApk(final ReleaseInfo info, final ProgressDialog progressDialog) throws Exception {
        File dir = new File(activity.getCacheDir(), "downloads");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建下载目录");
        }
        File apk = new File(dir, "VibeMonitor-beta.apk");
        HttpURLConnection connection = open(info.apkUrl);
        final long total = info.apkSize > 0L ? info.apkSize : connection.getContentLengthLong();
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(apk)) {
            byte[] buffer = new byte[8192];
            int read;
            long downloaded = 0L;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                downloaded += read;
                publishProgress(progressDialog, downloaded, total);
            }
        } finally {
            connection.disconnect();
        }
        return apk;
    }

    private void publishProgress(final ProgressDialog dialog, final long downloaded, final long total) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (total > 0L) {
                    int progress = Math.min(100, Math.round(downloaded * 100f / total));
                    dialog.setIndeterminate(false);
                    dialog.setProgress(progress);
                    dialog.setMessage(formatSize(downloaded) + " / " + formatSize(total));
                } else {
                    dialog.setMessage(formatSize(downloaded));
                }
            }
        });
    }

    private void verifyApk(ReleaseInfo info, File apk) throws Exception {
        if (info.apkSize > 0L && apk.length() != info.apkSize) {
            throw new IllegalStateException("安装包大小不一致，期望 "
                    + info.apkSize + "，实际 " + apk.length());
        }
        if (!info.sha256.isEmpty()) {
            String actual = sha256(apk);
            if (!info.sha256.equalsIgnoreCase(actual)) {
                throw new IllegalStateException("安装包 SHA-256 校验失败\n期望："
                        + info.sha256 + "\n实际：" + actual);
            }
        }
    }

    private void showDownloadFailedDialog(Exception error) {
        new AlertDialog.Builder(activity)
                .setTitle("下载更新失败")
                .setMessage(readableError(error))
                .setPositiveButton("知道了", null)
                .show();
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

    private String buildReleaseMessage(ReleaseInfo info) {
        String publishedAt = info.publishedAt.isEmpty() ? "未提供" : info.publishedAt;
        String sha = info.sha256.isEmpty() ? "未提供" : info.sha256;
        return "当前版本：" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                + "\n远端版本：" + info.versionName + " (" + info.versionCode + ")"
                + "\nAPK 大小：" + formatSize(info.apkSize)
                + "\n发布时间：" + publishedAt
                + "\nSHA-256：" + sha
                + "\n\n更新说明：\n" + info.notes
                + "\n\n下载地址：\n" + info.apkUrl;
    }

    private String readableError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    private static final class ReleaseInfo {
        final int versionCode;
        final String versionName;
        final String apkUrl;
        final long apkSize;
        final String sha256;
        final String notes;
        final String publishedAt;

        ReleaseInfo(
                int versionCode,
                String versionName,
                String apkUrl,
                long apkSize,
                String sha256,
                String notes,
                String publishedAt) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
            this.sha256 = sha256;
            this.notes = notes;
            this.publishedAt = publishedAt;
        }
    }
}
