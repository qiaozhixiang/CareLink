package com.carelink.app.utils;

import com.amap.api.maps.model.LatLng;
import com.carelink.app.data.local.pref.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 家庭地图成员本地演示数据仓库。
 */
public final class FamilyMapMemberRepository {

    private FamilyMapMemberRepository() {
    }

    public static List<MapMemberVisualizer.MemberPoint> buildMembers(PreferenceManager preferenceManager) {
        String locationLabel = preferenceManager.getShareLastLocation();
        String timeText = preferenceManager.getShareLastTime();
        String shareStatus = preferenceManager.getShareStatus();
        String elderName = preferenceManager.getNickname();

        LatLng primaryPoint = ShareMapDataHelper.getStoredOrFallback(
                preferenceManager.getShareLatitude(),
                preferenceManager.getShareLongitude(),
                locationLabel);

        List<MapMemberVisualizer.MemberPoint> members = new ArrayList<>();
        members.add(new MapMemberVisualizer.MemberPoint(
                "elder_1",
                safeName(elderName, "老人1"),
                "ELDER",
                primaryPoint,
                safeText(shareStatus, "共享中"),
                safeText(timeText, "刚刚更新"),
                MapMemberVisualizer.resolveColorHue("ELDER", 0),
                true
        ));

        members.add(new MapMemberVisualizer.MemberPoint(
                "elder_2",
                "老人2",
                "ELDER",
                ShareMapDataHelper.offsetLatLng(primaryPoint, 0.00125, -0.0010),
                "预留成员",
                "待接入实时数据",
                MapMemberVisualizer.resolveColorHue("ELDER", 1),
                false
        ));

        for (int i = 0; i < 5; i++) {
            double latitudeOffset = 0.0008 * Math.cos((i + 1) * 0.82d);
            double longitudeOffset = 0.00115 * Math.sin((i + 1) * 0.82d);
            boolean nearPrimary = i == 0;
            members.add(new MapMemberVisualizer.MemberPoint(
                    "family_" + (i + 1),
                    String.format(Locale.CHINA, "家属%d", i + 1),
                    "FAMILY",
                    ShareMapDataHelper.offsetLatLng(primaryPoint, latitudeOffset, longitudeOffset),
                    nearPrimary ? "附近守护" : "预留成员",
                    nearPrimary ? safeText(timeText, "刚刚更新") : "待接入实时数据",
                    MapMemberVisualizer.resolveColorHue("FAMILY", i),
                    false
            ));
        }
        return members;
    }

    private static String safeName(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}