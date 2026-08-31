package com.carelink.app.utils;

import androidx.annotation.Nullable;

import com.amap.api.maps.model.LatLng;

import java.util.Locale;

public final class ShareMapDataHelper {
    private static final double DEFAULT_LATITUDE = 31.2304;
    private static final double DEFAULT_LONGITUDE = 121.4737;

    private ShareMapDataHelper() {
    }

    public static LatLng getLatLngForLocationLabel(@Nullable String label) {
        if (label == null || label.trim().isEmpty()) {
            return createLatLng(DEFAULT_LATITUDE, DEFAULT_LONGITUDE);
        }

        String value = label.trim();
        LatLng parsed = parseLatLng(value);
        if (parsed != null) {
            return parsed;
        }

        if (value.contains("家中")) {
            return createLatLng(DEFAULT_LATITUDE, DEFAULT_LONGITUDE);
        }
        if (value.contains("社区") || value.contains("活动中心")) {
            return createLatLng(31.2281, 121.4809);
        }
        if (value.contains("医院") || value.contains("门诊")) {
            return createLatLng(31.2206, 121.4587);
        }
        return createLatLng(DEFAULT_LATITUDE, DEFAULT_LONGITUDE);
    }

    public static String formatLatLng(@Nullable LatLng latLng) {
        if (latLng == null) {
            return formatLatLng(DEFAULT_LATITUDE, DEFAULT_LONGITUDE);
        }
        return formatLatLng(latLng.latitude, latLng.longitude);
    }

    public static String formatLatLng(double latitude, double longitude) {
        return String.format(Locale.US, "%.6f,%.6f", latitude, longitude);
    }

    public static LatLng getStoredOrFallback(double latitude, double longitude, @Nullable String label) {
        if (isValidCoordinate(latitude, longitude)) {
            return createLatLng(latitude, longitude);
        }
        return getLatLngForLocationLabel(label);
    }

    public static boolean isValidCoordinate(double latitude, double longitude) {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    public static LatLng offsetLatLng(@Nullable LatLng source, double latitudeOffset, double longitudeOffset) {
        LatLng base = source == null ? createLatLng(DEFAULT_LATITUDE, DEFAULT_LONGITUDE) : source;
        double latitude = clamp(base.latitude + latitudeOffset, -90, 90);
        double longitude = clamp(base.longitude + longitudeOffset, -180, 180);
        return createLatLng(latitude, longitude);
    }

    private static LatLng createLatLng(double latitude, double longitude) {

        return new LatLng(latitude, longitude);
    }

    @Nullable
    private static LatLng parseLatLng(String value) {
        if (!value.contains(",")) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            double latitude = Double.parseDouble(parts[0].trim());
            double longitude = Double.parseDouble(parts[1].trim());
            return isValidCoordinate(latitude, longitude) ? createLatLng(latitude, longitude) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

