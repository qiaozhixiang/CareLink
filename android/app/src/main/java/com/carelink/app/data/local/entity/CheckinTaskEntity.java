package com.carelink.app.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 打卡任务模板 */
@Entity(tableName = "checkin_task")
public class CheckinTaskEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long elderId;
    public String type;        // MEDICINE / WATER / WALK / BLOOD_PRESSURE / BLOOD_SUGAR / SLEEP
    public String title;
    public long plannedTime;   // 计划时间戳（毫秒）
    public String repeatRule;  // DAILY / WEEKLY / NONE
    public int enabled;        // 1=启用 0=禁用
}
