package com.carelink.controller;

import com.carelink.dto.ApiResponse;
import com.carelink.entity.User;
import com.carelink.service.CompanionReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/family/reminders")
@RequiredArgsConstructor
public class CompanionReminderController {

    private final CompanionReminderService reminderService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createReminder(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("登录状态已过期，请重新登录"));
        }

        Object elderUserIdObj = body.get("elderUserId");
        if (elderUserIdObj == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("缺少 elderUserId"));
        }

        Long elderUserId = elderUserIdObj instanceof Number
                ? ((Number) elderUserIdObj).longValue()
                : Long.parseLong(String.valueOf(elderUserIdObj));

        String emoji = body.get("emoji") == null ? null : String.valueOf(body.get("emoji"));
        String label = body.get("label") == null ? null : String.valueOf(body.get("label"));
        String message = body.get("message") == null ? null : String.valueOf(body.get("message"));
        String imageUrl = body.get("imageUrl") == null ? null : String.valueOf(body.get("imageUrl"));

        Map<String, Object> result = reminderService.sendToElder(
                user.getId(),
                elderUserId,
                emoji,
                label,
                message,
                imageUrl
        );
        return ResponseEntity.ok(ApiResponse.ok("提醒发送成功", result));
    }

    @PostMapping("/upload-image")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadReminderImage(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("登录状态已过期，请重新登录"));
        }
        String imageUrl = reminderService.uploadReminderImage(file);
        Map<String, String> data = new HashMap<>();
        data.put("imageUrl", imageUrl);
        return ResponseEntity.ok(ApiResponse.ok("图片上传成功", data));
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUnread(
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("登录状态已过期，请重新登录"));
        }
        return ResponseEntity.ok(ApiResponse.ok(reminderService.getUnreadForElder(user.getId())));
    }

    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSent(
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("登录状态已过期，请重新登录"));
        }
        return ResponseEntity.ok(ApiResponse.ok(reminderService.getSentBySender(user.getId())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("登录状态已过期，请重新登录"));
        }
        reminderService.markRead(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok("已标记为已读", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReminder(
            @AuthenticationPrincipal User user,
            @PathVariable("id") Long id) {
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("登录状态已过期，请重新登录"));
        }
        reminderService.deleteBySender(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok("提醒已删除", null));
    }
}
