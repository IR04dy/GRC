package com.tss.grc.module.privacy.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Consent record — PDPL Article 5 ledger entry.
 *
 * <p>One row per (data subject × purpose) consent grant. Pseudonymous
 * subject reference only — we never store raw PII identifiers here; the
 * upstream collection point hashes / pseudonymises before writing.
 *
 * <p>Lifecycle: ACTIVE → WITHDRAWN (user withdraws), ACTIVE → EXPIRED
 * (valid_until elapses), ACTIVE → SUPERSEDED (a new version replaces it).
 * Withdrawal is non-reversible — withdraw + grant a new record instead.
 */
@Entity
@Table(schema = "privacy", name = "consent_records")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ConsentRecord extends BaseEntity {

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** Pseudonymous identifier (e.g. SHA-256 of citizen ID + salt). Never raw PII. */
    @Column(name = "data_subject_ref", nullable = false, length = 255)
    private String dataSubjectRef;

    @Column(name = "purpose_code", nullable = false, length = 100)
    private String purposeCode;

    @Column(name = "purpose_description", length = 1000)
    private String purposeDescription;

    /** CONSENT | LEGITIMATE_INTEREST | CONTRACT | LEGAL_OBLIGATION | VITAL_INTEREST | PUBLIC_TASK */
    @Column(name = "legal_basis", nullable = false, length = 50)
    private String legalBasis = "CONSENT";

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt = LocalDateTime.now();

    /** WEB_FORM | PAPER | IN_PERSON | API | EMAIL | OTHER */
    @Column(name = "granted_via", nullable = false, length = 50)
    private String grantedVia = "WEB_FORM";

    /** Null = open-ended; subject must explicitly withdraw. */
    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "withdrawn_reason", length = 500)
    private String withdrawnReason;

    /** Soft FK to evidence.evidence_items — the proof-of-consent artefact. */
    @Column(name = "evidence_id")
    private Long evidenceId;

    @Column(name = "version", nullable = false, length = 20)
    private String version = "v1";

    /** Language of the consent text shown to the subject — typically 'ar' or 'en'. */
    @Column(name = "language", nullable = false, length = 10)
    private String language = "ar";

    /** ACTIVE | EXPIRED | WITHDRAWN | SUPERSEDED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";
}
