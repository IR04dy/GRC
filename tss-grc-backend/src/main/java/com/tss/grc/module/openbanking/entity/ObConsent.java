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
 * One customer consent under SAMA OBF — the customer's authorisation
 * for a TPP to access specific account information or initiate
 * specific payments.
 *
 * <p>Lifecycle:
 * <pre>
 *   AUTHORISED → ACTIVE → EXPIRED
 *                       → REVOKED (by customer or bank)
 * </pre>
 *
 * <p>Per SAMA OBF v2, the default consent lifetime is 180 days for AIS
 * (account-information) consents; PIS (payment-initiation) consents
 * are single-use unless a Variable Recurring Payment (VRP) consent.
 */
@Entity
@Table(schema = "openbanking", name = "ob_consent")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ObConsent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "tpp_id", nullable = false)
    private Long tppId;

    /** External consent reference (sent to the TPP). */
    @Column(name = "consent_reference", nullable = false, length = 96)
    private String consentReference;

    /** Hashed customer identifier (not the raw customer id). */
    @Column(name = "customer_hash", nullable = false, length = 128)
    private String customerHash;

    /** Bilingual customer display label (e.g. "Account ending 4231"). */
    @Column(name = "customer_label_en", length = 256)
    private String customerLabelEn;

    @Column(name = "customer_label_ar", length = 256)
    private String customerLabelAr;

    /**
     * Consent type:
     * {@code AIS_ACCOUNT_INFO | AIS_BALANCES | AIS_TRANSACTIONS |
     *        PIS_PAYMENT | VRP_SETUP | CBPII_CONFIRM_FUNDS}.
     */
    @Column(name = "consent_type", nullable = false, length = 32)
    private String consentType;

    /** Comma-separated scopes (e.g. {@code accounts:read,balances:read}). */
    @Column(name = "scopes", nullable = false, length = 512)
    private String scopes;

    /** Status: {@code AUTHORISED | ACTIVE | EXPIRED | REVOKED}. */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "AUTHORISED";

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /** Who revoked: {@code CUSTOMER | BANK | TPP}. */
    @Column(name = "revoked_by", length = 16)
    private String revokedBy;

    @Column(name = "revocation_reason_en", columnDefinition = "NVARCHAR(MAX)")
    private String revocationReasonEn;

    @Column(name = "revocation_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String revocationReasonAr;

    /** Maximum-frequency / cap-amount metadata for VRP / PIS consents. */
    @Column(name = "amount_cap_sar")
    private java.math.BigDecimal amountCapSar;

    @Column(name = "frequency_window_days")
    private Integer frequencyWindowDays;

    @Column(name = "call_count")
    private Long callCount = 0L;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}
