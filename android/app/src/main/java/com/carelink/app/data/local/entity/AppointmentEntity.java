package com.carelink.app.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 复诊预约 */
@Entity(tableName = "appointments")
public class AppointmentEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long elderId;
    public String title;
    public String category;       // HOSPITAL / CLINIC / CHECK
    public long startTime;
    public long endTime;
    public String location;
    public String notes;
    public long assignedFamilyId;
    public int status;            // 0=待完成 1=已完成 2=已取消
}
