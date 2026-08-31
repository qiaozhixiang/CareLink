package com.carelink.app.utils;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

import androidx.core.content.ContextCompat;

import com.carelink.app.R;

/**
 * 统一封装常用页面背景、卡片和标签样式，减少各页面重复写颜色与圆角，降低“无法解析符号/复制改坏导入”类问题出现概率。
 */
public final class UiThemeHelper {
    private UiThemeHelper() {
    }

    public static int color(Context context, int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }

    public static GradientDrawable roundedBg(Context context, int colorRes, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(context, colorRes));
        drawable.setCornerRadius(radius);
        return drawable;
    }

    public static GradientDrawable roundedBgColor(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    public static GradientDrawable statusPill(Context context, String status) {
        int colorRes;
        switch (status) {
            case "临时共享中":
                colorRes = R.color.action_neutral;
                break;
            case "单次共享":
                colorRes = R.color.surface_soft_blue;
                break;
            case "已结束":
                colorRes = R.color.surface_soft_gray;
                break;
            default:
                colorRes = R.color.surface_soft_gray;
                break;
        }
        return roundedBg(context, colorRes, 18);
    }
}
