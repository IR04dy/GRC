package com.tss.grc.module.ethics.coi.entity;

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
 * One COI declaration submitted by an in-scope user against a
 * specific campaign — Sprint 13.
 *
 * <p>{@link #reviewOutcome} is auto-flagged on submit by
 * {@code CoiService.submitDeclaration} based on the line items
 * ({@link CoiDisclosedItem}) the declarant filed. The Ethics
 * Officer may then transition the outcome with explicit notes.
 */
@Entity
@Table(schema = "ethics", name = "coi_declaration")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CoiDeclaration extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "employee_no", length = 64)
    private String employeeNo;

    @Column(name = "full_name_en", nullable = false, length = 256)
    private String fullNameEn;

    @Column(name = "full_name_ar", nullable = false, length = 256)
    private String fullNameAr;

    @Column(name = "position_title_en", length = 256)
    private String positionTitleEn;

    @Column(name = "position_title_ar", length = 256)
    private String positionTitleAr;

    @Column(name = "department_en", length = 128)
    private String departmentEn;

    @Column(name = "department_ar", length = 128)
    private String departmentAr;

    @Column(name = "declaration_date", nullable = false)
    private LocalDateTime declarationDate = LocalDateTime.now();

    @Column(name = "has_conflict", nullable = false)
    private Boolean hasConflict = false;

    @Column(name = "has_potential_conflict", nullable = false)
    private Boolean hasPotentialConflict = false;

    @Column(name = "attestation_text", columnDefinition = "NVARCHAR(MAX)")
    private String attestationText;

    @Column(name = "submitted_on")
    private LocalDateTime submittedOn;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_on")
    private LocalDateTime reviewedOn;

    /** PENDING | CLEARED | MITIGATION_REQUIRED | RECUSAL_REQUIRED | ESCALATED. */
    @Column(name = "review_outcome", nullable = false, length = 32)
    private String reviewOutcome = "PENDING";

    @Column(name = "review_notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String reviewNotesEn;

    @Column(name = "review_notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String reviewNotesAr;
}
