package com.carelink.app.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 异常警报规则 */
@Entity(tableName = "alert_rule")
public class AlertRuleEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long elderId;
    public String triggerType;  // MEDICINE_TIMEOUT / NO_CHECKIN / CONSECUTIVE_MISS / EMERGENCY
    public int threshold;       // 阈值（分钟数或次数）
    public int level;           // 1=普通 2=重要 3=紧急
    public int enabled;
}
