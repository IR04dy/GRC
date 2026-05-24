package com.tss.grc.module.audit_committee.dashboard.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One Committee member's signed attestation against a
 * {@link DashboardSnapshot} — Sprint 11.
 *
 * <p>{@link #signatureHash} is a SHA-256 hex digest of
 * {@code attestation_text || user_id || ISO-8601(timestamp)} — the
 * tamper-evident proof an external auditor uses to verify the row
 * wasn't rewritten after the meeting.
 *
 * <p>SAMA Corporate Governance Regulations §6 expects the Audit
 * Committee's quarterly minutes to carry per-member attestations on
 * whether the reported posture (risk, controls, compliance, findings)
 * is fairly stated. This entity is the queryable artefact of that
 * attestation.
 */
@Entity
@Table(schema = "audit_committee", name = "dashboard_attestation")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DashboardAttestation extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;

    @Column(name = "attested_by_user_id", nullable = false)
    private Long attestedByUserId;

    /**
     * CHAIR | MEMBER | AUDIT_DIRECTOR | EXTERNAL_AUDITOR | COMPANY_SECRETARY.
     */
    @Column(name = "attested_role", nullable = false, length = 40)
    private String attestedRole;

    @Column(name = "attestation_text_en", length = 2000)
    private String attestationTextEn;

    @Column(name = "attestation_text_ar", length = 2000)
    private String attestationTextAr;

    @Column(name = "attested_at", nullable = false)
    private LocalDateTime attestedAt = LocalDateTime.now();

    /** SHA-256(text || user_id || ISO-8601(timestamp)). */
    @Column(name = "signature_hash", length = 255)
    private String signatureHash;
}
