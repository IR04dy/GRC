package com.tss.grc.module.ethics.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * KYC subject (customer / counter-party / employee) under continuous CDD/EDD
 * + sanctions/PEP screening per SAMA AML §3 and CMA AML rules.
 */
@Entity
@Table(schema = "ethics", name = "kyc_subjects")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class KycSubject {

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

    @Column(name = "subject_code", length = 50)
    private String subjectCode;

    @Column(name = "subject_type", nullable = false, length = 30)
    private String subjectType;        // INDIVIDUAL | LEGAL_ENTITY

    @Column(name = "full_name", nullable = false, length = 500)
    private String fullName;

    @Column(name = "full_name_ar", length = 500)
    private String fullNameAr;

    @Column(name = "nationality", length = 3)
    private String nationality;

    @Column(name = "country_of_residence", length = 3)
    private String countryOfResidence;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "national_id", length = 50)
    private String nationalId;

    @Column(name = "commercial_registration", length = 50)
    private String commercialRegistration;

    @Column(name = "subject_link_type", length = 30)
    private String subjectLinkType;    // VENDOR | EMPLOYEE | CUSTOMER | OTHER

    @Column(name = "subject_link_id")
    private Long subjectLinkId;

    // F2-291 — NO field initialisers on these four, and the absence is the fix.
    //
    // `KycController` binds this entity on POST and PUT, so an omitted key deserialises into
    // `new KycSubject()` and an initialiser is indistinguishable from a value the caller stated.
    // `KycService#apply` copies each of them under `if (s.getX() != null)`, which an initialiser
    // makes true on every request — so the ordinary subject-edit route reset every consequence of a
    // confirmed screening match.
    //
    // 🔑 These are precisely the flags F2-288 armed one slice earlier so that `approve()`'s
    // long-standing "Cannot approve a sanctioned subject" guard could finally fire. Probed live: a
    // confirmed OFAC_SDN TRUE_MATCH set `isSanctioned` + `riskRating = PROHIBITED` and `approve()`
    // correctly answered 400; a PUT carrying only `fullName` then answered 200, left the hit on the
    // register still reading TRUE_MATCH, and `approve()` answered **200**. F2-288's own gate leg
    // records that `apply()` legitimately copies these because the form lets an operator flag a PEP
    // or a sanctioned party by hand — true of a full-echo form, and the initialiser is what made
    // "a legitimate copy" a reset.
    @Column(name = "risk_rating", nullable = false, length = 20)
    private String riskRating; // LOW | STANDARD | HIGH | PROHIBITED

    @Column(name = "is_pep", nullable = false)
    private Boolean isPep;

    @Column(name = "is_sanctioned", nullable = false)
    private Boolean isSanctioned;

    @Column(name = "cdd_level", nullable = false, length = 20)
    private String cddLevel; // SIMPLIFIED | STANDARD | ENHANCED

    @Column(name = "cdd_completed_at")
    private LocalDateTime cddCompletedAt;

    // F2-291 — the fifth of the same set. This is the flag FATF R.12 and SAMA's AML/CFT rules turn
    // on for a PEP, and it was cleared by any edit that did not mention it.
    @Column(name = "edd_required", nullable = false)
    private Boolean eddRequired;

    @Column(name = "edd_completed_at")
    private LocalDateTime eddCompletedAt;

    @Column(name = "last_screened_at")
    private LocalDateTime lastScreenedAt;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    // F2-291 — an omitted status reset an APPROVED or REJECTED subject to PENDING. Unlike the four
    // above, `apply` copying this at all is a separate question (F2-22b(v) moved the ethics case's
    // status out of its `apply` for exactly that reason and this register never got the same
    // treatment) — filed, because a caller who SENDS a status still overwrites the lifecycle, and
    // deciding whether that is legitimate here is not a side effect of removing an initialiser.
    @Column(name = "status", nullable = false, length = 30)
    private String status;
    // PENDING | APPROVED | REJECTED | UNDER_REVIEW | OFFBOARDED

    @Column(name = "framework_id")
    private Long frameworkId;

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

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
