package com.carelink.controller;

import com.carelink.dto.AlertHandleRequest;
import com.carelink.dto.ApiResponse;
import com.carelink.entity.User;
import com.carelink.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAlertEvents(@RequestParam Long elderId) {
        return ResponseEntity.ok(ApiResponse.ok(alertService.getAlertEvents(elderId)));
    }

    @GetMapping("/events/pending")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPendingAlerts(
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("登录状态已过期，请重新登录"));
        }
        return ResponseEntity.ok(ApiResponse.ok(alertService.getPendingAlertsForFamily(user.getId())));
    }

    @PostMapping("/emergency/trigger")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerEmergency(
            @RequestBody Map<String, Object> body) {
        Long elderId = parseLong(body.get("elderId"));
        if (elderId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("缺少 elderId"));
        }
        String description = body.get("description") == null ? null : String.valueOf(body.get("description"));
        Double lat = parseDouble(body.get("lat"));
        Double lng = parseDouble(body.get("lng"));
        String alertType = body.get("alertType") == null ? null : String.valueOf(body.get("alertType"));
        Integer level = parseInteger(body.get("level"));
        Map<String, Object> result = alertService.triggerEmergency(elderId, description, lat, lng, alertType, level);
        return ResponseEntity.ok(ApiResponse.ok("紧急求助已发送", result));
    }

    @PutMapping("/events/{id}/handle")
    public ResponseEntity<ApiResponse<Void>> handleAlert(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody AlertHandleRequest request) {
        alertService.handleAlert(id, user.getId(), request.getStatus(), request.getHandleNote());
        return ResponseEntity.ok(ApiResponse.ok("事件已处理", null));
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
