package com.carelink.repository;

import com.carelink.entity.CheckinRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface CheckinRecordRepository extends JpaRepository<CheckinRecord, Long> {
    List<CheckinRecord> findByElderIdOrderByCompletedAtDesc(Long elderId);
    List<CheckinRecord> findByElderIdAndCompletedAtBetweenOrderByCompletedAtDesc(
            Long elderId, LocalDateTime start, LocalDateTime end);
}
