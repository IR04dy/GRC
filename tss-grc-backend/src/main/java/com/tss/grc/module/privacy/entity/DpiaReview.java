package com.tss.grc.module.privacy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(schema = "privacy", name = "dpia_review")
@Getter @Setter @NoArgsConstructor
public class DpiaReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dpia_id", nullable = false)
    private Long dpiaId;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewer_name", length = 255)
    private String reviewerName;

    /** APPROVE | REJECT | REQUEST_CHANGES */
    @Column(name = "decision", nullable = false, length = 30)
    private String decision;

    @Column(name = "comments", columnDefinition = "NVARCHAR(MAX)")
    private String comments;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt = LocalDateTime.now();
}
