package com.carelink.app.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/** 常用工具方法 */
public class AppUtils {

    /**
     * 获取应用版本名称
     */
    public static String getAppVersionName(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0.0";
        }
    }

    /**
     * 获取应用版本号
     */
    public static int getAppVersionCode(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 1;
        }
    }

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    public static boolean isValidPhone(String phone) {
        return !TextUtils.isEmpty(phone) && PHONE_PATTERN.matcher(phone).matches();
    }

    public static String formatTime(long timestamp, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public static String formatDateTime(long timestamp) {
        return formatTime(timestamp, "yyyy-MM-dd HH:mm");
    }

    public static String formatDate(long timestamp) {
        return formatTime(timestamp, "yyyy-MM-dd");
    }

    public static String formatTimeOnly(long timestamp) {
        return formatTime(timestamp, "HH:mm");
    }

    public static String getAlertLevelLabel(int level) {
        switch (level) {
            case 1: return "普通";
            case 2: return "重要";
            case 3: return "紧急";
            default: return "未知";
        }
    }

    public static int getAlertLevelColor(int level) {
        switch (level) {
            case 1: return 0xFF4CAF50; // 绿色
            case 2: return 0xFFFF9800; // 橙色
            case 3: return 0xFFF44336; // 红色
            default: return 0xFF9E9E9E; // 灰色
        }
    }
}
