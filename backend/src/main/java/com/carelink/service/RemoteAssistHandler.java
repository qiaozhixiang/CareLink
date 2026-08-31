package com.carelink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RemoteAssistHandler extends TextWebSocketHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // sessionId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // elderUserId -> sessionId
    private final Map<String, String> elderSessionMap = new ConcurrentHashMap<>();
    // familyUserId -> sessionId
    private final Map<String, String> familySessionMap = new ConcurrentHashMap<>();
    // sessionId -> registration info
    private final Map<String, ClientRegistration> registrationMap = new ConcurrentHashMap<>();
    // sessionId -> peerSessionId (bidirectional)
    private final Map<String, String> peerSessionMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        System.out.println("[RemoteAssist] connected session=" + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message == null ? null : message.getPayload();
        try {
            SignalingMessage signaling = parsePayload(payload);
            String type = trim(signaling.type);
            if (type.isEmpty()) {
                sendError(session, "missing_type");
                return;
            }

            switch (type) {
                case "register":
                    handleRegister(session, trim(signaling.role), trim(signaling.userId));
                    break;
                case "invite":
                    handleInvite(session, trim(signaling.role), trim(signaling.userId), trim(signaling.targetUserId));
                    break;
                case "answer":
                case "ice":
                case "candidate":
                    relayToPeer(session, type, "data:" + trim(signaling.data));
                    break;
                case "frame":
                    relayFrameToPeer(session, trim(signaling.base64));
                    break;
                case "touch":
                    relayTouchToPeer(session, trim(signaling.data));
                    break;
                case "touch_ack":
                    relayToPeer(session, type, "data:" + trim(signaling.data));
                    break;
                case "hangup":
                    handleHangup(session);
                    break;
                case "list":
                    sendOnlineList(session);
                    break;
                default:
                    sendError(session, "unsupported_type:" + type);
                    break;
            }
        } catch (Exception e) {
            try {
                sendError(session, "signaling_parse_failed");
            } catch (IOException ignored) {
            }
            System.err.println("[RemoteAssist] parse error: " + e.getMessage());
        }
    }

    private void handleRegister(WebSocketSession session, String role, String userId) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        String normalizedRole = normalizeRole(role);
        if (normalizedUserId.isEmpty()) {
            sendError(session, "missing_user_id");
            return;
        }
        clearSessionRegistration(session.getId());
        if ("elder".equals(normalizedRole)) {
            elderSessionMap.put(normalizedUserId, session.getId());
        } else if ("family".equals(normalizedRole)) {
            familySessionMap.put(normalizedUserId, session.getId());
        } else {
            sendError(session, "invalid_role");
            return;
        }
        registrationMap.put(session.getId(), new ClientRegistration(normalizedRole, normalizedUserId));
        sendMessage(session, "type:ack\ndata:registered as " + normalizedRole);
    }

    private void handleInvite(WebSocketSession session, String role, String userId, String targetUserId) throws IOException {
        String normalizedUserId = normalizeUserId(userId);
        String normalizedRole = normalizeRole(role);
        if (normalizedUserId.isEmpty()) {
            sendError(session, "missing_user_id");
            return;
        }
        if (!isRegisteredAs(session.getId(), normalizedRole, normalizedUserId)) {
            sendError(session, "session_not_registered");
            return;
        }
        if ("family".equals(normalizedRole)) {
            handleFamilyInvite(session, normalizedUserId, targetUserId);
            return;
        }
        if ("elder".equals(normalizedRole)) {
            handleElderInvite(session, normalizedUserId, targetUserId);
            return;
        }
        sendError(session, "invalid_role");
    }

    private void handleFamilyInvite(WebSocketSession familySession, String familyUserId, String targetElderUserId) throws IOException {
        WebSocketSession elderSession = resolveElderSessionForInvite(targetElderUserId);
        if (elderSession == null) {
            sendError(familySession, "elder_offline");
            return;
        }

        pairSessions(familySession.getId(), elderSession.getId());
        sendMessage(elderSession, "type:family_starting\ndata:family:" + familyUserId);
        sendMessage(elderSession, "type:incoming_invite\ndata:family:" + familyUserId);
        sendMessage(familySession, "type:invite_sent\ndata:waiting_elder");
    }

    private void handleElderInvite(WebSocketSession elderSession, String elderUserId, String targetFamilyUserId) throws IOException {
        String familyUserId = normalizeUserId(targetFamilyUserId);
        WebSocketSession familySession = findFamilySessionByUserId(familyUserId);
        if (familySession == null) {
            // Fallback: if already paired by initial family invite, use existing peer.
            familySession = getOpenSession(peerSessionMap.get(elderSession.getId()));
        }
        if (familySession == null) {
            sendError(elderSession, "family_offline");
            return;
        }

        pairSessions(elderSession.getId(), familySession.getId());
        sendMessage(familySession, "type:connected\ndata:elder_accepted");
        sendMessage(elderSession, "type:family_connected\ndata:ready");
    }

    private WebSocketSession resolveElderSessionForInvite(String targetElderUserId) {
        String target = normalizeUserId(targetElderUserId);

        if (!target.isEmpty() && !"elder_request".equalsIgnoreCase(target)) {
            WebSocketSession direct = findElderSessionByUserId(target);
            if (direct != null) {
                return direct;
            }
        }

        for (Map.Entry<String, String> entry : elderSessionMap.entrySet()) {
            WebSocketSession candidate = getOpenSession(entry.getValue());
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private WebSocketSession findElderSessionByUserId(String rawTarget) {
        return findSessionByUserId(rawTarget, "elder", elderSessionMap);
    }

    private WebSocketSession findFamilySessionByUserId(String rawTarget) {
        return findSessionByUserId(rawTarget, "family", familySessionMap);
    }

    private WebSocketSession findSessionByUserId(String rawTarget, String role, Map<String, String> roleSessionMap) {
        String target = normalizeUserId(rawTarget);
        if (target.isEmpty()) {
            return null;
        }

        WebSocketSession direct = getOpenSession(roleSessionMap.get(target));
        if (direct != null) {
            return direct;
        }

        for (Map.Entry<String, String> entry : roleSessionMap.entrySet()) {
            String registered = normalizeUserId(entry.getKey());
            if (registered.isEmpty() || !isLikelySameUserId(registered, target)) {
                continue;
            }
            WebSocketSession candidate = getOpenSession(entry.getValue());
            if (candidate != null) {
                return candidate;
            }
        }

        for (Map.Entry<String, ClientRegistration> entry : registrationMap.entrySet()) {
            ClientRegistration registration = entry.getValue();
            if (registration == null
                    || !registration.role.equals(role)
                    || !isLikelySameUserId(registration.userId, target)) {
                continue;
            }
            WebSocketSession candidate = getOpenSession(entry.getKey());
            if (candidate != null) {
                roleSessionMap.put(registration.userId, candidate.getId());
                return candidate;
            }
        }
        return null;
    }

    private boolean isLikelySameUserId(String registered, String target) {
        return registered.equals(target)
                || registered.endsWith(target)
                || target.endsWith(registered)
                || registered.contains(target)
                || target.contains(registered);
    }

    private String normalizeUserId(String raw) {
        String value = trim(raw);
        if (value.isEmpty()) {
            return "";
        }
        if (value.startsWith("elder:")) {
            value = trim(value.substring("elder:".length()));
        }
        if (value.startsWith("family:")) {
            value = trim(value.substring("family:".length()));
        }
        return value;
    }

    private String normalizeRole(String raw) {
        String value = trim(raw).toLowerCase();
        if ("elder".equals(value) || "family".equals(value)) {
            return value;
        }
        return "";
    }

    private void relayToPeer(WebSocketSession session, String type, String dataLine) throws IOException {
        WebSocketSession peer = getPeerSession(session.getId());
        if (peer == null) {
            sendError(session, "peer_not_connected");
            return;
        }
        StringBuilder payload = new StringBuilder("type:").append(type);
        String extra = trim(dataLine);
        if (!extra.isEmpty()) {
            payload.append('\n').append(extra);
        }
        sendMessage(peer, payload.toString());
    }

    private void relayFrameToPeer(WebSocketSession session, String base64Data) throws IOException {
        if (base64Data.isEmpty()) {
            sendError(session, "empty_frame");
            return;
        }
        WebSocketSession peer = getPeerSession(session.getId());
        if (peer == null) {
            sendError(session, "peer_not_connected");
            return;
        }
        sendMessage(peer, "type:frame\nbase64:" + base64Data);
    }

    private void relayTouchToPeer(WebSocketSession session, String touchData) throws IOException {
        if (touchData.isEmpty()) {
            sendError(session, "empty_touch");
            return;
        }
        WebSocketSession peer = getPeerSession(session.getId());
        if (peer == null) {
            sendError(session, "peer_not_connected");
            return;
        }
        sendMessage(peer, "type:touch\ndata:" + touchData);
    }

    private void handleHangup(WebSocketSession session) throws IOException {
        String peerSessionId = peerSessionMap.get(session.getId());
        WebSocketSession peer = getOpenSession(peerSessionId);
        unpair(session.getId());
        if (peer != null) {
            sendMessage(peer, "type:hangup\ndata:peer_disconnected");
        }
        sendMessage(session, "type:hangup\ndata:ok");
    }

    private void sendOnlineList(WebSocketSession session) throws IOException {
        StringBuilder sb = new StringBuilder("type:online_list\ndata:");
        sb.append("elder:");
        for (String uid : elderSessionMap.keySet()) {
            sb.append(uid).append(',');
        }
        sb.append(";family:");
        for (String uid : familySessionMap.keySet()) {
            sb.append(uid).append(',');
        }
        sendMessage(session, sb.toString());
    }

    private WebSocketSession getPeerSession(String sessionId) {
        String peerSessionId = peerSessionMap.get(sessionId);
        return getOpenSession(peerSessionId);
    }

    private WebSocketSession getOpenSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            clearSessionRegistration(sessionId);
            unpair(sessionId);
            return null;
        }
        return session;
    }

    private void pairSessions(String sessionA, String sessionB) {
        if (sessionA == null || sessionA.isEmpty() || sessionB == null || sessionB.isEmpty()) {
            return;
        }
        unpair(sessionA);
        unpair(sessionB);
        peerSessionMap.put(sessionA, sessionB);
        peerSessionMap.put(sessionB, sessionA);
    }

    private void unpair(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        String peer = peerSessionMap.remove(sessionId);
        if (peer != null) {
            peerSessionMap.remove(peer);
        }
    }

    private void sendError(WebSocketSession session, String code) throws IOException {
        sendMessage(session, "type:error\ndata:" + trim(code));
    }

    private void sendMessage(WebSocketSession session, String message) throws IOException {
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(message));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        clearSessionRegistration(session.getId());

        String peerSessionId = peerSessionMap.get(session.getId());
        WebSocketSession peer = getOpenSession(peerSessionId);
        unpair(session.getId());
        if (peer != null) {
            sendMessage(peer, "type:hangup\ndata:peer_disconnected");
        }

        System.out.println("[RemoteAssist] closed session=" + session.getId() + " status=" + status);
    }

    private void removeByValue(Map<String, String> map, String targetSessionId) {
        map.entrySet().removeIf(entry -> targetSessionId.equals(entry.getValue()));
    }

    private boolean isRegisteredAs(String sessionId, String role, String userId) {
        ClientRegistration registration = registrationMap.get(sessionId);
        return registration != null
                && registration.role.equals(role)
                && registration.userId.equals(userId);
    }

    private void clearSessionRegistration(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        ClientRegistration registration = registrationMap.remove(sessionId);
        if (registration != null) {
            if ("elder".equals(registration.role)) {
                elderSessionMap.remove(registration.userId, sessionId);
            } else if ("family".equals(registration.role)) {
                familySessionMap.remove(registration.userId, sessionId);
            }
            restoreRoleSessionMapping(registration.role, registration.userId);
        }
        removeByValue(elderSessionMap, sessionId);
        removeByValue(familySessionMap, sessionId);
    }

    private void restoreRoleSessionMapping(String role, String userId) {
        if (role == null || role.isEmpty() || userId == null || userId.isEmpty()) {
            return;
        }
        Map<String, String> targetMap = "elder".equals(role) ? elderSessionMap : familySessionMap;
        for (Map.Entry<String, ClientRegistration> entry : registrationMap.entrySet()) {
            ClientRegistration registration = entry.getValue();
            if (registration == null
                    || !role.equals(registration.role)
                    || !userId.equals(registration.userId)) {
                continue;
            }
            WebSocketSession session = getOpenSession(entry.getKey());
            if (session != null) {
                targetMap.put(userId, session.getId());
                return;
            }
        }
    }

    private String trim(String text) {
        return text == null ? "" : text.trim();
    }

    private SignalingMessage parsePayload(String payload) throws IOException {
        SignalingMessage message = new SignalingMessage();
        String text = trim(payload);
        if (text.contains("\\n")) {
            text = text.replace("\\n", "\n");
        }
        if (text.isEmpty()) {
            return message;
        }

        if (text.startsWith("{") && text.endsWith("}")) {
            JsonNode node = OBJECT_MAPPER.readTree(text);
            message.type = node.path("type").asText("");
            message.role = node.path("role").asText("");
            message.userId = node.path("userId").asText("");
            message.targetUserId = node.path("targetUserId").asText("");
            message.data = node.path("data").asText("");
            message.base64 = node.path("base64").asText("");
            return message;
        }

        String[] lines = text.split("\\n");
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trim(line.substring(0, colon));
            String value = trim(line.substring(colon + 1));
            switch (key) {
                case "type":
                    message.type = value;
                    break;
                case "role":
                    message.role = value;
                    break;
                case "userId":
                    message.userId = value;
                    break;
                case "targetUserId":
                    message.targetUserId = value;
                    break;
                case "data":
                    message.data = value;
                    break;
                case "base64":
                    message.base64 = value;
                    break;
                default:
                    break;
            }
        }

        return message;
    }

    private static class SignalingMessage {
        private String type = "";
        private String role = "";
        private String userId = "";
        private String targetUserId = "";
        private String data = "";
        private String base64 = "";
    }

    private static class ClientRegistration {
        private final String role;
        private final String userId;

        private ClientRegistration(String role, String userId) {
            this.role = role;
            this.userId = userId;
        }
    }
}
