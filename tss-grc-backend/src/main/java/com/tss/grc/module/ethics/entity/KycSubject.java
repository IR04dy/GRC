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

    @Column(name = "risk_rating", nullable = false, length = 20)
    private String riskRating = "STANDARD"; // LOW | STANDARD | HIGH | PROHIBITED

    @Column(name = "is_pep", nullable = false)
    private Boolean isPep = false;

    @Column(name = "is_sanctioned", nullable = false)
    private Boolean isSanctioned = false;

    @Column(name = "cdd_level", nullable = false, length = 20)
    private String cddLevel = "STANDARD"; // SIMPLIFIED | STANDARD | ENHANCED

    @Column(name = "cdd_completed_at")
    private LocalDateTime cddCompletedAt;

    @Column(name = "edd_required", nullable = false)
    private Boolean eddRequired = false;

    @Column(name = "edd_completed_at")
    private LocalDateTime eddCompletedAt;

    @Column(name = "last_screened_at")
    private LocalDateTime lastScreenedAt;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";
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
