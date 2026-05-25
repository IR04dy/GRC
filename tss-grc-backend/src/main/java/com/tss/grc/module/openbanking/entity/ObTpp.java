package com.tss.grc.module.openbanking.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Third-Party Provider (TPP) registered with the bank under SAMA's
 * Open Banking Framework.
 *
 * <p>TPPs hold one or more roles:
 * <ul>
 *   <li>AISP — Account Information Service Provider (read access)</li>
 *   <li>PISP — Payment Initiation Service Provider</li>
 *   <li>CBPII — Card-Based Payment Instrument Issuer (confirmation
 *       of funds)</li>
 * </ul>
 *
 * <p>Lifecycle:
 * <pre>
 *   PENDING_KYB → ACTIVE → SUSPENDED → REVOKED
 *                  ↑         ↓
 *                  └─ reinstate
 * </pre>
 */
@Entity
@Table(schema = "openbanking", name = "ob_tpp")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ObTpp extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** External reference, unique per tenant. e.g. {@code TPP-LEAN}. */
    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    @Column(name = "legal_name_en", nullable = false, length = 256)
    private String legalNameEn;

    @Column(name = "legal_name_ar", nullable = false, length = 256)
    private String legalNameAr;

    /** SAMA Open Banking licence reference. */
    @Column(name = "sama_licence_ref", length = 64)
    private String samaLicenceRef;

    /** FAPI client_id used at the bank's OAuth/OIDC endpoint. */
    @Column(name = "fapi_client_id", length = 128)
    private String fapiClientId;

    /** Comma-separated roles: {@code AISP,PISP,CBPII}. */
    @Column(name = "roles", nullable = false, length = 32)
    private String roles;

    /** Country of incorporation (SA, AE, GB, etc.). */
    @Column(name = "country", length = 2)
    private String country;

    /** Status: {@code PENDING_KYB | ACTIVE | SUSPENDED | REVOKED}. */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "PENDING_KYB";

    @Column(name = "onboarded_at")
    private LocalDate onboardedAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "suspension_reason_en", columnDefinition = "NVARCHAR(MAX)")
    private String suspensionReasonEn;

    @Column(name = "suspension_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String suspensionReasonAr;

    /** FAPI 2.0 signing certificate fingerprint. */
    @Column(name = "cert_fingerprint", length = 128)
    private String certFingerprint;

    /** Certificate expiry date — used for the 90/30/7-day reminder ladder. */
    @Column(name = "cert_expires_at")
    private LocalDate certExpiresAt;

    /** Rate limit (calls/sec) negotiated with the TPP. */
    @Column(name = "rate_limit_per_sec")
    private Integer rateLimitPerSec;

    /** Account manager / RM. */
    @Column(name = "account_manager_user_id")
    private Long accountManagerUserId;

    /** Compliance officer assigned to this TPP. */
    @Column(name = "compliance_officer_user_id")
    private Long complianceOfficerUserId;

    // Denormalised counters.
    @Column(name = "active_consent_count")
    private Integer activeConsentCount = 0;

    @Column(name = "open_incident_count")
    private Integer openIncidentCount = 0;
}
