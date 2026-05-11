package com.tss.grc.module.signature.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Digital signature transaction — Saudi GRC prompt §28.
 *
 * <p>Binds a signer to a specific record (board pack, risk acceptance,
 * BCP, supplier attestation, regulatory filing, evidence packet). The
 * persistent audit trail proves who approved what, when, and which
 * document hash they signed against — required for regulatory evidence
 * packages (NCA / PDPL / SAMA / DGA).
 *
 * <p>State machine: REQUESTED → SIGNED | REJECTED | EXPIRED.
 */
@Entity
@Table(schema = "signature", name = "digital_signature_transactions")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DigitalSignatureTransaction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "related_entity_type", nullable = false, length = 50)
    private String relatedEntityType;

    @Column(name = "related_entity_id", nullable = false)
    private Long relatedEntityId;

    @Column(name = "related_entity_label", length = 255)
    private String relatedEntityLabel;

    @Column(name = "signer_user_id")
    private Long signerUserId;

    @Column(name = "signer_name", nullable = false, length = 255)
    private String signerName;

    @Column(name = "signer_email", length = 255)
    private String signerEmail;

    @Column(name = "signer_role", length = 100)
    private String signerRole;

    @Column(name = "initiator_user_id")
    private Long initiatorUserId;

    @Column(name = "initiator_name", length = 255)
    private String initiatorName;

    /** INTERNAL (in-platform crypto), DOCUSIGN, ABSHER, NAFATH, OTHER. */
    @Column(name = "signature_provider", nullable = false, length = 50)
    private String signatureProvider = "INTERNAL";

    @Column(name = "purpose", nullable = false, length = 500)
    private String purpose;

    @Column(name = "purpose_ar", length = 500)
    private String purposeAr;

    /** sha256 hex of the signed document; filled when status moves to SIGNED. */
    @Column(name = "signed_document_hash", length = 64)
    private String signedDocumentHash;

    @Column(name = "certificate_reference", length = 255)
    private String certificateReference;

    /** REQUESTED, SIGNED, REJECTED, EXPIRED. */
    @Column(nullable = false, length = 20)
    private String status = "REQUESTED";

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "signature_timestamp")
    private LocalDateTime signatureTimestamp;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    // ─── BaseEntity audit fields (mirrors approvals.requests) ─────────
    @Column(name = "external_id", length = 64)
    private String externalId;

    @Column(name = "is_deleted", nullable = false)
    private Short isDeleted = 0;

    @Column(name = "is_active", nullable = false)
    private Short isActive = 1;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "row_version", nullable = false)
    private Long rowVersion = 0L;

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
