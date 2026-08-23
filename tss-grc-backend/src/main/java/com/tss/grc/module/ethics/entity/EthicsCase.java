package com.tss.grc.module.ethics.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    // F2-286 — the column is insertable = false, so the DATABASE refuses a caller's value
    // and the create hands back the instance Jackson bound: without this the response
    // echoed a value the row does not carry, and only a re-fetch showed the truth. This
    // says the mapping refuses it, not that the caller should never state it — where the
    // caller should, the fix is to make the column insertable (F2-286 did that for
    // ethics.screening_hits.screened_at, whose value nothing could ever state).
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
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

    // F2-291 — NO field initialiser here, and the absence is the fix.
    //
    // `EthicsCaseController` binds this entity on POST and PUT, so Jackson deserialises an omitted
    // key into `new EthicsCase()` and an initialiser is indistinguishable from a value the caller
    // stated. `EthicsCaseService#apply` copies each of these under `if (src.getX() != null)`, which
    // an initialiser makes true on every request — so a partial PUT that mentioned only the title
    // reset the severity to MEDIUM, rewrote the intake channel as HOTLINE and cleared
    // `isAnonymous`. Probed live: a CRITICAL, in-person, ANONYMOUS whistleblowing case came back
    // MEDIUM / HOTLINE / attributable, HTTP 200. The defaults live on the create path instead
    // (F2-238's remedy), where `NeverFiringNullGuardContractTest` asserts a writer exists for each.
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "intake_channel", nullable = false, length = 40)
    private String intakeChannel;

    @Column(name = "reporter_name", length = 255)
    private String reporterName;

    @Column(name = "reporter_email", length = 255)
    private String reporterEmail;

    @Column(name = "reporter_employee_id")
    private Long reporterEmployeeId;

    // F2-291 — the sharpest member of that set: on a whistleblowing register this flag is what
    // protects the reporter, and `= false` made an omitted key read as a deliberate
    // de-anonymisation. The reporter's name and email columns sit two fields above.
    @Column(name = "is_anonymous", nullable = false)
    private Boolean isAnonymous;

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

    // F2-291 — this one was not a reset, it was a REFUSAL. `apply` never copies `status` (F2-22b(v)
    // moved it to transition()), but `requireLifecycleIsNotBodySet` compares the body's value
    // against the stored one — and with `= "INTAKE"` a body that omitted `status` looked like a
    // request to move the case BACK to INTAKE. Probed live: a partial PUT on a case at TRIAGE
    // answered 400 "Case 50 is at TRIAGE and cannot be moved to INTAKE by an update", naming a
    // state the caller never sent. That refusal is also what held the three resets above shut for
    // the 28 of 30 live cases past INTAKE — so fixing it is what would otherwise have detonated
    // them (F2-203). `create()` owns the initial state.
    @Column(name = "status", nullable = false, length = 40)
    private String status;

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
