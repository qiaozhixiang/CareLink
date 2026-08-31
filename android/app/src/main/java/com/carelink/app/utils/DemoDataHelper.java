package com.carelink.app.utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 本地演示数据工具类 */
public class DemoDataHelper {

    public static class CheckinTask {
        public String title;
        public String time;
        public boolean done;
        public CheckinTask(String title, String time, boolean done) {
            this.title = title;
            this.time = time;
            this.done = done;
        }
    }

    public static class ScheduleItem {
        public String title;
        public String time;
        public String note;
        public String date;
        public String creator;
        public String category;
        public boolean done;
        public int priority;

        public ScheduleItem(String title, String time, String note, String date, String creator, String category, boolean done, int priority) {
            this.title = title;
            this.time = time;
            this.note = note;
            this.date = date;
            this.creator = creator;
            this.category = category;
            this.done = done;
            this.priority = priority;
        }
    }

    public static class AlertItemLocal {
        public String level;
        public String title;
        public String time;
        public AlertItemLocal(String level, String title, String time) {
            this.level = level;
            this.title = title;
            this.time = time;
        }
    }

    private static final List<ScheduleItem> sharedSchedules = new ArrayList<>();

    static {
        if (sharedSchedules.isEmpty()) {
            sharedSchedules.add(new ScheduleItem("社区卫生站复诊", "09:30", "带上医保卡与既往病历", "03-28", "家属", "健康", false, 3));
            sharedSchedules.add(new ScheduleItem("家庭视频通话", "19:00", "和孩子视频联系", getTodayShortDate(), "家属", "陪伴", false, 2));
            sharedSchedules.add(new ScheduleItem("服药提醒回顾", "20:30", "检查今日是否全部完成", getTodayShortDate(), "老人", "用药", false, 3));
        }
    }

    public static List<CheckinTask> getTodayTasks() {
        List<CheckinTask> list = new ArrayList<>();
        list.add(new CheckinTask("早餐后服药", "08:00", true));
        list.add(new CheckinTask("上午喝水", "10:00", true));
        list.add(new CheckinTask("午饭后散步", "15:00", false));
        list.add(new CheckinTask("晚间测血压", "20:00", false));
        return list;
    }

    public static List<ScheduleItem> getSchedules() {
        List<ScheduleItem> list = new ArrayList<>(sharedSchedules);
        Collections.sort(list, Comparator.comparing((ScheduleItem s) -> s.date).thenComparing(s -> s.time));
        return list;
    }

    public static void addSchedule(String title, String date, String time, String note, String creator) {
        sharedSchedules.add(0, new ScheduleItem(
                title,
                time,
                note == null || note.isEmpty() ? "无备注" : note,
                date == null || date.isEmpty() ? getTodayShortDate() : date,
                creator == null || creator.isEmpty() ? "系统" : creator,
                "日程",
                false,
                2
        ));
    }

    public static void markScheduleDone(String date, String title) {
        for (ScheduleItem item : sharedSchedules) {
            if (item.date.equals(date) && item.title.equals(title)) {
                item.done = true;
            }
        }
    }

    public static List<AlertItemLocal> getAlerts() {
        List<AlertItemLocal> list = new ArrayList<>();
        list.add(new AlertItemLocal("提醒", "下午散步任务未完成", "15:00"));
        list.add(new AlertItemLocal("关注", "今日饮水记录偏少", "11:20"));
        return list;
    }

    public static List<String> getNotes() {
        List<String> list = new ArrayList<>();
        list.add("今天精神状态不错，午睡约40分钟。\n—— 家属记录");
        list.add("晚饭建议清淡，少盐。\n—— 健康建议");
        list.add("记得周六上午复诊。\n—— 系统提醒");
        return list;
    }

    public static class SharedLocationSnapshot {
        public String owner;
        public String sessionId;
        public String location;
        public String time;
        public double latitude;
        public double longitude;
        public boolean visibleToBoth;

        public SharedLocationSnapshot(String owner, String sessionId, String location, String time,
                                      double latitude, double longitude, boolean visibleToBoth) {
            this.owner = owner;
            this.sessionId = sessionId;
            this.location = location;
            this.time = time;
            this.latitude = latitude;
            this.longitude = longitude;
            this.visibleToBoth = visibleToBoth;
        }
    }

    public static SharedLocationSnapshot getSharedLocationSnapshot(String owner, String sessionId,
                                                                   String location, String time,
                                                                   double latitude, double longitude,
                                                                   boolean visibleToBoth) {
        return new SharedLocationSnapshot(owner, sessionId, location, time, latitude, longitude, visibleToBoth);
    }

    public static String getGreeting() {
        int hour = Integer.parseInt(new SimpleDateFormat("HH", Locale.CHINA).format(new Date()));
        if (hour < 11) return "早上好，记得按时服药";
        if (hour < 14) return "中午好，注意饮食清淡";
        if (hour < 18) return "下午好，适当活动一下";
        return "晚上好，准备休息吧";
    }

    public static String getTodayDate() {
        return new SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA).format(new Date());
    }

    public static String getTodayShortDate() {
        return new SimpleDateFormat("MM-dd", Locale.CHINA).format(new Date());
    }
}
