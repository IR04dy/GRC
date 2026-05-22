package com.tss.grc.module.admin.committee.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Skills matrix — one row per member per skill dimension. Drives gap
 * analysis ("does the Audit Committee have a financial expert?" — CMA
 * Article 54, SAMA RGov § 5.3.2, SOX-equivalent guidance).
 *
 * <p>{@code skillDimension} captures the GRC-relevant competency areas
 * regulators care about:
 * <ul>
 *   <li>{@code FINANCIAL_EXPERT} — required on Audit Committee (CMA Art 54)</li>
 *   <li>{@code CYBER_EXPERT}     — required on Cyber Committee (NCA ECC-2 § 1-2)</li>
 *   <li>{@code BCM_EXPERT}       — required on Crisis/BCM Committee (ISO 22301)</li>
 *   <li>{@code AML_EXPERT}       — required on AML Committee (AML Law Art 41)</li>
 *   <li>{@code LEGAL}, {@code INDUSTRY}, {@code DIGITAL}, {@code ESG},
 *       {@code RISK_MGMT}</li>
 * </ul>
 *
 * <p>The {@code memberTermId} FK points back to the term row — when the
 * term ends, the skill entries are kept for historical reporting (last
 * year's skills matrix is a board-pack staple).
 */
@Entity
@Table(schema = "admin", name = "committee_member_skills")
@Getter @Setter @NoArgsConstructor
public class CommitteeMemberSkill {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "committee_id", nullable = false)
    private Long committeeId;

    /** Nullable — ad-hoc external advisors may not have a term row. */
    @Column(name = "member_term_id")
    private Long memberTermId;

    @Column(name = "member_name", nullable = false, length = 255)
    private String memberName;

    /** FINANCIAL_EXPERT | CYBER_EXPERT | BCM_EXPERT | AML_EXPERT |
     *  LEGAL | INDUSTRY | DIGITAL | ESG | RISK_MGMT | … */
    @Column(name = "skill_dimension", nullable = false, length = 60)
    private String skillDimension;

    /** BASIC | INTERMEDIATE | EXPERT */
    @Column(name = "skill_level", nullable = false, length = 20)
    private String skillLevel;

    /** CMA-style justification — "20-year career at PwC including
     *  CFO of listed entity 2018-2024" for the FINANCIAL_EXPERT
     *  designation. Required for regulator filings. */
    @Column(name = "evidence_en", columnDefinition = "NVARCHAR(MAX)")
    private String evidenceEn;

    /** Comma-separated relevant certifications: "CPA, CFA, CISA, CISSP" */
    @Column(length = 255)
    private String certification;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(name = "last_reviewed_at")
    private LocalDate lastReviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
