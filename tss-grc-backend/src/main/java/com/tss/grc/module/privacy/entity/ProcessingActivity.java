package com.tss.grc.module.privacy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "privacy", name = "processing_activities")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ProcessingActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "activity_name_en", nullable = false, length = 255)
    private String activityNameEn;

    @Column(name = "activity_name_ar", length = 255)
    private String activityNameAr;

    @Column(name = "purpose_en", columnDefinition = "NVARCHAR(MAX)")
    private String purposeEn;

    @Column(name = "legal_basis", length = 100)
    private String legalBasis;

    @Column(name = "retention_period_months")
    private Integer retentionPeriodMonths;

    @Column(name = "cross_border_transfer", nullable = false)
    private Boolean crossBorderTransfer = false;

    // §5.4 #6 cross-border transfer detail ─────────────────────────
    /** Destination jurisdiction — populated when crossBorderTransfer=true. */
    @Column(name = "receiving_country", length = 100)
    private String receivingCountry;

    /** ADEQUATE | PARTIAL | NONE — derived from a Saudi Data Office adequacy list. */
    @Column(name = "adequacy_decision", length = 20)
    private String adequacyDecision;

    /** SCC | BCR | EXPLICIT_CONSENT | DATA_OFFICE_APPROVAL | INTRA_GROUP */
    @Column(name = "transfer_mechanism", length = 40)
    private String transferMechanism;

    /** Saudi Data Office case / decision reference for non-adequate transfers. */
    @Column(name = "data_office_reference", length = 100)
    private String dataOfficeReference;

    // §5.4 #7 special-category data tagging ────────────────────────
    @Column(name = "special_category_flag", nullable = false)
    private Boolean specialCategoryFlag = false;

    /** JSON array, e.g. ["HEALTH","BIOMETRIC","CHILDREN_UNDER_18","FINANCIAL","GENETIC"]. */
    @Column(name = "data_categories_json", columnDefinition = "NVARCHAR(MAX)")
    private String dataCategoriesJson;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "ACTIVE";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "deleted_by")
    private Long deletedBy;
}
