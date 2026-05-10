package com.tss.grc.module.ethics.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Whistleblower / hotline case + investigation. Reporter PII columns are
 * sensitive and access should be gated via dedicated permission keys —
 * controllers enforce ETHICS:READ / WRITE / INVESTIGATE.
 */
@Entity
@Table(schema = "ethics", name = "cases")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EthicsCase {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", insertable = false, updatable = false)
    private UUID externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "case_code", length = 50)
    private String caseCode;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "MEDIUM";

    @Column(name = "intake_channel", nullable = false, length = 40)
    private String intakeChannel = "HOTLINE";

    @Column(name = "reporter_name", length = 255)
    private String reporterName;

    @Column(name = "reporter_email", length = 255)
    private String reporterEmail;

    @Column(name = "reporter_employee_id")
    private Long reporterEmployeeId;

    @Column(name = "is_anonymous", nullable = false)
    private Boolean isAnonymous = false;

    @Column(name = "subject_summary", columnDefinition = "NVARCHAR(MAX)")
    private String subjectSummary;

    @Column(name = "description", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "date_of_incident")
    private LocalDate dateOfIncident;

    @Column(name = "framework_id")
    private Long frameworkId;

    @Column(name = "clause_ref", length = 100)
    private String clauseRef;

    @Column(name = "status", nullable = false, length = 40)
    private String status = "INTAKE";

    @Column(name = "triage_decision", length = 30)
    private String triageDecision;

    @Column(name = "investigator_name", length = 255)
    private String investigatorName;

    @Column(name = "investigator_id")
    private Long investigatorId;

    @Column(name = "committee_name", length = 255)
    private String committeeName;

    @Column(name = "triaged_at")
    private LocalDateTime triagedAt;

    @Column(name = "investigation_started")
    private LocalDateTime investigationStarted;

    @Column(name = "investigation_ended")
    private LocalDateTime investigationEnded;

    @Column(name = "outcome", length = 40)
    private String outcome;

    @Column(name = "outcome_summary", columnDefinition = "NVARCHAR(MAX)")
    private String outcomeSummary;

    @Column(name = "regulator_notified", nullable = false)
    private Boolean regulatorNotified = false;

    @Column(name = "regulator_reference", length = 100)
    private String regulatorReference;

    @Column(name = "retaliation_check_at")
    private LocalDateTime retaliationCheckAt;

    @Column(name = "retaliation_observed")
    private Boolean retaliationObserved;

    @Column(name = "closure_date")
    private LocalDate closureDate;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
