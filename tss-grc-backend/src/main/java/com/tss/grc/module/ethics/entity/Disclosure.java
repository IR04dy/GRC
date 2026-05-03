package com.tss.grc.module.ethics.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Conflict of interest declarations + gifts/hospitality register + outside
 * business activities + related-party + political/charitable donations.
 *
 * Workflow: DRAFT → SUBMITTED → REVIEW → APPROVED|REJECTED|RECUSED|MITIGATED.
 */
@Entity
@Table(schema = "ethics", name = "disclosures")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Disclosure {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", insertable = false, updatable = false)
    private UUID externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "disclosure_code", length = 50)
    private String disclosureCode;

    @Column(name = "disclosure_type", nullable = false, length = 40)
    private String disclosureType;
    // CONFLICT_OF_INTEREST | GIFT_RECEIVED | GIFT_GIVEN | HOSPITALITY
    //   | OUTSIDE_BUSINESS | RELATED_PARTY | POLITICAL_DONATION | CHARITABLE

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "description", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "discloser_name", nullable = false, length = 255)
    private String discloserName;

    @Column(name = "discloser_email", length = 255)
    private String discloserEmail;

    @Column(name = "discloser_employee_id")
    private Long discloserEmployeeId;

    @Column(name = "discloser_department", length = 255)
    private String discloserDepartment;

    @Column(name = "discloser_role", length = 255)
    private String discloserRole;

    @Column(name = "counterparty_name", length = 500)
    private String counterpartyName;

    @Column(name = "counterparty_relationship", length = 100)
    private String counterpartyRelationship;

    @Column(name = "monetary_value", precision = 18, scale = 2)
    private BigDecimal monetaryValue;

    @Column(name = "currency", length = 3)
    private String currency = "SAR";

    @Column(name = "occurred_on")
    private LocalDate occurredOn;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "decision", length = 40)
    private String decision;

    @Column(name = "decision_notes", columnDefinition = "NVARCHAR(MAX)")
    private String decisionNotes;

    @Column(name = "decided_by_name", length = 255)
    private String decidedByName;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "framework_id")
    private Long frameworkId;

    @Column(name = "clause_ref", length = 100)
    private String clauseRef;

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
