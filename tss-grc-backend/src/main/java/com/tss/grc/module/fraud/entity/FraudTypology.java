package com.tss.grc.module.fraud.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * Codified fraud typology catalog — Sprint 14.
 *
 * <p>Seeded with Saudi-bank-relevant typologies (Business Email
 * Compromise, ATM skimming, SADAD/IPS push-payment fraud, account
 * takeover, internal embezzlement, card-not-present fraud, etc.).
 * Each typology carries a {@link #samaCategoryCode} that maps to the
 * SAMA Anti-Fraud Framework 2018 reporting taxonomy so quarterly
 * regulator returns can roll up by category.
 *
 * <p>{@code organisationId == null} for catalog rows shared across
 * all tenants; tenant-specific extensions set {@code organisationId}.
 */
@Entity
@Table(schema = "fraud", name = "fraud_typology")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class FraudTypology extends BaseEntity {

    /** {@code null} → shared catalog row; non-null → tenant extension. */
    @Column(name = "organisation_id")
    private Long organisationId;

    /** Stable code e.g. {@code BEC}, {@code ATM_SKIMMING}, {@code PUSH_PAYMENT}. */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name_en", nullable = false, length = 256)
    private String nameEn;

    @Column(name = "name_ar", nullable = false, length = 256)
    private String nameAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /**
     * SAMA Anti-Fraud Framework 2018 reporting category —
     * {@code EXTERNAL | INTERNAL | CYBER | CARD | PAYMENT | OTHER}.
     */
    @Column(name = "sama_category_code", nullable = false, length = 32)
    private String samaCategoryCode;

    /** {@code EXTERNAL} or {@code INTERNAL} — perpetrator origin. */
    @Column(name = "perpetrator_origin", nullable = false, length = 16)
    private String perpetratorOrigin;

    /** {@code RETAIL | CORPORATE | TREASURY | OPERATIONS | IT | CARDS} — typical channel. */
    @Column(name = "primary_channel", nullable = false, length = 32)
    private String primaryChannel;

    /** {@code CRITICAL | HIGH | MEDIUM | LOW} — typical severity for this typology. */
    @Column(name = "typical_severity", nullable = false, length = 16)
    private String typicalSeverity = "MEDIUM";

    /** Whether this typology typically requires a SAFIU STR filing. */
    @Column(name = "requires_str_filing", nullable = false)
    private Boolean requiresStrFiling = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
