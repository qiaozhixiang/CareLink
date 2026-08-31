package com.carelink.app.domain.model;

import java.util.List;

/** 今日打卡任务展示模型 */
public class TodayTaskItem {
    public long taskId;
    public String type;
    public String title;
    public long plannedTime;
    public boolean isDone;
    public String doneValue;
    public String doneTime;

    public TodayTaskItem(long taskId, String type, String title, long plannedTime,
                          boolean isDone, String doneValue, String doneTime) {
        this.taskId = taskId;
        this.type = type;
        this.title = title;
        this.plannedTime = plannedTime;
        this.isDone = isDone;
        this.doneValue = doneValue;
        this.doneTime = doneTime;
    }
}
