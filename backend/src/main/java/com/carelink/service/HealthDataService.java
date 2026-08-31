package com.carelink.service;

import com.carelink.entity.User;
import com.carelink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class HealthDataService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final Map<Long, Map<String, Object>> latestByUser = new ConcurrentHashMap<>();

    public Map<String, Object> report(Long userId, Map<String, Object> payload) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        long nowMs = System.currentTimeMillis();
        long reportedAtMs = parseLong(payload == null ? null : payload.get("reportedAt"), nowMs);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("userId", user.getId());
        snapshot.put("familyId", user.getFamilyId());
        snapshot.put("nickname", resolveDisplayName(user));
        snapshot.put("role", safeText(user.getRole(), "MEMBER"));
        snapshot.put("heartRate", safeInt(payload == null ? null : payload.get("heartRate"), 0));
        snapshot.put("bloodOxygen", safeInt(payload == null ? null : payload.get("bloodOxygen"), 0));
        snapshot.put("systolic", safeInt(payload == null ? null : payload.get("systolic"), 0));
        snapshot.put("diastolic", safeInt(payload == null ? null : payload.get("diastolic"), 0));
        snapshot.put("steps", safeInt(payload == null ? null : payload.get("steps"), 0));
        snapshot.put("source", safeText(payload == null ? null : payload.get("source"), "manual"));
        snapshot.put("fallDetected", safeBoolean(payload == null ? null : payload.get("fallDetected"), false));
        snapshot.put("reportedAt", toTimeText(reportedAtMs));
        snapshot.put("reportedAtMs", reportedAtMs);

        latestByUser.put(user.getId(), snapshot);
        return new LinkedHashMap<>(snapshot);
    }

    public Map<String, Object> getFamilyLatest(Long userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Long familyId = currentUser.getFamilyId();
        if (familyId == null || familyId <= 0) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("familyId", familyId);
            empty.put("count", 0);
            empty.put("items", new ArrayList<>());
            empty.put("updatedAt", toTimeText(System.currentTimeMillis()));
            return empty;
        }

        List<User> members = userRepository.findByFamilyId(familyId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (User member : members) {
            if (member == null || member.getId() == null) {
                continue;
            }
            Map<String, Object> latest = latestByUser.get(member.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            if (latest != null) {
                row.putAll(latest);
            }
            row.put("userId", member.getId());
            row.put("familyId", familyId);
            row.put("nickname", resolveDisplayName(member));
            row.put("role", safeText(member.getRole(), "MEMBER"));
            row.put("heartRate", safeInt(row.get("heartRate"), 0));
            row.put("bloodOxygen", safeInt(row.get("bloodOxygen"), 0));
            row.put("systolic", safeInt(row.get("systolic"), 0));
            row.put("diastolic", safeInt(row.get("diastolic"), 0));
            row.put("steps", safeInt(row.get("steps"), 0));
            row.put("source", safeText(row.get("source"), "none"));
            row.put("fallDetected", safeBoolean(row.get("fallDetected"), false));
            row.put("reportedAt", safeText(row.get("reportedAt"), "暂无"));
            row.put("reportedAtMs", parseLong(row.get("reportedAtMs"), 0L));
            items.add(row);
        }

        items.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                long r = parseLong(right.get("reportedAtMs"), 0L);
                long l = parseLong(left.get("reportedAtMs"), 0L);
                return Long.compare(r, l);
            }
        });

        for (Map<String, Object> item : items) {
            item.remove("reportedAtMs");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("familyId", familyId);
        result.put("count", items.size());
        result.put("items", items);
        result.put("updatedAt", toTimeText(System.currentTimeMillis()));
        return result;
    }

    private String resolveDisplayName(User user) {
        if (user == null) {
            return "成员";
        }
        String nickname = safeText(user.getNickname(), "");
        if (!nickname.isEmpty()) {
            return nickname;
        }
        String email = safeText(user.getEmail(), "");
        if (!email.isEmpty()) {
            int at = email.indexOf('@');
            return at > 0 ? email.substring(0, at) : email;
        }
        Long id = user.getId();
        return id == null ? "成员" : "成员" + id;
    }

    private String safeText(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private int safeInt(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean safeBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return fallback;
        }
        if ("1".equals(text)) {
            return true;
        }
        if ("0".equals(text)) {
            return false;
        }
        return Boolean.parseBoolean(text);
    }

    private long parseLong(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String toTimeText(long timestampMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault())
                .format(TIME_FORMATTER);
    }
}

