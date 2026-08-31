package com.carelink.controller;

import com.carelink.dto.ApiResponse;
import com.carelink.dto.CheckinRecordRequest;
import com.carelink.entity.User;
import com.carelink.service.CheckinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTasks(@RequestParam Long elderId) {
        return ResponseEntity.ok(ApiResponse.ok(checkinService.getTodayTasks(elderId)));
    }

    @PostMapping("/record")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitRecord(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CheckinRecordRequest request) {
        Map<String, Object> result = checkinService.submitRecord(
                request.getElderId(),
                request.getTaskId(),
                request.getTitle(),
                request.getStatus(),
                request.getNote()
        );
        return ResponseEntity.ok(ApiResponse.ok("打卡成功", result));
    }

    @GetMapping("/records/today")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTodayRecords(@RequestParam Long elderId) {
        return ResponseEntity.ok(ApiResponse.ok(checkinService.getTodayRecords(elderId)));
    }
}
