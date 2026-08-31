package com.carelink.app.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 打卡记录 */
@Entity(tableName = "checkin_record")
public class CheckinRecordEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long elderId;
    public long taskId;
    public String type;
    public long actualTime;
    public String value;     // 具体数值，如血压 "120/80"
    public String note;
    public String source;    // USER / AUTO / FAMILY
}
