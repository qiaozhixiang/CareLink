package com.carelink.app.worker;

import android.content.Context;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** WorkManager 调度管理 */
public class WorkScheduler {

    private static final String REMINDER_WORKER_TAG = "reminder_worker";
    private static final String ALERT_DETECT_TAG = "alert_detect_worker";
    private static final String SYNC_TAG = "sync_worker";

    public static void scheduleAll(Context context) {
        scheduleReminderWorker(context);
        scheduleAlertDetect(context);
        scheduleSyncWorker(context);
    }

    public static void scheduleReminderWorker(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ReminderWorker.class, 15, TimeUnit.MINUTES)
                .addTag(REMINDER_WORKER_TAG)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                REMINDER_WORKER_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                request);
    }

    public static void scheduleAlertDetect(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AlertDetectWorker.class, 15, TimeUnit.MINUTES)
                .addTag(ALERT_DETECT_TAG)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                ALERT_DETECT_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                request);
    }

    public static void scheduleSyncWorker(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 1, TimeUnit.HOURS)
                .addTag(SYNC_TAG)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                request);
    }
}
