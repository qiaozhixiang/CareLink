package com.carelink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "checkin_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class CheckinTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long elderId;

    private String title;

    /** MEDICINE / EXERCISE / MEAL / SLEEP / CUSTOM */
    @Column(length = 20)
    private String category;

    /** 期望完成时间（HH:mm 格式） */
    @Column(length = 10)
    private String expectedTime;

    /** 是否启用 */
    @Builder.Default
    private Boolean active = true;

    @CreatedDate
    private LocalDateTime createdAt;
}
