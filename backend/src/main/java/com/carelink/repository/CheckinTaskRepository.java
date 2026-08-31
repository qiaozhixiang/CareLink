package com.carelink.repository;

import com.carelink.entity.CheckinTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CheckinTaskRepository extends JpaRepository<CheckinTask, Long> {
    List<CheckinTask> findByElderIdAndActiveTrue(Long elderId);
}
