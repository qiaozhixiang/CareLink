package com.carelink.repository;

import com.carelink.entity.CareNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CareNoteRepository extends JpaRepository<CareNote, Long> {
    List<CareNote> findByElderIdOrderByCreatedAtDesc(Long elderId);
    List<CareNote> findByAuthorIdOrderByCreatedAtDesc(Long authorId);
}
