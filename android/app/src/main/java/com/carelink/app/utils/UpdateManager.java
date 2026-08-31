package com.carelink.app.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.carelink.app.R;
import com.carelink.app.data.remote.api.AuthApi;
import com.carelink.app.data.remote.dto.BaseResponse;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.HttpsURLConnection;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 应用自动更新管理器
 * 1. 调用后端 /api/app/version/check 检查最新版本
 * 2. 有新版本时弹出更新对话框
 * 3. 支持 APK 下载、安装、强制更新
 */
public class UpdateManager {

    private static final String BASE_URL = ApiConfig.HTTP_BASE_URL;


    private final Context context;
    private final java.lang.ref.WeakReference<Activity> activityRef;
    private final Handler mainHandler;
    private final Gson gson;
    private AlertDialog updateDialog;
    private ProgressBar progressBar;

    /**
     * @param activity 用于显示对话框，必须是前台 Activity
     */
    public UpdateManager(Activity activity) {
        this.context = activity.getApplicationContext();
        this.activityRef = new java.lang.ref.WeakReference<>(activity);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
    }

    private Activity getActivity() {
        Activity a = activityRef != null ? activityRef.get() : null;
        if (a == null || a.isFinishing()) return null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && a.isDestroyed()) return null;
        return a;
    }

    /**
     * 检查是否有新版本（调用方传入当前 Activity）
     */
    public void checkForUpdate(Activity callerActivity) {
        try {
            PackageInfo pInfo = getPackageInfo();
            String currentVersion = pInfo.versionName;
            int currentVersionCode = getVersionCode(pInfo);

            LogUtil.d("UpdateManager", "检查版本: 当前=" + currentVersion + "(" + currentVersionCode + ")");
            showToastSafe(callerActivity, "正在检查版本更新...", false);
            checkFromServer(currentVersion, currentVersionCode, callerActivity);
        } catch (Exception e) {
            LogUtil.e("UpdateManager", "版本检查异常", e);
        }
    }

    /**
     * 从服务器检查最新版本
     */
    private void checkFromServer(String currentVersion, int currentVersionCode, Activity callerActivity) {
        // 独立 Retrofit 实例，无 AuthInterceptor（版本检查不需要登录态）
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        retrofit2.Retrofit retrofit = new retrofit2.Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build();
        AuthApi api = retrofit.create(AuthApi.class);

        LogUtil.d("UpdateManager", "发送版本检查: GET /api/app/version/check");

        api.checkVersion("android", currentVersion, currentVersionCode)
                .enqueue(new Callback<BaseResponse<Object>>() {
                    @Override
                    public void onResponse(Call<BaseResponse<Object>> call,
                                           Response<BaseResponse<Object>> response) {
                        LogUtil.d("UpdateManager", "收到响应: HTTP " + response.code());

                        if (!response.isSuccessful() || response.body() == null) {
                            LogUtil.w("UpdateManager", "HTTP失败: " + response.code());
                            return;
                        }

                        BaseResponse<Object> resp = response.body();
                        LogUtil.d("UpdateManager", "业务层 success=" + resp.isSuccess()
                                + ", message=" + resp.getMessage());

                        if (!resp.isSuccess()) {
                            LogUtil.w("UpdateManager", "业务错误: " + resp.getMessage());
                            return;
                        }

                        Object data = resp.getData();
                        if (data == null) {
                            LogUtil.d("UpdateManager", "已是最新版本（服务器无新数据）");
                            return;
                        }

                        try {
                            JsonObject json = gson.toJsonTree(data).getAsJsonObject();
                            LogUtil.d("UpdateManager", "版本数据: " + json.toString());

                            String latestVersion = getStringSafely(json, "version", "");
                            int latestVersionCode = getIntSafely(json, "versionCode", currentVersionCode);
                            String message = getStringSafely(json, "message",
                                    "发现新版本 v" + latestVersion + "，是否立即更新？");
                            String downloadUrl = getStringSafely(json, "downloadUrl", "");
                            boolean forceUpdate = getBooleanSafely(json, "forceUpdate", false);

                            LogUtil.d("UpdateManager", "最新版本: v" + latestVersion
                                    + " (code=" + latestVersionCode + ")"
                                    + ", 当前: " + currentVersionCode
                                    + ", 差值: " + (latestVersionCode - currentVersionCode)
                                    + ", downloadUrl: " + downloadUrl);

                            if (latestVersionCode > currentVersionCode) {
                                LogUtil.d("UpdateManager", ">>> 检测到新版本，弹出更新对话框");
                                showUpdateDialog(latestVersion, message, downloadUrl, forceUpdate, callerActivity);
                            } else {
                                LogUtil.d("UpdateManager", "当前已是最新版本");
                            }
                        } catch (Exception e) {
                            LogUtil.e("UpdateManager", "解析失败", e);
                        }

                    }

                    @Override
                    public void onFailure(Call<BaseResponse<Object>> call, Throwable t) {
                        LogUtil.e("UpdateManager", "网络失败: " + t.getMessage());
                    }
                });
    }

    /**
     * 显示更新对话框
     */
    private void showUpdateDialog(String version, String message, String downloadUrl,
                                  boolean forceUpdate, Activity callerActivity) {
        Activity act = callerActivity != null ? callerActivity : getActivity();
        if (!isActivityAlive(act)) {
            LogUtil.w("UpdateManager", "无有效Activity，跳过更新对话框");
            return;
        }

        View dialogView = LayoutInflater.from(act).inflate(R.layout.dialog_update, null);

        if (dialogView == null) {
            LogUtil.e("UpdateManager", "dialog_update 布局文件不存在");
            return;
        }

        TextView tvVersion = dialogView.findViewById(R.id.tv_version);
        TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        Button btnUpdate = dialogView.findViewById(R.id.btn_update);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);

        tvVersion.setText("发现新版本 v" + version);
        tvMessage.setText(message != null ? message : "是否立即更新？");

        AlertDialog.Builder builder = new AlertDialog.Builder(act)
                .setView(dialogView)
                .setCancelable(!forceUpdate);

        updateDialog = builder.create();
        updateDialog.setCanceledOnTouchOutside(!forceUpdate);

        btnUpdate.setOnClickListener(v -> {
            updateDialog.dismiss();
            LogUtil.d("UpdateManager", "用户点击更新，URL: " + downloadUrl);
            downloadAndInstall(downloadUrl);
        });

        if (forceUpdate) {
            btnCancel.setVisibility(View.GONE);
        } else {
            btnCancel.setOnClickListener(v -> updateDialog.dismiss());
        }

        mainHandler.post(() -> {
            if (isActivityAlive(act)) {
                try {
                    updateDialog.show();
                    showToastSafe(act, "发现新版本 v" + version + "，请点击「立即更新」", true);
                    LogUtil.d("UpdateManager", "更新对话框已显示");
                } catch (Exception e) {
                    LogUtil.e("UpdateManager", "显示对话框异常", e);
                }
            } else {
                LogUtil.w("UpdateManager", "Activity 已失效，取消显示更新对话框");
            }
        });

    }

    /**
     * 下载 APK 并安装
     */
    private void downloadAndInstall(String downloadUrl) {
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            showToastSafe(getActivity(), "下载地址无效，请稍后重试", true);
            return;
        }

        // 提取文件名
        String fileName = "app-update.apk";
        try {
            String path = new URL(downloadUrl).getPath();
            String fn = path.substring(path.lastIndexOf('/') + 1);
            if (fn.endsWith(".apk")) fileName = fn;
        } catch (Exception e) { }

        LogUtil.d("UpdateManager", "开始下载: " + downloadUrl);
        LogUtil.d("UpdateManager", "保存为: " + fileName);
        showToastSafe(getActivity(), "正在下载 v" + fileName + "...", true);

        final String finalFileName = fileName;
        new Thread(() -> {
            File apkFile;
            try {
                apkFile = downloadFile(downloadUrl, finalFileName);
            } catch (Exception e) {
                LogUtil.e("UpdateManager", "下载异常", e);
                showToastSafe(getActivity(), "下载失败，请检查网络后重试", true);

                return;
            }

            if (apkFile == null || !apkFile.exists() || apkFile.length() < 1024) {
                showToastSafe(getActivity(), "下载文件异常，请稍后重试", true);
                LogUtil.e("UpdateManager", "APK文件无效: " + (apkFile != null ? apkFile.length() : "null"));
                return;
            }

            LogUtil.d("UpdateManager", "下载完成: " + apkFile.length() / 1024 + " KB");
            installApk(apkFile);
        }).start();
    }

    private File downloadFile(String urlStr, String fileName) throws IOException {
        File cacheDir = context.getCacheDir();
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            File ext = context.getExternalCacheDir();
            if (ext != null) cacheDir = ext;
        }
        File apkFile = new File(cacheDir, fileName);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Accept", "application/vnd.android.package-archive");

            int code = conn.getResponseCode();
            LogUtil.d("UpdateManager", "下载HTTP响应: " + code);
            if (code != 200) {
                return null;
            }

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(apkFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            return apkFile;
        } finally {
            conn.disconnect();
        }
    }

    private void installApk(File apkFile) {
        Uri apkUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", apkFile);
        } else {
            apkUri = Uri.fromFile(apkFile);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else {
            showToastSafe(getActivity(), "无法启动安装器，请手动打开: " + apkFile.getName(), true);
        }
    }

    // ─── 工具 ───────────────────────────────────────────────────

    private PackageInfo getPackageInfo() throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    }

    private int getVersionCode(PackageInfo pInfo) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? (int) pInfo.getLongVersionCode()
                : pInfo.versionCode;
    }

    private void showToastSafe(Activity act, String msg, boolean longDuration) {
        if (!isActivityAlive(act)) return;
        mainHandler.post(() -> {
            try {
                if (!isActivityAlive(act)) {
                    return;
                }
                Toast.makeText(act, msg, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) { }
        });
    }

    private boolean isActivityAlive(Activity act) {
        if (act == null || act.isFinishing()) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !act.isDestroyed();
    }

    private String getStringSafely(JsonObject json, String key, String defaultValue) {
        try {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                return defaultValue;
            }
            String value = element.getAsString();
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int getIntSafely(JsonObject json, String key, int defaultValue) {
        try {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                return defaultValue;
            }
            return element.getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean getBooleanSafely(JsonObject json, String key, boolean defaultValue) {
        try {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                return defaultValue;
            }
            return element.getAsBoolean();
        } catch (Exception e) {
            return defaultValue;
        }
    }
}

