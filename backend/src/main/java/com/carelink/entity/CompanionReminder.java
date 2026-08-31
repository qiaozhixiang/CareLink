package com.carelink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "companion_reminders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class CompanionReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Column(name = "elder_user_id", nullable = false)
    private Long elderUserId;

    @Column(length = 20)
    private String emoji;

    @Column(length = 50)
    private String label;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "sender_name", length = 50)
    private String senderName;

    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
