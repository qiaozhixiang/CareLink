package com.carelink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String password; // BCrypt 加密存储

    @Column(length = 50)
    private String nickname;

    /** ELDER / FAMILY */
    @Column(length = 20)
    private String role;

    /** 头像 URL */
    @Column(length = 500)
    private String avatarUrl;

    /** 紧急联系人姓名 */
    @Column(length = 50)
    private String emergencyContactName;

    /** 紧急联系人电话 */
    @Column(length = 20)
    private String emergencyContactPhone;

    /** 关联家庭 ID（可为 null） */
    private Long familyId;

    /** 角色选择时间（1个月内不能切换） */
    private Long roleSelectedAt;

    /** 邮箱是否已验证 */
    @Builder.Default
    private Boolean emailVerified = false;

    /** 微信 OpenID（用于微信登录） */
    @Column(length = 100)
    private String wechatOpenid;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
