package com.carelink.app.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 责任轮值排班 */
@Entity(tableName = "shift_assignments")
public class ShiftAssignmentEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long elderId;
    public long familyUserId;
    public String dutyDate;   // yyyy-MM-dd
    public String dutyType;   // DAY / NIGHT / FULL
    public int status;        // 0=计划 1=进行中 2=已完成 3=换班申请中
}
