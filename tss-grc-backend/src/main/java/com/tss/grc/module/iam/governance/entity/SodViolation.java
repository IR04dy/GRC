package com.tss.grc.module.iam.governance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * RBAC Access-Governance P2 (plan §6.4) — one detected SoD violation with its
 * disposition workflow: {@code OPEN → ACCEPTED_RISK (accepted_until mandatory) |
 * REMEDIATED}. {@code detectionSource} = SCAN (detective) or OVERRIDE (a grant
 * approved past the preventive check with a documented justification).
 */
@Entity
@Table(schema = "iam", name = "sod_violations")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SodViolation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "rule_id", nullable = false) private Long ruleId;
    @Column(name = "rule_code", nullable = false, length = 20) private String ruleCode;
    @Column(nullable = false, length = 10) private String severity;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 100) private String username;
    @Column(name = "detail_en", length = 500) private String detailEn;
    /** SCAN | OVERRIDE. */
    @Column(name = "detection_source", nullable = false, length = 15) private String detectionSource = "SCAN";
    /** OPEN | ACCEPTED_RISK | REMEDIATED. */
    @Column(nullable = false, length = 20) private String status = "OPEN";
    @Column(name = "detected_at", nullable = false) private LocalDateTime detectedAt = LocalDateTime.now();
    @Column(name = "disposition_by") private Long dispositionBy;
    @Column(name = "disposition_at") private LocalDateTime dispositionAt;
    @Column(name = "disposition_note", length = 1000) private String dispositionNote;
    @Column(name = "accepted_until") private LocalDate acceptedUntil;
    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
