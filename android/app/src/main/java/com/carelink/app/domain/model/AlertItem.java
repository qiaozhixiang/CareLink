package com.carelink.app.domain.model;

/** 异常警报展示模型 */
public class AlertItem {
    public long id;
    public String alertType;
    public String description;
    public int level;           // 1=普通 2=重要 3=紧急
    public String status;
    public long createdAt;
    public String levelLabel;
    public int levelColor;

    public AlertItem(long id, String alertType, String description,
                     int level, String status, long createdAt) {
        this.id = id;
        this.alertType = alertType;
        this.description = description;
        this.level = level;
        this.status = status;
        this.createdAt = createdAt;
    }
}
