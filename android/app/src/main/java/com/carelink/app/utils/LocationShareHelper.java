package com.carelink.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class LocationShareHelper {
    private static final String PREF_NAME = "location_share";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LNG = "lng";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_TIME = "time";
    private static final String KEY_ENABLED = "enabled";

    private final SharedPreferences prefs;

    public LocationShareHelper(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveLocation(double lat, double lng, String address, String time) {
        prefs.edit()
                .putFloat(KEY_LAT, (float) lat)
                .putFloat(KEY_LNG, (float) lng)
                .putString(KEY_ADDRESS, address)
                .putString(KEY_TIME, time)
                .putBoolean(KEY_ENABLED, true)
                .apply();
    }

    public double getLatitude() {
        return prefs.getFloat(KEY_LAT, 31.2304f);
    }

    public double getLongitude() {
        return prefs.getFloat(KEY_LNG, 121.4737f);
    }

    public String getAddress() {
        return prefs.getString(KEY_ADDRESS, "暂无位置，点击老人端首页更新位置");
    }

    public String getUpdateTime() {
        return prefs.getString(KEY_TIME, "未更新");
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }
}
