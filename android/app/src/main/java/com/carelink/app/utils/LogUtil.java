package com.carelink.app.utils;

import android.util.Log;

/** 统一日志工具 */
public class LogUtil {
    private static final String TAG_PREFIX = "CareLink_";
    private static boolean DEBUG = true;

    public static void d(String tag, String msg) {
        if (DEBUG) Log.d(TAG_PREFIX + tag, msg);
    }

    public static void w(String tag, String msg) {
        if (DEBUG) Log.w(TAG_PREFIX + tag, msg);
    }

    public static void e(String tag, String msg) {
        Log.e(TAG_PREFIX + tag, msg);
    }

    public static void e(String tag, String msg, Throwable t) {
        Log.e(TAG_PREFIX + tag, msg, t);
    }
}
