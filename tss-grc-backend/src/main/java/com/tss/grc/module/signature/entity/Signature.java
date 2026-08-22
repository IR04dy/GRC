package com.tss.grc.module.signature.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * An in-house electronic sign-off (attestation) against any entity.
 *
 * <p>Not PKI/certificate-based non-repudiation — it is a tamper-evident record
 * of <em>who</em> signed <em>what</em>, with what <em>intent</em>, and a
 * SHA-256 {@code payloadHash} binding the signer + entity + intent + timestamp.
 * Polymorphic by ({@code entity_type}, {@code entity_id}) so the same facility
 * serves policy approvals, evidence sign-off, attestations, etc.
 */
@Entity
@Table(schema = "platform", name = "signatures")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Signature {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** What the signature asserts — APPROVE / REVIEW / ACKNOWLEDGE / ATTEST / REJECT. */
    @Column(name = "intent", nullable = false, length = 30)
    private String intent;

    /** The attestation statement the signer agreed to. */
    @Column(name = "statement_en", columnDefinition = "NVARCHAR(MAX)")
    private String statementEn;

    @Column(name = "signer_id", nullable = false)
    private Long signerId;

    /** Name snapshot at signing time (survives later user renames). */
    @Column(name = "signer_name", length = 200)
    private String signerName;

    /**
     * F2-277 — the Arabic half of the same snapshot, from {@code iam.users.full_name_ar}.
     *
     * <p>Nullable and deliberately WITHOUT the username fallback its English sibling carries: the
     * username is language-neutral and already keeps the row identifiable, so putting it here
     * would assert an Arabic name the directory does not have.
     */
    @Column(name = "signer_name_ar", length = 200)
    private String signerNameAr;

    @Column(name = "signer_role", length = 100)
    private String signerRole;

    /** SHA-256 over a canonical payload (entityType|entityId|intent|signerId|signedAt). */
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt = LocalDateTime.now();

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
