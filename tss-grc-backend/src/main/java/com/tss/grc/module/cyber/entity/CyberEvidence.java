package com.tss.grc.module.cyber.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(schema = "cyber", name = "cyber_evidence")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CyberEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "title_en")
    private String titleEn;

    /** CY-J — bilingual parity (every other cyber aggregate already carries title_ar). */
    @Column(name = "title_ar")
    private String titleAr;

    @Column(name = "evidence_type")
    private String evidenceType;

    @Column(name = "control_id")
    private Long controlId;

    @Column(name = "assessment_id")
    private Long assessmentId;

    @Column(name = "framework_requirement")
    private String frameworkRequirement;

    @Column(name = "owner_name")
    private String ownerName;

    private String version;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** CY-J — when the expiry sweep flipped this row to STALE (null while CURRENT). */
    @Column(name = "stale_since")
    private LocalDate staleSince;

    /** CY-J — the evidence-request the sweep auto-raised on expiry (traceability). */
    @Column(name = "re_request_id")
    private Long reRequestId;

    @Column(nullable = false)
    private String status = "CURRENT";

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (externalId == null) externalId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
