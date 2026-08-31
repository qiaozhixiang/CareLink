package com.carelink.controller;

import com.carelink.dto.ApiResponse;
import com.carelink.dto.LocationUpdateRequest;
import com.carelink.dto.MemberLocationUpdateRequest;
import com.carelink.entity.User;
import com.carelink.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @PostMapping("/update")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateLocation(
            @Valid @RequestBody LocationUpdateRequest request) {
        Map<String, Object> result = locationService.updateLocation(
                request.getElderId(),
                request.getLatitude(),
                request.getLongitude(),
                request.getAddress(),
                request.getEnabled(),
                request.getExpireAt()
        );
        return ResponseEntity.ok(ApiResponse.ok("位置已更新", result));
    }

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLatestLocation(@RequestParam Long elderId) {
        return ResponseEntity.ok(ApiResponse.ok(locationService.getLatestLocation(elderId)));
    }

    @GetMapping("/family/latest")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getFamilyLatestLocations(
            @AuthenticationPrincipal User user) {
        if (user == null || user.getFamilyId() == null) {
            return ResponseEntity.ok(ApiResponse.fail("尚未加入任何家庭"));
        }
        return ResponseEntity.ok(ApiResponse.ok(locationService.getFamilyLatestLocations(user.getFamilyId())));
    }

    @PostMapping("/toggle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleSharing(
            @RequestBody Map<String, Object> body) {
        Long elderId = Long.valueOf(body.get("elderId").toString());
        Boolean enabled = Boolean.valueOf(body.get("enabled").toString());
        return ResponseEntity.ok(ApiResponse.ok(locationService.toggleSharing(elderId, enabled)));
    }

    @PostMapping("/member/update")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateMemberLocation(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody MemberLocationUpdateRequest request) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("登录已过期，请重新登录"));
        }
        Map<String, Object> result = locationService.updateMemberLocation(
                user.getId(),
                user.getRole(),
                request.getLatitude(),
                request.getLongitude(),
                request.getAddress(),
                user.getNickname(),
                user.getAvatarUrl(),
                request.getEnabled(),
                request.getExpireAt()
        );
        return ResponseEntity.ok(ApiResponse.ok("位置已更新", result));
    }
}
