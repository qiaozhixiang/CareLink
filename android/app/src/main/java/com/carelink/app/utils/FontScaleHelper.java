package com.carelink.app.utils;

import android.content.Context;
import com.carelink.app.data.local.pref.PreferenceManager;

/**
 * 全局字体辅助类
 * 统一管理老人端所有页面的可见文字字号，后续新页面也应通过这里取值。
 */
public class FontScaleHelper {

    public static int getBaseFont(Context context) {
        return new PreferenceManager(context).getFontSize();
    }

    public static int title(Context context) {
        return getBaseFont(context) + 6;
    }

    public static int sectionTitle(Context context) {
        return getBaseFont(context) + 3;
    }

    public static int body(Context context) {
        return getBaseFont(context);
    }

    public static int secondary(Context context) {
        return Math.max(14, getBaseFont(context) - 2);
    }

    public static int small(Context context) {
        return Math.max(12, getBaseFont(context) - 4);
    }
}
