package com.carelink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "checkin_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class CheckinRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long elderId;

    private Long taskId;

    private String title;

    /** 实际完成时间 */
    private LocalDateTime completedAt;

    /** 完成状态 DONE / SKIP / MISSED */
    @Column(length = 20)
    @Builder.Default
    private String status = "DONE";

    /** 家属备注 */
    @Column(length = 300)
    private String note;

    @CreatedDate
    private LocalDateTime createdAt;
}
