package com.carelink.app.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 异常事件 */
@Entity(tableName = "alert_event")
public class AlertEventEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long elderId;
    public String alertType;
    public String description;
    public int level;           // 1=普通 2=重要 3=紧急
    public long createdAt;
    public String status;       // PENDING / HANDLED / IGNORED
    public long assignedTo;     // 家属 userId
}
