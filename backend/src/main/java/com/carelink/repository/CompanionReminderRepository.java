package com.carelink.repository;

import com.carelink.entity.CompanionReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanionReminderRepository extends JpaRepository<CompanionReminder, Long> {
    List<CompanionReminder> findTop20ByElderUserIdAndIsReadFalseOrderByCreatedAtDesc(Long elderUserId);
    List<CompanionReminder> findTop50BySenderUserIdOrderByCreatedAtDesc(Long senderUserId);
    Optional<CompanionReminder> findByIdAndSenderUserId(Long id, Long senderUserId);
}
