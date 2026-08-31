package com.carelink.app.domain.usecase;

import com.carelink.app.data.local.entity.AlertEventEntity;
import com.carelink.app.data.local.entity.AlertRuleEntity;
import com.carelink.app.data.local.entity.CheckinRecordEntity;
import java.util.ArrayList;
import java.util.List;

/**
 * 异常检测引擎 - 规则执行器
 */
public class AlertEngine {

    public List<AlertEventEntity> evaluate(long elderId,
                                           List<CheckinRecordEntity> records,
                                           List<AlertRuleEntity> rules) {
        List<AlertEventEntity> result = new ArrayList<>();

        for (AlertRuleEntity rule : rules) {
            if ("MEDICINE_TIMEOUT".equals(rule.triggerType)) {
                if (checkMedicineTimeout(records, rule)) {
                    result.add(buildAlert(elderId, "MEDICINE_TIMEOUT", "服药超时未完成", rule.level));
                }
            } else if ("NO_CHECKIN".equals(rule.triggerType)) {
                if (checkNoCheckin(records, rule)) {
                    result.add(buildAlert(elderId, "NO_CHECKIN", "今日无打卡记录", rule.level));
                }
            } else if ("CONSECUTIVE_MISS".equals(rule.triggerType)) {
                if (checkConsecutiveMiss(records, rule)) {
                    result.add(buildAlert(elderId, "CONSECUTIVE_MISS", "连续多天异常，请关注", rule.level));
                }
            }
        }
        return result;
    }

    private boolean checkMedicineTimeout(List<CheckinRecordEntity> records, AlertRuleEntity rule) {
        long now = System.currentTimeMillis();
        long thresholdMs = rule.threshold * 60L * 1000L;
        // 查找今日服药记录，若无且超过阈值时间则触发
        for (CheckinRecordEntity r : records) {
            if ("MEDICINE".equals(r.type)) {
                long diff = now - r.actualTime;
                if (diff < thresholdMs) return false;
            }
        }
        return true;
    }

    private boolean checkNoCheckin(List<CheckinRecordEntity> records, AlertRuleEntity rule) {
        // 今日无任何打卡
        if (records == null || records.isEmpty()) return true;
        long todayStart = getTodayStartMillis();
        for (CheckinRecordEntity r : records) {
            if (r.actualTime >= todayStart) return false;
        }
        return true;
    }

    private boolean checkConsecutiveMiss(List<CheckinRecordEntity> records, AlertRuleEntity rule) {
        // 连续 N 天无打卡（简化逻辑）
        return false;
    }

    private AlertEventEntity buildAlert(long elderId, String type, String desc, int level) {
        AlertEventEntity entity = new AlertEventEntity();
        entity.elderId = elderId;
        entity.alertType = type;
        entity.description = desc;
        entity.level = level;
        entity.createdAt = System.currentTimeMillis();
        entity.status = "PENDING";
        return entity;
    }

    private long getTodayStartMillis() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
