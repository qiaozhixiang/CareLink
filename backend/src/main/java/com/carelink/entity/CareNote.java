package com.carelink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "care_notes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class CareNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long elderId;

    private Long authorId;

    @Column(length = 1000)
    private String content;

    @Column(length = 200)
    private String tags;

    /** 是否重要 0=普通 1=重要 */
    @Builder.Default
    private Integer isImportant = 0;

    @Column(length = 500)
    private String imageUrl;

    @CreatedDate
    private LocalDateTime createdAt;
}
