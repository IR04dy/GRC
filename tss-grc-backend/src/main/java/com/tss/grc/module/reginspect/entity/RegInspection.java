package com.tss.grc.module.reginspect.entity;

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
 * One regulatory on-site inspection.
 *
 * <p>Workspace for the team responding to a SAMA / NCA / CMA / SDAIA visit.
 * Lifecycle:
 * <pre>
 *   PLANNED → ENTRY_MEETING → FIELDWORK → EXIT_MEETING →
 *   RESPONSE_DRAFTING → CLOSED
 * </pre>
 * The state machine matches how on-site inspections actually run in Saudi
 * banks — notification letter arrives weeks ahead (PLANNED), then the
 * inspector formally opens (ENTRY_MEETING), document requests + interviews
 * fill the fieldwork window, then a provisional close-out (EXIT_MEETING)
 * followed by drafting + submitting management responses, finally formal
 * closure when the regulator's final letter lands.
 */
@Entity
@Table(schema = "reginspect", name = "reginspect_inspection")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RegInspection extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** External reference (e.g. "SAMA-OSI-2026-Q2-001"). Unique per tenant. */
    @Column(name = "external_reference", nullable = false, length = 64)
    private String externalReference;

    /**
     * Regulator code — {@code SAMA | NCA | CMA | SDAIA | NCEMA | OTHER}.
     * Drives F8 routing rules (e.g. SAMA → CRO; NCA → CISO).
     */
    @Column(name = "regulator", nullable = false, length = 16)
    private String regulator;

    @Column(name = "title_en", nullable = false, length = 256)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 256)
    private String titleAr;

    /**
     * State:
     * {@code PLANNED | ENTRY_MEETING | FIELDWORK | EXIT_MEETING |
     *        RESPONSE_DRAFTING | CLOSED}.
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status = "PLANNED";

    /** Notification letter received date. */
    @Column(name = "notified_at")
    private LocalDate notifiedAt;

    /** Planned / actual entry-meeting date. */
    @Column(name = "entry_date")
    private LocalDate entryDate;

    /** Planned / actual exit-meeting date. */
    @Column(name = "exit_date")
    private LocalDate exitDate;

    /** Final closure date (post-response acceptance). */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Lead inspector(s), free text (e.g. "أ. عبدالله الحربي + فريق ساما"). */
    @Column(name = "lead_inspector_name", length = 256)
    private String leadInspectorName;

    /** Scope summary in English (e.g. "Cyber + AML + Op-Res posture"). */
    @Column(name = "scope_en", columnDefinition = "NVARCHAR(MAX)")
    private String scopeEn;

    /** Scope summary in Arabic. */
    @Column(name = "scope_ar", columnDefinition = "NVARCHAR(MAX)")
    private String scopeAr;

    /** Internal coordinator (typically Compliance Officer). */
    @Column(name = "coordinator_user_id")
    private Long coordinatorUserId;

    /** Sponsor (typically CRO or Board Secretary). */
    @Column(name = "sponsor_user_id")
    private Long sponsorUserId;

    /** Executive summary, written at closure. */
    @Column(name = "executive_summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String executiveSummaryEn;

    @Column(name = "executive_summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String executiveSummaryAr;

    /** Count of document requests open + submitted — denormalised for dashboards. */
    @Column(name = "open_dr_count")
    private Integer openDrCount = 0;

    @Column(name = "overdue_dr_count")
    private Integer overdueDrCount = 0;

    @Column(name = "submitted_dr_count")
    private Integer submittedDrCount = 0;
}
