package com.carelink.app.ui.common;

import android.content.Context;

import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.ui.elder.ChatMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RoleScopedAiChatStore {

    public static final String ROLE_ELDER = "ELDER";
    public static final String ROLE_FAMILY = "FAMILY";

    private final PreferenceManager preferenceManager;
    private final String roleScope;

    public RoleScopedAiChatStore(Context context, String roleScope) {
        this.preferenceManager = new PreferenceManager(context.getApplicationContext());
        this.roleScope = normalizeRole(roleScope);
    }

    public List<ChatMessage> loadMessages() {
        String raw = preferenceManager.getAiChatHistory(roleScope);
        List<ChatMessage> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                result.add(new ChatMessage(
                        item.optInt("type", ChatMessage.TYPE_AI),
                        item.optString("content", ""),
                        item.optString("time", now())
                ));
            }
        } catch (Exception ignored) {
        }
        if (result.isEmpty()) {
            result.add(buildWelcomeMessage(roleScope));
        }
        return result;
    }

    public void saveMessages(List<ChatMessage> messages) {
        JSONArray array = new JSONArray();
        if (messages != null) {
            for (ChatMessage message : messages) {
                if (message == null) {
                    continue;
                }
                JSONObject item = new JSONObject();
                try {
                    item.put("type", message.getType());
                    item.put("content", message.getContent());
                    item.put("time", message.getTime());
                    array.put(item);
                } catch (Exception ignored) {
                }
            }
        }
        preferenceManager.saveAiChatHistory(roleScope, array.toString());
    }

    public List<ChatMessage> clearMessages() {
        preferenceManager.clearAiChatHistory(roleScope);
        List<ChatMessage> welcome = new ArrayList<>();
        welcome.add(buildWelcomeMessage(roleScope));
        saveMessages(welcome);
        return welcome;
    }

    public static ChatMessage buildWelcomeMessage(String roleScope) {
        String role = normalizeRole(roleScope);
        String content = ROLE_FAMILY.equals(role)
                ? "您好，我是 CareLink 家属关怀助手。您可以询问如何提醒老人、如何陪伴沟通，也可以随时开启新的独立会话。"
                : "您好，我是 CareLink 老人助手。您可以和我聊天、问健康建议、让我帮您整理待办或提醒家属。";
        return new ChatMessage(ChatMessage.TYPE_AI, content, now());
    }

    private static String normalizeRole(String role) {
        if (ROLE_FAMILY.equalsIgnoreCase(role)) {
            return ROLE_FAMILY;
        }
        return ROLE_ELDER;
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
    }
}
