package com.carelink.repository;

import com.carelink.entity.AlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {
    List<AlertEvent> findByElderIdOrderByCreatedAtDesc(Long elderId);
    List<AlertEvent> findByAssignedToOrderByCreatedAtDesc(Long assignedTo);
    List<AlertEvent> findByStatusOrderByCreatedAtDesc(String status);
    List<AlertEvent> findByElderIdInAndStatusOrderByCreatedAtDesc(List<Long> elderIds, String status);
}
