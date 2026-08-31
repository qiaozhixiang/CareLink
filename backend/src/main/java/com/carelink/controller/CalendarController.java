package com.carelink.controller;

import com.carelink.dto.ApiResponse;
import com.carelink.service.LunarCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 万年历 / 农历接口
 * 对应 App 中的"万年历"页面
 */
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final LunarCalendarService lunarCalendarService;

    /**
     * 获取指定日期的农历信息
     * @param timestamp 毫秒时间戳（默认当天）
     */
    @GetMapping("/lunar")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLunarInfo(
            @RequestParam(required = false) Long timestamp) {
        if (timestamp == null) {
            timestamp = System.currentTimeMillis();
        }
        Map<String, Object> lunarInfo = lunarCalendarService.getLunarInfo(timestamp);
        return ResponseEntity.ok(ApiResponse.ok(lunarInfo));
    }

    /**
     * 获取指定月份的宜忌汇总
     * @param timestamp 该月任意一天的毫秒时间戳
     */
    @GetMapping("/month/yiji")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMonthYiJi(
            @RequestParam Long timestamp) {
        Map<String, Object> lunarInfo = lunarCalendarService.getLunarInfo(timestamp);
        return ResponseEntity.ok(ApiResponse.ok(lunarInfo));
    }
}
