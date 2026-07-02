package com.tss.grc.module.sust.vendor.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Vendor ESG surveillance record (V256 — S-O). Promotes the frontend-only
 * "Vendor ESG survey" mock to a real, tenant-scoped, auditable workflow bound
 * to the REAL vendor register ({@code tprm.vendors}).
 *
 * <p>Status machine:
 * <pre>
 *   DRAFT --send--> SENT --record-response--> RESPONDED --accept--> ACCEPTED
 *                    |                            |
 *                    | (past due, swept)          +--reject--> REJECTED
 *                    +--sweep-overdue--> ESCALATED
 * </pre>
 *
 * <p>Both {@code ESCALATED} (non-response) and {@code REJECTED} open a
 * {@code sust.corrective_action} (VENDOR_ESG source) via the S-D auto-CAPA
 * loop, back-linked here as {@code linkedCapaId}. Vendor-supplied ESG data is
 * collected through the generic evidence engine — {@code evidenceItemId} is a
 * real FK into {@code evidence.evidence_items} (S-C), so it carries a SHA-256 +
 * review/approve lifecycle. {@code esgScore} is always COMPUTED (never bound
 * from the request).
 */
@Entity
@Table(schema = "sust", name = "vendor_esg_survey")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class VendorEsgSurvey {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)           private String externalId;

    /** FK into the real vendor register — validated same-tenant in the service. */
    @Column(name = "tprm_vendor_id", nullable = false) private Long tprmVendorId;
    /** Optional secondary link to a module/scm supplier. */
    @Column(name = "scm_supplier_id") private Long scmSupplierId;
    @Column(name = "vendor_name_en", nullable = false, length = 255) private String vendorNameEn;
    @Column(name = "vendor_name_ar", length = 255)                   private String vendorNameAr;

    @Column(name = "reporting_year", nullable = false) private Short reportingYear;

    @Column(nullable = false, length = 30) private String status = "DRAFT";

    @Column(name = "spend_sar_k", precision = 18, scale = 2) private BigDecimal spendSarK;

    @Column(name = "due_date")     private LocalDate dueDate;
    @Column(name = "sent_at")      private LocalDateTime sentAt;
    @Column(name = "responded_at") private LocalDateTime respondedAt;
    @Column(name = "reviewed_at")  private LocalDateTime reviewedAt;

    // ── Vendor-reported ESG data (populated on record-response) ──────────
    @Column(name = "reported_scope12", precision = 18, scale = 2) private BigDecimal reportedScope12;
    @Column(name = "renewable_pct")       private Integer renewablePct;
    @Column(name = "saudization_pct")     private Integer saudizationPct;
    @Column(name = "has_net_zero_target") private Boolean hasNetZeroTarget;
    @Column(name = "iso14001")            private Boolean iso14001;

    /** COMPUTED 0..100 — derived from the reported data, never bound. */
    @Column(name = "esg_score", precision = 5, scale = 2) private BigDecimal esgScore;

    /** Real evidence FK (S-C) — the vendor's uploaded ESG data package. */
    @Column(name = "evidence_item_id") private Long evidenceItemId;
    /** Auto-CAPA back-link (S-D) opened on escalation / rejection. */
    @Column(name = "linked_capa_id") private Long linkedCapaId;

    @Column(name = "reviewer_name", length = 255)  private String reviewerName;
    @Column(name = "reviewer_email", length = 255) private String reviewerEmail;
    @Column(name = "reject_reason", columnDefinition = "NVARCHAR(MAX)") private String rejectReason;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
