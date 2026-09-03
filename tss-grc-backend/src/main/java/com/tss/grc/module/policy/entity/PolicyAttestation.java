package com.tss.grc.module.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Per-user attestation of having read + accepted a policy.
 *
 * <p>Status: PENDING → ATTESTED | DECLINED.
 *
 * <p>⚠ This said "EXPIRED applies when the underlying policy version is superseded", and that
 * lifecycle does not exist. {@code EXPIRED} is in the attestation vocabulary and
 * {@code PolicyManagementService}'s attestation summary counts it, but <b>no code path ever
 * writes it</b> — measured across the tree, and 0 rows sit at EXPIRED in either the dev or the
 * deployed database. So an attestation of a superseded policy version still reads as current,
 * and the "expired" figure on the summary is permanently zero. Building the supersede pass is a
 * feature rather than a bug fix, so it is filed rather than smuggled in; the point of correcting
 * the sentence is that it answered the question a reader was about to ask (F2-184).
 *
 * <p>{@code policyVersion} is a COPY of the resolved document's own {@code version} —
 * {@code policy.documents} holds one row per version, so {@code policyId} already identifies
 * one. It is derived on the create path and is not the caller's to choose (F2-453). Nothing
 * reads it today, which is exactly why the claim above went unnoticed.
 */
@Entity
@Table(schema = "policy", name = "attestations")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class PolicyAttestation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "policy_id", nullable = false) private Long policyId;
    @Column(name = "policy_version", length = 20) private String policyVersion;

    @Column(name = "user_username", nullable = false, length = 120) private String userUsername;
    @Column(name = "user_full_name", length = 255)                  private String userFullName;
    @Column(name = "user_role", length = 80)                        private String userRole;
    @Column(name = "user_department", length = 120)                 private String userDepartment;

    @Column(nullable = false, length = 20) private String status;
    @Column(name = "attested_at")          private LocalDateTime attestedAt;
    @Column(name = "declined_at")          private LocalDateTime declinedAt;
    @Column(name = "declined_reason", columnDefinition = "NVARCHAR(MAX)") private String declinedReason;

    @Column(name = "evidence_ref", length = 255) private String evidenceRef;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null)    status = "PENDING";
        if (isDeleted == null) isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
