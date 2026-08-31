package com.carelink.app.data.local.pref;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.carelink.app.ui.auth.LoginActivity;

/** SharedPreferences 封装管理类 */
public class PreferenceManager {

    private static final String PREF_NAME = "care_pref";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_LOCAL_PASSWORD = "local_password";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_ID_STR = "user_id_str";
    private static final String KEY_ROLE = "role";
    private static final String KEY_ROLE_SELECT_TIME = "role_select_time";
    private static final String KEY_FAMILY_ID = "family_id";
    private static final String KEY_FAMILY_NAME = "family_name";
    private static final String KEY_INVITE_CODE = "invite_code";
    private static final String KEY_ELDER_ID = "elder_id";
    private static final String KEY_BIND_ELDER_COUNT = "bind_elder_count";
    private static final String KEY_BIND_FAMILY_COUNT = "bind_family_count";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_AVATAR_URL = "avatar_url";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_EMAIL = "email";

    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_VOICE_ENABLED = "voice_enabled";
    private static final String KEY_EMERGENCY_CONTACT = "emergency_contact";
    private static final String KEY_REMINDER_ENABLED = "reminder_enabled";
    private static final String KEY_DEFAULT_CALENDAR_VIEW = "default_calendar_view";
    private static final String KEY_AUDIO_AUTOPLAY = "audio_autoplay";
    private static final String KEY_SHARE_STATUS = "share_status";
    private static final String KEY_SHARE_LAST_LOCATION = "share_last_location";
    private static final String KEY_SHARE_LAST_TIME = "share_last_time";
    private static final String KEY_SHARE_END_TIME = "share_end_time";
    private static final String KEY_SHARE_LATITUDE = "share_latitude";
    private static final String KEY_SHARE_LONGITUDE = "share_longitude";
    private static final String KEY_SHARED_LOCATION_OWNER = "shared_location_owner";
    private static final String KEY_SHARED_SESSION_ID = "shared_session_id";
    private static final String KEY_SHARED_LOCATION_VISIBLE_TO_BOTH = "shared_location_visible_to_both";
    private static final String KEY_REALTIME_LOCATION_ENABLED = "realtime_location_enabled";
    private static final String KEY_HEALTH_HEART_RATE = "health_heart_rate";
    private static final String KEY_HEALTH_BLOOD_OXYGEN = "health_blood_oxygen";
    private static final String KEY_HEALTH_SYSTOLIC = "health_systolic";
    private static final String KEY_HEALTH_DIASTOLIC = "health_diastolic";
    private static final String KEY_HEALTH_STEPS = "health_steps";
    private static final String KEY_HEALTH_UPDATED_AT = "health_updated_at";
    private static final String KEY_FALL_DETECTION_ENABLED = "fall_detection_enabled";
    private static final String KEY_ELDER_HOME_APPS = "elder_home_apps";
    private static final String KEY_COMPANION_REMINDERS = "companion_reminders";
    private static final String KEY_ELDER_PENDING_REMINDERS = "elder_pending_reminders";
    private static final String KEY_AI_CHAT_HISTORY_ELDER = "ai_chat_history_elder";
    private static final String KEY_AI_CHAT_HISTORY_FAMILY = "ai_chat_history_family";



    private final SharedPreferences sp;
    public PreferenceManager(Context context) { sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE); }

    public void saveToken(String token) { sp.edit().putString(KEY_TOKEN, token == null ? "" : token).apply(); }
    public String getToken() { return safeString(KEY_TOKEN, ""); }
    public void saveLocalPassword(String password) { sp.edit().putString(KEY_LOCAL_PASSWORD, password == null ? "" : password).apply(); }
    public String getLocalPassword() { return safeString(KEY_LOCAL_PASSWORD, ""); }
    public boolean hasLocalPassword() { return !getLocalPassword().isEmpty(); }
    public void saveUserId(long userId) { sp.edit().putLong(KEY_USER_ID, userId).apply(); }

    public long getUserId() { return sp.getLong(KEY_USER_ID, -1); }
    public void saveUserIdStr(String userId) { if (userId != null) sp.edit().putString(KEY_USER_ID_STR, userId).apply(); }
    public String getUserIdStr() { return sp.getString(KEY_USER_ID_STR, ""); }
    public void saveRole(String role) { sp.edit().putString(KEY_ROLE, role).apply(); }
    public String getRole() { return sp.getString(KEY_ROLE, ""); }
    public void saveRoleSelectTime(long timestamp) { sp.edit().putLong(KEY_ROLE_SELECT_TIME, timestamp).apply(); }
    public long getRoleSelectTime() { return sp.getLong(KEY_ROLE_SELECT_TIME, 0); }
    public boolean canSwitchRole() { return true; }
    public long getDaysUntilCanSwitch() { return 0; }
    public void saveFamilyId(long familyId) { sp.edit().putLong(KEY_FAMILY_ID, familyId).apply(); }
    public void saveFamilyId(Long familyId) {
        SharedPreferences.Editor editor = sp.edit();
        if (familyId != null) {
            editor.putLong(KEY_FAMILY_ID, familyId);
        } else {
            editor.remove(KEY_FAMILY_ID);
        }
        editor.apply();
    }

    public long getFamilyId() { return sp.getLong(KEY_FAMILY_ID, -1); }
    public void saveElderId(long elderId) { sp.edit().putLong(KEY_ELDER_ID, elderId).apply(); }
    public long getElderId() { return sp.getLong(KEY_ELDER_ID, -1); }
    public void saveFamilyName(String name) { sp.edit().putString(KEY_FAMILY_NAME, name).apply(); }
    public String getFamilyName() { return sp.getString(KEY_FAMILY_NAME, ""); }
    public void saveInviteCode(String code) { sp.edit().putString(KEY_INVITE_CODE, code).apply(); }
    public String getInviteCode() { return sp.getString(KEY_INVITE_CODE, ""); }
    public void saveBindElderCount(int count) { sp.edit().putInt(KEY_BIND_ELDER_COUNT, count).apply(); }
    public int getBindElderCount() { return sp.getInt(KEY_BIND_ELDER_COUNT, 0); }
    public boolean canBindMoreElder() { return getBindElderCount() < 2; }
    public void saveBindFamilyCount(int count) { sp.edit().putInt(KEY_BIND_FAMILY_COUNT, count).apply(); }
    public int getBindFamilyCount() { return sp.getInt(KEY_BIND_FAMILY_COUNT, 0); }
    public boolean canBindMoreFamily() { return getBindFamilyCount() < 5; }
    public void saveNickname(String nickname) { sp.edit().putString(KEY_NICKNAME, nickname).apply(); }
    public String getNickname() { return sp.getString(KEY_NICKNAME, ""); }
    public void saveAvatarUrl(String url) { sp.edit().putString(KEY_AVATAR_URL, url).apply(); }
    public String getAvatarUrl() { return sp.getString(KEY_AVATAR_URL, ""); }
    public void savePhone(String phone) { sp.edit().putString(KEY_PHONE, phone).apply(); }
    public String getPhone() { return sp.getString(KEY_PHONE, ""); }
    public void saveEmail(String email) { sp.edit().putString(KEY_EMAIL, email).apply(); }
    public String getEmail() { return sp.getString(KEY_EMAIL, ""); }
    public void saveFontSize(int size) { sp.edit().putInt(KEY_FONT_SIZE, size).apply(); }
    public int getFontSize() { return sp.getInt(KEY_FONT_SIZE, 18); }
    public void setVoiceEnabled(boolean enabled) { sp.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply(); }
    public boolean isVoiceEnabled() { return sp.getBoolean(KEY_VOICE_ENABLED, true); }
    public void saveEmergencyContact(String contact) { sp.edit().putString(KEY_EMERGENCY_CONTACT, contact).apply(); }
    public String getEmergencyContact() { return sp.getString(KEY_EMERGENCY_CONTACT, "120"); }
    public void setReminderEnabled(boolean enabled) { sp.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply(); }
    public boolean isReminderEnabled() { return sp.getBoolean(KEY_REMINDER_ENABLED, true); }
    public void saveDefaultCalendarView(String value) { sp.edit().putString(KEY_DEFAULT_CALENDAR_VIEW, value).apply(); }
    public String getDefaultCalendarView() { return sp.getString(KEY_DEFAULT_CALENDAR_VIEW, "月视图"); }
    public void setAudioAutoplay(boolean enabled) { sp.edit().putBoolean(KEY_AUDIO_AUTOPLAY, enabled).apply(); }
    public boolean isAudioAutoplay() { return sp.getBoolean(KEY_AUDIO_AUTOPLAY, false); }
    public void saveShareStatus(String value) { sp.edit().putString(KEY_SHARE_STATUS, value).apply(); }
    public String getShareStatus() { return sp.getString(KEY_SHARE_STATUS, "未共享"); }
    public void saveShareLastLocation(String value) { sp.edit().putString(KEY_SHARE_LAST_LOCATION, value).apply(); }
    public String getShareLastLocation() { return sp.getString(KEY_SHARE_LAST_LOCATION, "等待主动共享"); }
    public void saveShareLastTime(String value) { sp.edit().putString(KEY_SHARE_LAST_TIME, value).apply(); }
    public String getShareLastTime() { return safeString(KEY_SHARE_LAST_TIME, "暂无"); }
    public void saveShareEndTime(String value) { sp.edit().putString(KEY_SHARE_END_TIME, value).apply(); }
    public String getShareEndTime() { return safeString(KEY_SHARE_END_TIME, "未设置"); }
    public void saveShareLatitude(double value) { sp.edit().putString(KEY_SHARE_LATITUDE, String.valueOf(value)).apply(); }
    public double getShareLatitude() { return safeDouble(KEY_SHARE_LATITUDE, 31.2304); }
    public void saveShareLongitude(double value) { sp.edit().putString(KEY_SHARE_LONGITUDE, String.valueOf(value)).apply(); }
    public double getShareLongitude() { return safeDouble(KEY_SHARE_LONGITUDE, 121.4737); }
    public void saveSharedLocationOwner(String value) { sp.edit().putString(KEY_SHARED_LOCATION_OWNER, value).apply(); }
    public String getSharedLocationOwner() { return safeString(KEY_SHARED_LOCATION_OWNER, "ELDER"); }
    public void saveSharedSessionId(String value) { sp.edit().putString(KEY_SHARED_SESSION_ID, value).apply(); }
    public String getSharedSessionId() { return safeString(KEY_SHARED_SESSION_ID, "LOCAL-DEMO-SESSION"); }
    public void setSharedLocationVisibleToBoth(boolean value) { sp.edit().putBoolean(KEY_SHARED_LOCATION_VISIBLE_TO_BOTH, value).apply(); }
    public boolean isSharedLocationVisibleToBoth() { return sp.getBoolean(KEY_SHARED_LOCATION_VISIBLE_TO_BOTH, true); }
    public void setRealtimeLocationEnabled(boolean value) { sp.edit().putBoolean(KEY_REALTIME_LOCATION_ENABLED, value).apply(); }
    public boolean isRealtimeLocationEnabled() { return sp.getBoolean(KEY_REALTIME_LOCATION_ENABLED, false); }
    public void saveHealthHeartRate(int value) { sp.edit().putInt(KEY_HEALTH_HEART_RATE, value).apply(); }
    public int getHealthHeartRate() { return sp.getInt(KEY_HEALTH_HEART_RATE, 0); }
    public void saveHealthBloodOxygen(int value) { sp.edit().putInt(KEY_HEALTH_BLOOD_OXYGEN, value).apply(); }
    public int getHealthBloodOxygen() { return sp.getInt(KEY_HEALTH_BLOOD_OXYGEN, 0); }
    public void saveHealthSystolic(int value) { sp.edit().putInt(KEY_HEALTH_SYSTOLIC, value).apply(); }
    public int getHealthSystolic() { return sp.getInt(KEY_HEALTH_SYSTOLIC, 0); }
    public void saveHealthDiastolic(int value) { sp.edit().putInt(KEY_HEALTH_DIASTOLIC, value).apply(); }
    public int getHealthDiastolic() { return sp.getInt(KEY_HEALTH_DIASTOLIC, 0); }
    public void saveHealthSteps(int value) { sp.edit().putInt(KEY_HEALTH_STEPS, value).apply(); }
    public int getHealthSteps() { return sp.getInt(KEY_HEALTH_STEPS, 0); }
    public void saveHealthUpdatedAt(String value) { sp.edit().putString(KEY_HEALTH_UPDATED_AT, value == null ? "" : value).apply(); }
    public String getHealthUpdatedAt() { return safeString(KEY_HEALTH_UPDATED_AT, "暂无"); }
    public void setFallDetectionEnabled(boolean enabled) { sp.edit().putBoolean(KEY_FALL_DETECTION_ENABLED, enabled).apply(); }
    public boolean isFallDetectionEnabled() { return sp.getBoolean(KEY_FALL_DETECTION_ENABLED, false); }
    public void saveElderHomeApps(String value) { sp.edit().putString(KEY_ELDER_HOME_APPS, value == null ? "" : value).apply(); }
    public String getElderHomeApps() { return safeString(KEY_ELDER_HOME_APPS, ""); }
    public void saveCompanionReminders(String json) { sp.edit().putString(KEY_COMPANION_REMINDERS, json == null ? "[]" : json).apply(); }
    public String getCompanionReminders() { return safeString(KEY_COMPANION_REMINDERS, "[]"); }
    public void saveElderPendingReminders(String json) { sp.edit().putString(KEY_ELDER_PENDING_REMINDERS, json == null ? "[]" : json).apply(); }
    public String getElderPendingReminders() { return safeString(KEY_ELDER_PENDING_REMINDERS, "[]"); }
    public void saveAiChatHistory(String roleScope, String json) {
        sp.edit().putString(resolveAiChatHistoryKey(roleScope), json == null ? "[]" : json).apply();
    }
    public String getAiChatHistory(String roleScope) {
        return safeString(resolveAiChatHistoryKey(roleScope), "[]");
    }
    public void clearAiChatHistory(String roleScope) {
        sp.edit().remove(resolveAiChatHistoryKey(roleScope)).apply();
    }

    public boolean isLoggedIn() { return !getToken().isEmpty() || (hasLocalPassword() && !getEmail().isEmpty()); }
    public void clearFamilyState() {
        sp.edit()
                .remove(KEY_FAMILY_ID)
                .remove(KEY_FAMILY_NAME)
                .remove(KEY_INVITE_CODE)
                .remove(KEY_ELDER_ID)
                .apply();
    }
    public void clearRoleState() { sp.edit().remove(KEY_ROLE).remove(KEY_ROLE_SELECT_TIME).apply(); }
    public void clear() { sp.edit().clear().apply(); }

    public void clearSession() {
        sp.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_LOCAL_PASSWORD)
                .remove(KEY_USER_ID)
                .remove(KEY_USER_ID_STR)
                .remove(KEY_ROLE)
                .remove(KEY_ROLE_SELECT_TIME)
                .remove(KEY_FAMILY_ID)
                .remove(KEY_FAMILY_NAME)
                .remove(KEY_INVITE_CODE)
                .remove(KEY_ELDER_ID)
                .remove(KEY_BIND_ELDER_COUNT)
                .remove(KEY_BIND_FAMILY_COUNT)
                .remove(KEY_NICKNAME)
                .remove(KEY_AVATAR_URL)
                .remove(KEY_PHONE)
                .remove(KEY_EMAIL)
                .remove(KEY_AI_CHAT_HISTORY_ELDER)
                .remove(KEY_AI_CHAT_HISTORY_FAMILY)
                .apply();

    }

    public void redirectToLogin(Context context, String reason) {
        clearSession();
        if (context == null) {
            return;
        }
        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra("session_expired", true);
        if (reason != null && !reason.trim().isEmpty()) {
            intent.putExtra("session_expired_message", reason.trim());
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    private String safeString(String key, String defaultValue) {
        String value = sp.getString(key, defaultValue);
        return value == null ? defaultValue : value;
    }

    private String resolveAiChatHistoryKey(String roleScope) {
        return "FAMILY".equalsIgnoreCase(roleScope)
                ? KEY_AI_CHAT_HISTORY_FAMILY
                : KEY_AI_CHAT_HISTORY_ELDER;
    }

    private double safeDouble(String key, double defaultValue) {

        String stored = sp.getString(key, null);
        if (stored == null || stored.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(stored.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}


