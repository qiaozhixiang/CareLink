package com.carelink.app.worker;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorker;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.data.repository.AlertRepository;
import com.carelink.app.data.repository.CheckinRepository;
import com.carelink.app.domain.usecase.AlertEngine;
import com.carelink.app.data.local.entity.AlertEventEntity;
import com.carelink.app.data.local.entity.AlertRuleEntity;
import com.carelink.app.data.local.entity.CheckinRecordEntity;
import com.carelink.app.notification.NotificationHelper;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import java.util.ArrayList;
import java.util.List;

/**
 * 异常检测 Worker - 每 15 分钟运行一次
 */
@HiltWorker
public class AlertDetectWorker extends Worker {

    private final AlertRepository alertRepository;
    private final PreferenceManager preferenceManager;
    private final AlertEngine alertEngine = new AlertEngine();

    @AssistedInject
    public AlertDetectWorker(@Assisted @NonNull Context context,
                              @Assisted @NonNull WorkerParameters params,
                              AlertRepository alertRepository,
                              PreferenceManager preferenceManager) {
        super(context, params);
        this.alertRepository = alertRepository;
        this.preferenceManager = preferenceManager;
    }

    @NonNull
    @Override
    public Result doWork() {
        long elderId = preferenceManager.getElderId();
        if (elderId < 0) return Result.success();

        try {
            List<AlertRuleEntity> rules = alertRepository.getEnabledRules(elderId);
            List<CheckinRecordEntity> records = new ArrayList<>(); // 实际应从 DB 读取今日记录
            List<AlertEventEntity> events = alertEngine.evaluate(elderId, records, rules);

            for (AlertEventEntity event : events) {
                alertRepository.saveAlert(event);
                NotificationHelper.showAlertNotification(getApplicationContext(),
                        event.alertType, event.description, event.level);
            }
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
