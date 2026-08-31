package com.carelink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "location_shares")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class LocationShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long elderId;

    /** 通用用户 ID（老人或家属均用此字段，取代 elderId 的单一用途） */
    private Long userId;

    /** 用户角色（ELDER / FAMILY），用于区分成员类型 */
    @Column(length = 20)
    private String userRole;

    /** 昵称快照，避免每次查都 join */
    @Column(length = 50)
    private String nickname;

    /** 头像 URL 快照 */
    @Column(length = 500)
    private String avatarUrl;

    private Double latitude;
    private Double longitude;

    @Column(length = 300)
    private String address;

    /** 是否正在共享 */
    @Builder.Default
    private Boolean enabled = true;

    /** 共享截止时间 */
    private LocalDateTime expireAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
