package com.carelink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联老人用户 ID */
    private Long elderId;

    private String title;

    /** HOSPITAL / CLINIC / CHECK / MEDICINE / CUSTOM */
    @Column(length = 20)
    private String category;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Column(length = 200)
    private String location;

    @Column(length = 500)
    private String notes;

    /** 提醒类型：ONCE / DAILY / WEEKLY / MONTHLY */
    @Column(length = 20)
    private String reminderType;

    /** 提醒分钟数（提前） */
    private Integer remindBefore;

    /** 0=待完成 1=已完成 2=已取消 */
    @Builder.Default
    private Integer status = 0;

    /** 创建者用户 ID */
    private Long createdBy;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
