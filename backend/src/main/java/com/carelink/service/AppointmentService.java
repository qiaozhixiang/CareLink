package com.carelink.service;

import com.carelink.dto.AppointmentRequest;
import com.carelink.entity.Appointment;
import com.carelink.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    /** 获取某老人的所有日程 */
    public List<Map<String, Object>> getAppointments(Long elderId) {
        List<Appointment> appointments = appointmentRepository.findByElderIdOrderByStartTimeDesc(elderId);
        return appointments.stream().map(this::toMap).collect(Collectors.toList());
    }

    /** 获取指定日期范围的日程 */
    public List<Map<String, Object>> getAppointmentsByDateRange(Long elderId, long startMs, long endMs) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), zone);
        LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endMs), zone);
        List<Appointment> appointments = appointmentRepository
                .findByElderIdAndStartTimeBetweenOrderByStartTimeAsc(elderId, start, end);
        return appointments.stream().map(this::toMap).collect(Collectors.toList());
    }

    /** 新建日程 */
    @Transactional
    public Map<String, Object> createAppointment(Long userId, AppointmentRequest request) {
        Appointment appointment = Appointment.builder()
                .elderId(request.getElderId())
                .title(request.getTitle())
                .category(request.getCategory())
                .startTime(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(request.getStartTime()), ZoneId.of("Asia/Shanghai")))
                .endTime(request.getEndTime() != null ?
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(request.getEndTime()), ZoneId.of("Asia/Shanghai")) : null)
                .location(request.getLocation())
                .notes(request.getNotes())
                .reminderType(request.getReminderType())
                .remindBefore(request.getRemindBefore())
                .createdBy(userId)
                .status(0)
                .build();

        appointment = appointmentRepository.save(appointment);

        Map<String, Object> result = new HashMap<>();
        result.put("id", appointment.getId());
        result.put("title", appointment.getTitle());
        return result;
    }

    /** 更新日程 */
    @Transactional
    public void updateAppointment(Long id, AppointmentRequest request) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("日程不存在"));

        if (request.getTitle() != null) appointment.setTitle(request.getTitle());
        if (request.getCategory() != null) appointment.setCategory(request.getCategory());
        if (request.getStartTime() != null) {
            appointment.setStartTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(request.getStartTime()), ZoneId.of("Asia/Shanghai")));
        }
        if (request.getEndTime() != null) {
            appointment.setEndTime(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(request.getEndTime()), ZoneId.of("Asia/Shanghai")));
        }
        if (request.getLocation() != null) appointment.setLocation(request.getLocation());
        if (request.getNotes() != null) appointment.setNotes(request.getNotes());

        appointmentRepository.save(appointment);
    }

    /** 删除日程 */
    @Transactional
    public void deleteAppointment(Long id) {
        appointmentRepository.deleteById(id);
    }

    /** 更新日程状态（完成/取消） */
    @Transactional
    public void updateStatus(Long id, int status) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("日程不存在"));
        appointment.setStatus(status);
        appointmentRepository.save(appointment);
    }

    private Map<String, Object> toMap(Appointment a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", a.getId());
        map.put("elderId", a.getElderId());
        map.put("title", a.getTitle());
        map.put("category", a.getCategory());
        map.put("startTime", a.getStartTime() != null ?
                a.getStartTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() : null);
        map.put("endTime", a.getEndTime() != null ?
                a.getEndTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() : null);
        map.put("location", a.getLocation());
        map.put("notes", a.getNotes());
        map.put("reminderType", a.getReminderType());
        map.put("remindBefore", a.getRemindBefore());
        map.put("status", a.getStatus());
        return map;
    }
}
