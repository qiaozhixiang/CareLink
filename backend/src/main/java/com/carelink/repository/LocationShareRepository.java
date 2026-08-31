package com.carelink.repository;

import com.carelink.entity.LocationShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationShareRepository extends JpaRepository<LocationShare, Long> {
    Optional<LocationShare> findTopByElderIdOrderByUpdatedAtDesc(Long elderId);
    List<LocationShare> findByElderIdInOrderByUpdatedAtDesc(List<Long> elderIds);

    /** 查找某用户的最新位置（通用，支持老人和家属） */
    Optional<LocationShare> findTopByUserIdOrderByUpdatedAtDesc(Long userId);

    /** 批量查找多个用户的最新位置 */
    List<LocationShare> findByUserIdInOrderByUpdatedAtDesc(List<Long> userIds);

    /** 查找某家庭所有成员的最新位置 */
    List<LocationShare> findByUserIdInOrderByUpdatedAtDesc(java.util.Collection<Long> userIds);
}
