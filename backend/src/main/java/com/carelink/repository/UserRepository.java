package com.carelink.repository;

import com.carelink.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByFamilyId(Long familyId);
    Optional<User> findByWechatOpenid(String wechatOpenid);
}
