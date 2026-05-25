package com.tss.grc.module.openbanking.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * OB-specific incident — consent violation, scope creep, cert misuse,
 * data leak via API, TPP-on-TPP fraud, abnormal API pattern.
 *
 * <p>Kept separate from the platform's general incident module because
 * SAMA OBF has its own reporting cadence (incidents materially
 * affecting the OB service must be reported to SAMA's OBF unit within
 * 24 hours under OBF v2 §11.4).
 *
 * <p>Lifecycle:
 * <pre>
 *   OPEN → INVESTIGATING → CONTAINED → RESOLVED → CLOSED
 *                                    ↘ ESCALATED_SAMA (reportable, then back to RESOLVED)
 * </pre>
 */
@Entity
@Table(schema = "openbanking", name = "ob_incident")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ObIncident extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** External reference, unique per tenant. e.g. {@code OBI-2026-001}. */
    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    /** Optional FK to the TPP involved. */
    @Column(name = "tpp_id")
    private Long tppId;

    /**
     * Incident type:
     * {@code CONSENT_VIOLATION | SCOPE_CREEP | CERT_MISUSE |
     *        TPP_ON_TPP_FRAUD | API_ABUSE | DATA_LEAK |
     *        BULK_REVOCATION_FRAUD_SIGNAL | OUTAGE | OTHER}.
     */
    @Column(name = "incident_type", nullable = false, length = 32)
    private String incidentType;

    /** Severity: {@code LOW | MEDIUM | HIGH | CRITICAL}. */
    @Column(name = "severity", nullable = false, length = 12)
    private String severity = "MEDIUM";

    /** Status: {@code OPEN | INVESTIGATING | CONTAINED | RESOLVED | CLOSED}. */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "title_en", nullable = false, length = 256)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 256)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "contained_at")
    private LocalDateTime containedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** SAMA reportable flag — drives the AR-247 24h-window escalation. */
    @Column(name = "sama_reportable")
    private Boolean samaReportable = false;

    /** SAMA notification reference (when reported). */
    @Column(name = "sama_notification_ref", length = 128)
    private String samaNotificationRef;

    @Column(name = "sama_notified_at")
    private LocalDateTime samaNotifiedAt;

    /** Estimated affected customer count. */
    @Column(name = "affected_customer_count")
    private Long affectedCustomerCount;

    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;
}
