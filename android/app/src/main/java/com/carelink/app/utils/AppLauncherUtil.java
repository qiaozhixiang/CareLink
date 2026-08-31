package com.carelink.app.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Toast;

/**
 * APP 启动器工具类：优先打开手机内已安装的真实应用。
 */
public final class AppLauncherUtil {

    private AppLauncherUtil() {
    }

    public static void launchDouyin(Context context) {
        launchApp(context, new String[]{
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.lite"
        }, "抖音");
    }

    public static void launchQQ(Context context) {
        launchApp(context, new String[]{
                "com.tencent.mobileqq",
                "com.tencent.tim"
        }, "QQ");
    }

    public static void launchWeChat(Context context) {
        launchApp(context, new String[]{
                "com.tencent.mm"
        }, "微信");
    }

    public static void launchPhone(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:"));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "无法打开电话应用", Toast.LENGTH_SHORT).show();
        }
    }

    public static void launchSMS(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:"));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "无法打开短信应用", Toast.LENGTH_SHORT).show();
        }
    }

    public static void launchKuaishou(Context context) {
        launchApp(context, new String[]{
                "com.smile.gifmaker",
                "com.kuaishou.nebula"
        }, "快手");
    }

    public static void launchToutiao(Context context) {
        launchApp(context, new String[]{
                "com.ss.android.article.news",
                "com.ss.android.article.lite"
        }, "今日头条");
    }

    /** 启动系统相机 */
    public static void launchCamera(Context context) {
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // 尝试通用相机 intent
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.setType("image/*");
                fallback.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Exception ex) {
                Toast.makeText(context, "无法打开相机应用", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "无法打开相机", Toast.LENGTH_SHORT).show();
        }
    }

    /** 启动地图应用（优先高德、百度，其次系统浏览器地图） */
    public static void launchMap(Context context) {
        launchApp(context, new String[]{
                "com.autonavi.minimap",        // 高德地图
                "com.baidu.BaiduMap",          // 百度地图
                "com.tencent.map",             // 腾讯地图
                "com.google.android.apps.maps" // 谷歌地图
        }, "地图");
    }

    /** 启动天气应用 */
    public static void launchWeather(Context context) {
        launchApp(context, new String[]{
                "com.miui.weather2",           // 小米天气
                "com.huawei.android.totemweather", // 华为天气
                "com.oppo.weather",            // OPPO 天气
                "com.vivo.weather",            // vivo 天气
                "com.samsung.android.weather.bgapp",  // 三星天气
                "com.color.weather"            // 一加天气
        }, "天气");
    }

    /** 启动系统计算器 */
    public static void launchCalculator(Context context) {
        launchApp(context, new String[]{
                "com.miui.calculator",          // 小米计算器
                "com.huawei.calculator",        // 华为计算器
                "com.oppo.calculator",          // OPPO 计算器
                "com.vivo.calculator",          // vivo 计算器
                "com.sec.android.app.popupcalculator", // 三星计算器
                "com.android.calculator2"       // AOSP 计算器
        }, "计算器");
    }

    /** 打开系统设置 */
    public static void launchSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "无法打开系统设置", Toast.LENGTH_SHORT).show();
        }
    }

    private static void launchApp(Context context, String[] packageNames, String appName) {
        PackageManager pm = context.getPackageManager();
        try {
            for (String packageName : packageNames) {
                Intent intent = pm.getLaunchIntentForPackage(packageName);
                if (intent == null) {
                    intent = pm.getLeanbackLaunchIntentForPackage(packageName);
                }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    context.startActivity(intent);
                    return;
                }
            }
            Toast.makeText(context, "未安装" + appName + "，请先在手机中安装后使用", Toast.LENGTH_SHORT).show();
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "无法打开" + appName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "启动" + appName + "失败", Toast.LENGTH_SHORT).show();
        }
    }
}


