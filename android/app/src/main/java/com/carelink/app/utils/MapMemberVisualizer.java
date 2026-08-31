package com.carelink.app.utils;

import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MarkerOptions;

/**
 * 家庭成员地图可视化工具。
 */
public final class MapMemberVisualizer {

    /** 成员数据点。 */
    public static final class MemberPoint {
        public final String memberId;
        public final String displayName;
        public final String role;
        public final LatLng latLng;
        public final String status;
        public final String timeText;
        public final float colorHue;
        public final boolean primary;

        public MemberPoint(String memberId, String displayName, String role, LatLng latLng,
                           String status, String timeText, float colorHue, boolean primary) {
            this.memberId = memberId;
            this.displayName = displayName;
            this.role = role;
            this.latLng = latLng;
            this.status = status;
            this.timeText = timeText;
            this.colorHue = colorHue;
            this.primary = primary;
        }
    }

    /** 老人：蓝色系。 */
    private static final float[] ELDER_HUES = {
            BitmapDescriptorFactory.HUE_AZURE,
            210f
    };

    /** 家属：暖色/冷色区分。 */
    private static final float[] FAMILY_HUES = {
            BitmapDescriptorFactory.HUE_ROSE,
            BitmapDescriptorFactory.HUE_ORANGE,
            BitmapDescriptorFactory.HUE_GREEN,
            BitmapDescriptorFactory.HUE_VIOLET,
            BitmapDescriptorFactory.HUE_CYAN
    };

    private MapMemberVisualizer() {
    }

    public static float resolveColorHue(String role, int index) {
        if ("ELDER".equalsIgnoreCase(role)) {
            return ELDER_HUES[Math.max(0, Math.min(index, ELDER_HUES.length - 1))];
        }
        return FAMILY_HUES[Math.max(0, Math.min(index, FAMILY_HUES.length - 1))];
    }

    public static MarkerOptions createMarkerOptions(MemberPoint point) {
        return createMarkerOptions(point, false);
    }

    public static MarkerOptions createMarkerOptions(MemberPoint point, boolean highlighted) {
        String roleLabel = "ELDER".equalsIgnoreCase(point.role) ? "老人" : "家属";
        String snippet = roleLabel + " · " + safeText(point.status, "状态未知")
                + " · " + safeText(point.timeText, "暂无时间");
        float zIndex = highlighted ? 5f : (point.primary ? 2f : 1f);
        float anchorV = highlighted ? 0.9f : 0.85f;

        return new MarkerOptions()
                .position(point.latLng)
                .title(point.displayName)
                .snippet(snippet)
                .anchor(0.5f, anchorV)
                .icon(BitmapDescriptorFactory.defaultMarker(point.colorHue))
                .zIndex(zIndex);
    }

    public static String buildLegendText() {
        return "颜色说明：老人（蓝/深蓝） · 家属（玫红/橙/绿/紫/青）";
    }

    private static String safeText(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value.trim();
    }
}