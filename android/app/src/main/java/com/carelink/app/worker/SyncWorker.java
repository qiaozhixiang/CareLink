package com.carelink.app.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

/**
 * 数据同步 Worker - 将本地离线数据同步到服务器
 */
@HiltWorker
public class SyncWorker extends Worker {

    @AssistedInject
    public SyncWorker(@Assisted @NonNull Context context,
                      @Assisted @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            // TODO: 同步本地未上传的打卡记录、备注等到服务器
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
