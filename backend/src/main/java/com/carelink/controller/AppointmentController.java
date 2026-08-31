package com.carelink.controller;

import com.carelink.dto.ApiResponse;
import com.carelink.dto.AppointmentRequest;
import com.carelink.entity.User;
import com.carelink.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAppointments(
            @RequestParam Long elderId) {
        List<Map<String, Object>> appointments = appointmentService.getAppointments(elderId);
        return ResponseEntity.ok(ApiResponse.ok(appointments));
    }

    @GetMapping("/range")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAppointmentsByRange(
            @RequestParam Long elderId,
            @RequestParam long start,
            @RequestParam long end) {
        List<Map<String, Object>> appointments = appointmentService.getAppointmentsByDateRange(elderId, start, end);
        return ResponseEntity.ok(ApiResponse.ok(appointments));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createAppointment(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AppointmentRequest request) {
        Map<String, Object> result = appointmentService.createAppointment(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok("日程创建成功", result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> updateAppointment(
            @PathVariable Long id,
            @RequestBody AppointmentRequest request) {
        appointmentService.updateAppointment(id, request);
        return ResponseEntity.ok(ApiResponse.ok("日程更新成功", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAppointment(@PathVariable Long id) {
        appointmentService.deleteAppointment(id);
        return ResponseEntity.ok(ApiResponse.ok("日程已删除", null));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        appointmentService.updateStatus(id, body.get("status"));
        return ResponseEntity.ok(ApiResponse.ok("状态已更新", null));
    }
}
