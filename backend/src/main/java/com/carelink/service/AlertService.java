package com.carelink.service;

import com.carelink.entity.AlertEvent;
import com.carelink.entity.User;
import com.carelink.repository.AlertEventRepository;
import com.carelink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertEventRepository alertEventRepository;
    private final UserRepository userRepository;

    public List<Map<String, Object>> getAlertEvents(Long elderId) {
        List<AlertEvent> events = alertEventRepository.findByElderIdOrderByCreatedAtDesc(elderId);
        Map<Long, User> elderMap = buildElderMap(events);
        return events.stream().map(event -> toMap(event, elderMap)).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getPendingAlertsForFamily(Long currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        Long familyId = currentUser.getFamilyId();
        if (familyId == null || familyId <= 0) {
            return new ArrayList<>();
        }

        List<User> members = userRepository.findByFamilyId(familyId);
        Map<Long, User> elderMap = members.stream()
                .filter(u -> "ELDER".equalsIgnoreCase(u.getRole()))
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        if (elderMap.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> elderIds = new ArrayList<>(elderMap.keySet());
        List<AlertEvent> events = alertEventRepository.findByElderIdInAndStatusOrderByCreatedAtDesc(elderIds, "PENDING");
        return events.stream().map(event -> toMap(event, elderMap)).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> triggerEmergency(Long elderId, String description, Double lat, Double lng) {
        return triggerEmergency(elderId, description, lat, lng, "SOS", 3);
    }

    @Transactional
    public Map<String, Object> triggerEmergency(Long elderId, String description, Double lat, Double lng,
                                                String alertType, Integer level) {
        User elder = userRepository.findById(elderId)
                .orElseThrow(() -> new RuntimeException("老人不存在"));

        String normalizedType = normalizeAlertType(alertType);
        int normalizedLevel = normalizeLevel(level);
        String desc = description == null ? "" : description.trim();
        if (desc.isEmpty()) {
            StringBuilder builder;
            if ("FALL".equals(normalizedType)) {
                builder = new StringBuilder("检测到老人疑似跌倒，请家属立即确认");
            } else if ("ABNORMAL_VITAL".equals(normalizedType)) {
                builder = new StringBuilder("检测到老人生命体征异常，请家属及时关注");
            } else {
                builder = new StringBuilder("老人发起协助请求");
            }
            if (lat != null && lng != null) {
                builder.append("，位置：").append(lat).append(",").append(lng);
            }
            desc = builder.toString();
        }

        AlertEvent alert = AlertEvent.builder()
                .elderId(elderId)
                .alertType(normalizedType)
                .description(desc)
                .level(normalizedLevel)
                .status("PENDING")
                .build();

        alert = alertEventRepository.save(alert);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("alertId", alert.getId());
        result.put("elderId", elderId);
        result.put("elderName", resolveDisplayName(elder));
        result.put("familyId", elder.getFamilyId());
        result.put("alertType", normalizedType);
        result.put("level", normalizedLevel);
        result.put("message", "协助请求已发送给家庭成员");
        return result;
    }

    @Transactional
    public void handleAlert(Long alertId, Long handlerId, String status, String handleNote) {
        AlertEvent alert = alertEventRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("事件不存在"));

        alert.setStatus(status);
        alert.setAssignedTo(handlerId);
        alert.setHandleNote(handleNote);
        alert.setHandledAt(System.currentTimeMillis());
        alertEventRepository.save(alert);
    }

    private Map<Long, User> buildElderMap(List<AlertEvent> events) {
        List<Long> elderIds = events.stream()
                .map(AlertEvent::getElderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (elderIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return userRepository.findAllById(elderIds).stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, Object> toMap(AlertEvent event, Map<Long, User> elderMap) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", event.getId());
        map.put("elderId", event.getElderId());
        User elder = elderMap == null ? null : elderMap.get(event.getElderId());
        map.put("elderName", elder == null ? "老人" : resolveDisplayName(elder));
        map.put("alertType", event.getAlertType());
        map.put("description", event.getDescription());
        map.put("level", event.getLevel());
        map.put("status", event.getStatus());
        map.put("assignedTo", event.getAssignedTo());
        map.put("handleNote", event.getHandleNote());
        map.put("createdAt", event.getCreatedAt() != null ? event.getCreatedAt().toString() : null);
        return map;
    }

    private String resolveDisplayName(User user) {
        if (user == null) {
            return "老人";
        }
        String nickname = trim(user.getNickname());
        if (!nickname.isEmpty()) {
            return nickname;
        }
        String email = trim(user.getEmail());
        if (!email.isEmpty()) {
            int at = email.indexOf('@');
            return at > 0 ? email.substring(0, at) : email;
        }
        Long id = user.getId();
        return id == null ? "老人" : "成员" + id;
    }

    private String trim(String text) {
        return text == null ? "" : text.trim();
    }

    private String normalizeAlertType(String alertType) {
        String text = trim(alertType).toUpperCase();
        if (text.isEmpty()) {
            return "SOS";
        }
        switch (text) {
            case "LOW_STAY":
            case "FALL":
            case "ABNORMAL_VITAL":
            case "MISSING_MEDICINE":
            case "SOS":
            case "CUSTOM":
                return text;
            default:
                return "SOS";
        }
    }

    private int normalizeLevel(Integer level) {
        if (level == null) {
            return 3;
        }
        if (level < 1) {
            return 1;
        }
        if (level > 3) {
            return 3;
        }
        return level;
    }
}
