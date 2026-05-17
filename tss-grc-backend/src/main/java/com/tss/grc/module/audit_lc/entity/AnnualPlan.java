package com.tss.grc.module.audit_lc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Annual audit plan — V16 table {@code audit_lc.annual_plans}.
 *
 * <p>Status machine: DRAFT → AC_REVIEW → AC_APPROVED → EXECUTING → CLOSED.
 * Quarterly columns (q1_codes…q4_codes) hold comma-separated engagement
 * codes scheduled in that quarter — kept as text for now to match the
 * V16 schema; UI parses/joins.
 */
@Entity
@Table(schema = "audit_lc", name = "annual_plans")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AnnualPlan {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64) private String externalId;
    @Column(name = "organisation_id", nullable = false)          private Long organisationId;

    @Column(name = "year", nullable = false)        private Integer year;
    @Column(nullable = false, length = 20)          private String status = "DRAFT";
    @Column(name = "last_updated", nullable = false) private LocalDateTime lastUpdated = LocalDateTime.now();

    @Column(name = "ac_approved_by") private String acApprovedBy;
    @Column(name = "ac_approved_at") private LocalDateTime acApprovedAt;

    @Column(name = "q1_codes", columnDefinition = "NVARCHAR(MAX)") private String q1Codes;
    @Column(name = "q2_codes", columnDefinition = "NVARCHAR(MAX)") private String q2Codes;
    @Column(name = "q3_codes", columnDefinition = "NVARCHAR(MAX)") private String q3Codes;
    @Column(name = "q4_codes", columnDefinition = "NVARCHAR(MAX)") private String q4Codes;

    @Column(name = "budget_hours", nullable = false) private Integer budgetHours = 0;
    @Column(name = "ac_notes_en",  columnDefinition = "NVARCHAR(MAX)") private String acNotesEn;
    @Column(name = "ac_notes_ar",  columnDefinition = "NVARCHAR(MAX)") private String acNotesAr;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
}
