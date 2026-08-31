package com.carelink.controller;

import com.carelink.dto.ApiResponse;
import com.carelink.entity.User;
import com.carelink.service.HealthDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthDataService healthDataService;

    @PostMapping("/report")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reportHealth(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) Map<String, Object> body) {
        if (user == null || user.getId() == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("登录已过期，请重新登录"));
        }
        Map<String, Object> result = healthDataService.report(user.getId(), body);
        return ResponseEntity.ok(ApiResponse.ok("健康数据已同步", result));
    }

    @GetMapping("/family/latest")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFamilyLatestHealth(
            @AuthenticationPrincipal User user) {
        if (user == null || user.getId() == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("登录已过期，请重新登录"));
        }
        return ResponseEntity.ok(ApiResponse.ok(healthDataService.getFamilyLatest(user.getId())));
    }
}

