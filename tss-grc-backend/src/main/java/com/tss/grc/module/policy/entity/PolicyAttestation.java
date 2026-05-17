package com.tss.grc.module.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Per-user attestation of having read + accepted a policy.
 *
 * <p>Status: PENDING → ATTESTED | DECLINED. EXPIRED applies when the
 * underlying policy version is superseded.
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
