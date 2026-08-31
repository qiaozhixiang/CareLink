package com.carelink.app;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

import com.carelink.app.worker.WorkScheduler;

import javax.inject.Inject;

import androidx.hilt.work.HiltWorkerFactory;
import dagger.hilt.android.HiltAndroidApp;

/**
 * Application 类，使用 Hilt 注入
 * 实现 Configuration.Provider 以支持 @HiltWorker 注解的 Worker 注入
 */
@HiltAndroidApp
public class CareLinkApplication extends Application implements Configuration.Provider {

    private static final String TAG = "CareLinkApplication";

    @Inject
    HiltWorkerFactory workerFactory;

    @Override
    public void onCreate() {
        super.onCreate();
        // 全局异常处理器必须最先设置，这样后续初始化崩溃也能正常退出
        initGlobalExceptionHandler();
        initAmapPrivacy();
        initBackgroundWork();
    }

    /**
     * 提供 WorkManager 配置，使用 HiltWorkerFactory 替代默认工厂
     * 这样 @HiltWorker 注解的 Worker 才能正确被 Hilt 创建和注入
     */
    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(Log.INFO)
                .build();
    }

    /**
     * 高德隐私合规初始化 — 用反射避免类加载时强依赖高德 SDK
     * 如果高德 SDK SO 库缺失或版本不匹配，不会影响 Application 启动
     */
    private void initAmapPrivacy() {
        try {
            Class<?> clazz = Class.forName("com.amap.api.location.AMapLocationClient");
            java.lang.reflect.Method showMethod = clazz.getMethod("updatePrivacyShow",
                    android.content.Context.class, boolean.class, boolean.class);
            java.lang.reflect.Method agreeMethod = clazz.getMethod("updatePrivacyAgree",
                    android.content.Context.class, boolean.class);
            showMethod.invoke(null, this, true, true);
            agreeMethod.invoke(null, this, true);
            Log.d(TAG, "高德隐私初始化成功");
        } catch (Throwable t) {
            Log.w(TAG, "高德隐私初始化失败（不影响应用启动）: " + t.getMessage());
        }
    }

    private void initBackgroundWork() {
        try {
            WorkScheduler.scheduleAll(this);
        } catch (Throwable t) {
            Log.w(TAG, "后台任务初始化失败（不影响应用启动）: " + t.getMessage());
        }
    }

    /**
     * 全局异常兜底 — 记录日志后转发给原始处理器
     * 修复：之前覆盖了默认处理器但不转发不退出，导致崩溃后应用卡死白屏
     */
    private void initGlobalExceptionHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                Log.e(TAG, "☠️ 全局未捕获异常 thread=" + thread.getName(), throwable);
                Log.e(TAG, "☠️ 异常类型: " + throwable.getClass().getName());
                Log.e(TAG, "☠️ 异常信息: " + throwable.getMessage());
            } catch (Throwable ignored) {
                // 日志记录本身不能再抛异常
            }
            // 转发给原始处理器（系统默认会弹出"应用已停止"并退出进程）
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                // 没有原始处理器时手动退出，避免卡死白屏
                System.exit(1);
            }
        });
    }
}

