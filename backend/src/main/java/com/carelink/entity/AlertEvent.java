package com.carelink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "alert_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long elderId;

    /** LOW_STAY / FALL / ABNORMAL_VITAL / MISSING_MEDICINE / SOS / CUSTOM */
    @Column(length = 30)
    private String alertType;

    @Column(length = 500)
    private String description;

    /** 1=普通 2=重要 3=紧急 */
    @Builder.Default
    private Integer level = 1;

    /** PENDING / HANDLED / IGNORED */
    @Column(length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** 处理该事件的家属用户 ID */
    private Long assignedTo;

    /** 处理备注 */
    @Column(length = 300)
    private String handleNote;

    private Long handledAt;

    @CreatedDate
    private LocalDateTime createdAt;
}
