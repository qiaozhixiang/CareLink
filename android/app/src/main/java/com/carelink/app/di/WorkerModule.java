package com.carelink.app.di;

import com.carelink.app.worker.AlertDetectWorker;
import com.carelink.app.worker.ReminderWorker;
import com.carelink.app.worker.SyncWorker;

import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

/**
 * WorkerModule - Hilt 注入 Worker 工厂
 * 实际 Worker 绑定由 @HiltWorker + HiltWorkerFactory 自动完成
 * 此模块用于补充 Worker 相关的依赖配置说明
 */
@Module
@InstallIn(SingletonComponent.class)
public class WorkerModule {
    // Worker 绑定通过 @HiltWorker 注解 + HiltWorkerFactory 自动完成
    // 涉及的 Worker：ReminderWorker / AlertDetectWorker / SyncWorker
}
