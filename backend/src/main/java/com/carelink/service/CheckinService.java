package com.carelink.service;

import com.carelink.entity.CheckinRecord;
import com.carelink.entity.CheckinTask;
import com.carelink.repository.CheckinRecordRepository;
import com.carelink.repository.CheckinTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckinService {

    private final CheckinTaskRepository checkinTaskRepository;
    private final CheckinRecordRepository checkinRecordRepository;

    /** 获取老人当天的打卡任务 */
    public List<Map<String, Object>> getTodayTasks(Long elderId) {
        List<CheckinTask> tasks = checkinTaskRepository.findByElderIdAndActiveTrue(elderId);

        // 获取今日所有记录
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zone);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        List<CheckinRecord> todayRecords = checkinRecordRepository
                .findByElderIdAndCompletedAtBetweenOrderByCompletedAtDesc(elderId, startOfDay, endOfDay);

        Set<Long> completedTaskIds = todayRecords.stream()
                .map(CheckinRecord::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return tasks.stream().map(task -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("taskId", task.getId());
            map.put("title", task.getTitle());
            map.put("category", task.getCategory());
            map.put("expectedTime", task.getExpectedTime());
            map.put("completed", completedTaskIds.contains(task.getId()));
            return map;
        }).collect(Collectors.toList());
    }

    /** 提交打卡记录 */
    @Transactional
    public Map<String, Object> submitRecord(Long elderId, Long taskId, String title,
                                             String status, String note) {
        CheckinRecord record = CheckinRecord.builder()
                .elderId(elderId)
                .taskId(taskId)
                .title(title)
                .completedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))
                .status(status != null ? status : "DONE")
                .note(note)
                .build();

        record = checkinRecordRepository.save(record);

        Map<String, Object> result = new HashMap<>();
        result.put("recordId", record.getId());
        result.put("status", record.getStatus());
        return result;
    }

    /** 获取今日所有打卡记录 */
    public List<Map<String, Object>> getTodayRecords(Long elderId) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDateTime start = LocalDate.now(zone).atStartOfDay();
        LocalDateTime end = LocalDate.now(zone).plusDays(1).atStartOfDay();

        List<CheckinRecord> records = checkinRecordRepository
                .findByElderIdAndCompletedAtBetweenOrderByCompletedAtDesc(elderId, start, end);

        return records.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("recordId", r.getId());
            map.put("taskId", r.getTaskId());
            map.put("title", r.getTitle());
            map.put("status", r.getStatus());
            map.put("note", r.getNote());
            map.put("completedAt", r.getCompletedAt() != null ? r.getCompletedAt().toString() : null);
            return map;
        }).collect(Collectors.toList());
    }
}
