package com.tss.grc.module.ethics.whistleblower.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One whistleblower / ethics-hotline case — Sprint 13.
 *
 * <p>The {@link #submissionToken} is the ONLY identifier given to the
 * public submitter — they use it on {@code GET /api/v1/ethics/
 * whistleblower/public/status/{token}} to check progress and reply.
 * It cannot be recovered if lost (PDPL data-minimisation +
 * SAMA Art.28 anonymity).
 *
 * <h3>Status machine</h3>
 * <pre>
 *   RECEIVED → TRIAGE → UNDER_INVESTIGATION → SUBSTANTIATED | UNSUBSTANTIATED | REFERRED
 *                                          ↘ CLOSED_NO_ACTION | CLOSED_REMEDIATED
 *   (TRIAGE may also short-circuit to CLOSED_NO_ACTION for clearly out-of-scope reports.)
 * </pre>
 *
 * <p>The {@link #involvesSeniorExecutive} flag fires the
 * {@code WHISTLEBLOWER_SENIOR_EXEC_FLAGGED} F8 trigger on submission
 * and triage so the Ethics Committee Chair + Internal Auditor get
 * notified out-of-band.
 */
@Entity
@Table(schema = "ethics", name = "whistleblower_case")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class WhistleblowerCase extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "case_code", nullable = false, length = 64)
    private String caseCode;

    /** Opaque per-row token the public submitter receives. */
    @Column(name = "submission_token", nullable = false, updatable = false)
    private UUID submissionToken = UUID.randomUUID();

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    /**
     * PUBLIC_PORTAL | INTERNAL_PORTAL | EMAIL | PHONE | IN_PERSON | EXTERNAL_PARTNER.
     */
    @Column(name = "submission_channel", nullable = false, length = 20)
    private String submissionChannel = "PUBLIC_PORTAL";

    /** EMPLOYEE | MANAGER | CONTRACTOR | VENDOR | CUSTOMER | EXTERNAL | ANONYMOUS. */
    @Column(name = "reporter_type", nullable = false, length = 20)
    private String reporterType = "ANONYMOUS";

    @Column(name = "reporter_name", length = 255)
    private String reporterName;

    @Column(name = "reporter_email", length = 255)
    private String reporterEmail;

    @Column(name = "reporter_phone", length = 50)
    private String reporterPhone;

    @Column(name = "reporter_employee_id", length = 64)
    private String reporterEmployeeId;

    @Column(name = "is_anonymous", nullable = false)
    private Boolean isAnonymous = true;

    /**
     * FRAUD | CORRUPTION | CONFLICT_OF_INTEREST | HARASSMENT |
     * DISCRIMINATION | SAFETY | ENVIRONMENT | PRIVACY_VIOLATION |
     * FINANCIAL_MISCONDUCT | IT_SECURITY | REGULATORY_NON_COMPLIANCE | OTHER.
     */
    @Column(name = "category", nullable = false, length = 40)
    private String category;

    /** CRITICAL | HIGH | MEDIUM | LOW. */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "MEDIUM";

    @Column(name = "subject_en", nullable = false, length = 255)
    private String subjectEn;

    @Column(name = "subject_ar", nullable = false, length = 255)
    private String subjectAr;

    @Column(name = "narrative_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String narrativeEn;

    @Column(name = "narrative_ar", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String narrativeAr;

    @Column(name = "parties_involved", length = 1000)
    private String partiesInvolved;

    @Column(name = "alleged_period_start")
    private LocalDate allegedPeriodStart;

    @Column(name = "alleged_period_end")
    private LocalDate allegedPeriodEnd;

    @Column(name = "involves_senior_executive", nullable = false)
    private Boolean involvesSeniorExecutive = false;

    /**
     * RECEIVED | TRIAGE | UNDER_INVESTIGATION | SUBSTANTIATED |
     * UNSUBSTANTIATED | REFERRED | CLOSED_NO_ACTION | CLOSED_REMEDIATED.
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "RECEIVED";

    @Column(name = "triage_by_user_id")
    private Long triageByUserId;

    @Column(name = "triage_at")
    private LocalDateTime triageAt;

    @Column(name = "triage_decision_en", length = 1000)
    private String triageDecisionEn;

    @Column(name = "triage_decision_ar", length = 1000)
    private String triageDecisionAr;

    @Column(name = "assigned_investigator_user_id")
    private Long assignedInvestigatorUserId;

    @Column(name = "investigation_started_at")
    private LocalDateTime investigationStartedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closure_summary_en", length = 2000)
    private String closureSummaryEn;

    @Column(name = "closure_summary_ar", length = 2000)
    private String closureSummaryAr;

    @Column(name = "linked_capa_task_id")
    private Long linkedCapaTaskId;
}
