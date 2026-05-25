package com.tss.grc.module.sharia.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sharia non-compliance event — when a transaction or income stream
 * is found, after the fact, to have breached Sharia rulings.
 *
 * <p>The unrecovered earnings ("Sharia non-compliance income", SNCI)
 * must be:
 * <ol>
 *   <li>Calculated separately from operating revenue</li>
 *   <li>Segregated from shareholder profit</li>
 *   <li>Purified — donated to a charitable purpose approved by the SSB,
 *       within 90 days of detection per SAMA §6.4 + AAOIFI FAS 1</li>
 * </ol>
 *
 * <p>Lifecycle:
 * <pre>
 *   DETECTED → INVESTIGATION → INCOME_PURIFIED → CLOSED
 * </pre>
 */
@Entity
@Table(schema = "sharia", name = "sharia_nc_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ShariaNonComplianceEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** External reference, e.g. {@code SNCI-2026-012}. */
    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    /** Status: {@code DETECTED | INVESTIGATION | INCOME_PURIFIED | CLOSED}. */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "DETECTED";

    /** Severity: {@code HIGH | MEDIUM | LOW}. */
    @Column(name = "severity", nullable = false, length = 12)
    private String severity = "MEDIUM";

    @Column(name = "title_en", nullable = false, length = 512)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 512)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** Linked product line. */
    @Column(name = "product_line", length = 128)
    private String productLine;

    /** Linked resolution that was breached, if any. */
    @Column(name = "linked_resolution_id")
    private Long linkedResolutionId;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    /** Detection source: {@code AUDIT | SSB_REVIEW | CUSTOMER_COMPLAINT | OPERATIONS | EXTERNAL}. */
    @Column(name = "detection_source", length = 32)
    private String detectionSource;

    /** SNCI amount to purify (SAR). */
    @Column(name = "snci_amount_sar", precision = 18, scale = 2,
            columnDefinition = "DECIMAL(18, 2)")
    private BigDecimal snciAmountSar;

    /** Purification destination (charity name + reference). */
    @Column(name = "purification_destination", length = 256)
    private String purificationDestination;

    /** Date the SNCI was actually transferred to the charity. */
    @Column(name = "purified_at")
    private LocalDate purifiedAt;

    /** Charity receipt reference. */
    @Column(name = "charity_receipt_ref", length = 128)
    private String charityReceiptRef;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Number of customers affected (for transparency to customers, e.g. profit-share recalculation). */
    @Column(name = "affected_customer_count")
    private Long affectedCustomerCount;

    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;
}
