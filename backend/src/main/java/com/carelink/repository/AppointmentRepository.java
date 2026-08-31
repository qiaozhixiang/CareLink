package com.carelink.repository;

import com.carelink.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByElderIdOrderByStartTimeDesc(Long elderId);
    List<Appointment> findByElderIdAndStartTimeBetweenOrderByStartTimeAsc(
            Long elderId, LocalDateTime start, LocalDateTime end);
}
