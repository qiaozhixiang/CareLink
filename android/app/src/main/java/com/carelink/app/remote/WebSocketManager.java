package com.carelink.app.remote;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.carelink.app.utils.ApiConfig;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WebSocketManager {
    private static final String TAG = "WebSocketManager";
    private static final String WS_URL = ApiConfig.WS_BASE_URL;

    private final OkHttpClient client;
    private final Handler mainHandler;
    private final CopyOnWriteArrayList<WsListener> listeners;

    private WebSocket webSocket;
    private volatile boolean isConnected = false;
    private volatile boolean isConnecting = false;

    public interface WsListener {
        void onConnected();

        void onDisconnected();

        void onMessage(String type, String data);

        void onFrame(byte[] jpegData);

        void onTouchEvent(float x, float y, String action);

        void onError(String error);
    }

    public WebSocketManager() {
        client = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
        mainHandler = new Handler(Looper.getMainLooper());
        listeners = new CopyOnWriteArrayList<>();
    }

    public void connect() {
        if (isConnected || isConnecting) {
            return;
        }

        String endpointError = validateEndpoint(WS_URL);
        if (!endpointError.isEmpty()) {
            notifyError(endpointError);
            return;
        }

        isConnecting = true;
        Request request = new Request.Builder().url(WS_URL).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.d(TAG, "WebSocket connected");
                isConnected = true;
                isConnecting = false;
                notifyConnected();
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                parseMessage(text);
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                Log.d(TAG, "Ignore binary websocket message, size=" + bytes.size());
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + reason);
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + reason);
                isConnected = false;
                isConnecting = false;
                notifyDisconnected();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                String error = sanitizeError(buildFailureMessage(t, response));
                Log.e(TAG, "WebSocket connect failed: " + error);
                isConnected = false;
                isConnecting = false;
                notifyError(error);
            }
        });
    }

    private String validateEndpoint(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "远程协助地址为空，请检查配置";
        }
        String normalized = url.trim().toLowerCase();
        if (!normalized.startsWith("ws://") && !normalized.startsWith("wss://")) {
            return "远程协助地址格式错误，必须以 ws:// 或 wss:// 开头";
        }
        return "";
    }

    private String buildFailureMessage(Throwable t, Response response) {
        String throwableMessage = t == null || t.getMessage() == null ? "" : t.getMessage().trim();
        if (response == null) {
            return throwableMessage;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("http ").append(response.code());
        if (response.message() != null && !response.message().trim().isEmpty()) {
            sb.append(' ').append(response.message().trim());
        }
        if (!throwableMessage.isEmpty()) {
            sb.append(" - ").append(throwableMessage);
        }
        return sb.toString();
    }

    private void parseMessage(String text) {
        try {
            SignalEnvelope signal = parseSignal(text);
            if (signal.type.isEmpty()) {
                notifyError("信令解析失败，请重新发起远程协助");
                return;
            }

            if ("frame".equals(signal.type)) {
                String frameBase64 = resolveFrameBase64(signal);
                if (!frameBase64.isEmpty()) {
                    byte[] jpegData = android.util.Base64.decode(frameBase64, android.util.Base64.DEFAULT);
                    mainHandler.post(() -> {
                        for (WsListener listener : listeners) {
                            listener.onFrame(jpegData);
                        }
                    });
                    return;
                }
            }

            if ("touch".equals(signal.type) && !signal.data.isEmpty()) {
                String[] parts = signal.data.split(":");
                if (parts.length >= 3) {
                    float x = safeFloat(parts[0], -1f);
                    float y = safeFloat(parts[1], -1f);
                    if (x >= 0f && y >= 0f) {
                        String action = parts[2].trim();
                        mainHandler.post(() -> {
                            for (WsListener listener : listeners) {
                                listener.onTouchEvent(x, y, action);
                            }
                        });
                    }
                }
                return;
            }

            final String finalType = signal.type;
            final String finalData = signal.data;
            mainHandler.post(() -> {
                for (WsListener listener : listeners) {
                    listener.onMessage(finalType, finalData);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Parse signaling failed", e);
            notifyError("信令解析失败，请重新发起远程协助");
        }
    }

    private SignalEnvelope parseSignal(String rawText) throws Exception {
        SignalEnvelope signal = new SignalEnvelope();
        String payload = rawText == null ? "" : rawText.trim();
        if (payload.isEmpty()) {
            return signal;
        }
        if (payload.contains("\\n")) {
            payload = payload.replace("\\n", "\n");
        }

        if (payload.startsWith("{") && payload.endsWith("}")) {
            JSONObject json = new JSONObject(payload);
            signal.type = json.optString("type", "").trim();
            signal.data = json.optString("data", "").trim();
            signal.base64 = json.optString("base64", "").trim();
            if (signal.base64.isEmpty()) {
                signal.base64 = extractBase64FromData(signal.data);
            }
            return signal;
        }

        Map<String, String> lines = new HashMap<>();
        String[] parts = payload.split("\\n");
        for (String line : parts) {
            if (line == null) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (!key.isEmpty()) {
                lines.put(key, value);
            }
        }

        signal.type = safeText(lines.get("type"));
        signal.data = safeText(lines.get("data"));
        signal.base64 = safeText(lines.get("base64"));
        if (signal.base64.isEmpty()) {
            signal.base64 = extractBase64FromData(signal.data);
        }
        return signal;
    }

    private String resolveFrameBase64(SignalEnvelope signal) {
        if (signal == null) {
            return "";
        }
        String value = safeText(signal.base64);
        if (!value.isEmpty()) {
            return value;
        }
        return extractBase64FromData(signal.data);
    }

    private String extractBase64FromData(String data) {
        String text = safeText(data);
        if (text.isEmpty()) {
            return "";
        }
        if (text.startsWith("base64:")) {
            return safeText(text.substring("base64:".length()));
        }
        return text;
    }

    public void sendRaw(String text) {
        if (webSocket != null && isConnected) {
            webSocket.send(text);
        }
    }

    public void sendFrame(byte[] jpegData) {
        if (webSocket != null && isConnected && jpegData != null && jpegData.length > 0) {
            String base64 = android.util.Base64.encodeToString(jpegData, android.util.Base64.NO_WRAP);
            webSocket.send("type:frame\nbase64:" + base64);
        }
    }

    public void sendTouch(float x, float y, String action) {
        if (webSocket != null && isConnected) {
            webSocket.send("type:touch\ndata:" + x + ":" + y + ":" + action);
        }
    }

    public void sendTouchAck(String data) {
        if (webSocket != null && isConnected) {
            webSocket.send("type:touch_ack\ndata:" + data);
        }
    }

    public void sendRegister(String role, String userId) {
        sendRaw("type:register\nrole:" + role + "\nuserId:" + userId);
    }

    public void sendInvite(String role, String userId, String targetUserId) {
        String target = targetUserId == null ? "" : targetUserId.trim();
        sendRaw("type:invite\nrole:" + role + "\nuserId:" + userId + "\ntargetUserId:" + target);
    }

    public void sendHangup() {
        sendRaw("type:hangup\ndata:bye");
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "user disconnect");
            webSocket = null;
        }
        isConnected = false;
        isConnecting = false;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void addListener(WsListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(WsListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyConnected() {
        mainHandler.post(() -> {
            for (WsListener listener : listeners) {
                listener.onConnected();
            }
        });
    }

    private void notifyDisconnected() {
        mainHandler.post(() -> {
            for (WsListener listener : listeners) {
                listener.onDisconnected();
            }
        });
    }

    private void notifyError(String error) {
        final String safeError = sanitizeError(error);
        mainHandler.post(() -> {
            for (WsListener listener : listeners) {
                listener.onError(safeError);
            }
        });
    }

    private float safeFloat(String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String sanitizeError(String raw) {
        String msg = raw == null ? "" : raw.trim();
        if (msg.isEmpty()) {
            return "网络连接失败，请检查网络后重试";
        }

        String lower = msg.toLowerCase();
        if (lower.contains("401")
                || lower.contains("403")
                || lower.contains("unauthorized")
                || lower.contains("forbidden")) {
            return "远程协助连接被拒绝，请确认后端已放行 /ws/**";
        }
        if (lower.contains("404") || lower.contains("not found")) {
            return "远程协助服务不可用，请检查服务地址与后端部署";
        }
        if (lower.contains("signaling_parse_failed")
                || lower.contains("json")
                || lower.contains("parse")) {
            return "信令解析失败，请重新发起远程协助";
        }
        if (isLikelyNetworkFailure(lower)) {
            return "网络连接失败，请检查网络后重试";
        }
        return msg;
    }

    private boolean isLikelyNetworkFailure(String lower) {
        if (lower == null) {
            return false;
        }
        return lower.contains("timeout")
                || lower.contains("timed out")
                || lower.contains("refused")
                || lower.contains("unreachable")
                || lower.contains("unknownhost")
                || lower.contains("no route")
                || lower.contains("unable to resolve host")
                || lower.contains("failed to connect");
    }

    private static class SignalEnvelope {
        private String type = "";
        private String data = "";
        private String base64 = "";
    }
}
