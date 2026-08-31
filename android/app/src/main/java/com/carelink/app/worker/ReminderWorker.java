package com.carelink.app.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.carelink.app.notification.NotificationHelper;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

/**
 * 提醒 Worker - 周期执行，检查并触发本地提醒通知
 */
@HiltWorker
public class ReminderWorker extends Worker {

    @AssistedInject
    public ReminderWorker(@Assisted @NonNull Context context,
                          @Assisted @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            // TODO: 从数据库读取今日未完成任务，触发到点提醒
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
