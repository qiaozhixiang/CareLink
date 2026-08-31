package com.carelink.service;

import com.carelink.entity.LocationShare;
import com.carelink.entity.User;
import com.carelink.repository.LocationShareRepository;
import com.carelink.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationShareRepository locationShareRepository;
    private final UserRepository userRepository;

    @Transactional
    public Map<String, Object> updateLocation(Long elderId, Double latitude, Double longitude,
                                              String address, Boolean enabled, Long expireAtMs) {
        Optional<LocationShare> existingOpt = locationShareRepository.findTopByElderIdOrderByUpdatedAtDesc(elderId);

        LocationShare locationShare;
        if (existingOpt.isPresent()) {
            locationShare = existingOpt.get();
        } else {
            locationShare = new LocationShare();
            locationShare.setElderId(elderId);
            locationShare.setUserId(elderId);
            locationShare.setUserRole("ELDER");
        }

        locationShare.setLatitude(latitude);
        locationShare.setLongitude(longitude);
        locationShare.setAddress(address);
        locationShare.setEnabled(enabled != null ? enabled : true);
        if (expireAtMs != null) {
            locationShare.setExpireAt(LocalDateTime.now().plusNanos(expireAtMs * 1_000_000));
        }

        locationShare = locationShareRepository.save(locationShare);

        Map<String, Object> result = new HashMap<>();
        result.put("id", locationShare.getId());
        result.put("elderId", elderId);
        result.put("updatedAt", locationShare.getUpdatedAt() != null
                ? locationShare.getUpdatedAt().toString() : null);
        return result;
    }

    @Transactional
    public Map<String, Object> updateMemberLocation(Long userId, String userRole,
                                                    Double latitude, Double longitude,
                                                    String address, String nickname, String avatarUrl,
                                                    Boolean enabled, Long expireAtMs) {
        Optional<LocationShare> existingOpt = locationShareRepository.findTopByUserIdOrderByUpdatedAtDesc(userId);

        LocationShare locationShare;
        if (existingOpt.isPresent()) {
            locationShare = existingOpt.get();
        } else {
            locationShare = new LocationShare();
            locationShare.setUserId(userId);
            if ("ELDER".equalsIgnoreCase(userRole)) {
                locationShare.setElderId(userId);
            }
        }

        locationShare.setUserRole(userRole);
        locationShare.setLatitude(latitude);
        locationShare.setLongitude(longitude);
        locationShare.setAddress(address != null ? address : "");
        locationShare.setNickname(nickname);
        locationShare.setAvatarUrl(avatarUrl);
        locationShare.setEnabled(enabled != null ? enabled : true);
        if (expireAtMs != null) {
            locationShare.setExpireAt(LocalDateTime.now().plusNanos(expireAtMs * 1_000_000));
        }

        locationShare = locationShareRepository.save(locationShare);

        Map<String, Object> result = new HashMap<>();
        result.put("id", locationShare.getId());
        result.put("userId", userId);
        result.put("userRole", userRole);
        result.put("updatedAt", locationShare.getUpdatedAt() != null
                ? locationShare.getUpdatedAt().toString() : null);
        return result;
    }

    public Map<String, Object> getLatestLocation(Long elderId) {
        Optional<LocationShare> opt = locationShareRepository.findTopByElderIdOrderByUpdatedAtDesc(elderId);
        if (opt.isEmpty()) {
            return buildEmptyLocationPayload(elderId);
        }
        return toLocationPayload(opt.get(), elderId);
    }

    public List<Map<String, Object>> getFamilyLatestLocations(Long familyId) {
        if (familyId == null) {
            throw new RuntimeException("家庭 ID 不能为空");
        }

        List<User> allMembers = userRepository.findByFamilyId(familyId);
        if (allMembers.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> allUserIds = allMembers.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        List<LocationShare> shares = locationShareRepository.findByUserIdInOrderByUpdatedAtDesc(allUserIds);
        Map<Long, LocationShare> latestLocationMap = new LinkedHashMap<>();
        for (LocationShare share : shares) {
            if (share == null || share.getUserId() == null) {
                continue;
            }
            latestLocationMap.putIfAbsent(share.getUserId(), share);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (User member : allMembers) {
            Long userId = member.getId();
            LocationShare latest = latestLocationMap.get(userId);

            Map<String, Object> payload = new LinkedHashMap<>();
            String displayName = resolveDisplayName(member);
            payload.put("userId", userId);
            payload.put("nickname", displayName);
            payload.put("name", displayName);
            payload.put("role", member.getRole());
            payload.put("avatarUrl", member.getAvatarUrl());
            payload.put("familyId", member.getFamilyId());

            if (latest != null && Boolean.TRUE.equals(latest.getEnabled())) {
                payload.put("latitude", latest.getLatitude());
                payload.put("longitude", latest.getLongitude());
                payload.put("address", latest.getAddress());
                payload.put("enabled", true);
                payload.put("updatedAt", latest.getUpdatedAt() != null ? latest.getUpdatedAt().toString() : null);
                payload.put("expireAt", latest.getExpireAt() != null ? latest.getExpireAt().toString() : null);
                payload.put("hasLocation", true);
                payload.put("locationError", "");
            } else {
                payload.put("latitude", 0.0);
                payload.put("longitude", 0.0);
                payload.put("address", "暂无共享位置");
                payload.put("enabled", false);
                payload.put("updatedAt", latest != null && latest.getUpdatedAt() != null ? latest.getUpdatedAt().toString() : null);
                payload.put("expireAt", latest != null && latest.getExpireAt() != null ? latest.getExpireAt().toString() : null);
                payload.put("hasLocation", false);
                payload.put("locationError", "该成员未开启位置共享");
            }
            result.add(payload);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> toggleSharing(Long elderId, Boolean enabled) {
        Optional<LocationShare> opt = locationShareRepository.findTopByElderIdOrderByUpdatedAtDesc(elderId);
        if (opt.isPresent()) {
            LocationShare loc = opt.get();
            loc.setEnabled(enabled);
            locationShareRepository.save(loc);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("elderId", elderId);
        result.put("enabled", enabled);
        result.put("message", Boolean.TRUE.equals(enabled) ? "已开启位置共享" : "已关闭位置共享");
        return result;
    }

    private Map<String, Object> toLocationPayload(LocationShare loc, Long elderId) {
        Map<String, Object> result = new HashMap<>();
        result.put("elderId", elderId);
        result.put("latitude", loc.getLatitude());
        result.put("longitude", loc.getLongitude());
        result.put("address", loc.getAddress());
        result.put("enabled", loc.getEnabled());
        result.put("updatedAt", loc.getUpdatedAt() != null ? loc.getUpdatedAt().toString() : null);
        result.put("expireAt", loc.getExpireAt() != null ? loc.getExpireAt().toString() : null);
        return result;
    }

    private Map<String, Object> buildEmptyLocationPayload(Long elderId) {
        Map<String, Object> result = new HashMap<>();
        result.put("elderId", elderId);
        result.put("latitude", 0.0);
        result.put("longitude", 0.0);
        result.put("address", "暂无共享位置");
        result.put("enabled", false);
        result.put("updatedAt", null);
        result.put("expireAt", null);
        return result;
    }

    private String resolveDisplayName(User user) {
        if (user == null) {
            return "家庭成员";
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
        String role = trim(user.getRole());
        if (id != null && !role.isEmpty()) {
            return role.toUpperCase() + "-" + id;
        }
        return id == null ? "家庭成员" : "成员" + id;
    }

    private String trim(String text) {
        return text == null ? "" : text.trim();
    }
}
