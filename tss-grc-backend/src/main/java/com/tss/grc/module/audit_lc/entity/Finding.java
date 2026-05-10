package com.tss.grc.module.audit_lc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(schema = "audit_lc", name = "findings")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Finding {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "engagement_code", nullable = false, length = 64)
    private String engagementCode;

    @Column(name = "ref_number", nullable = false, length = 20)
    private String refNumber;

    @Column(name = "title_en", nullable = false, length = 500) private String titleEn;
    @Column(name = "title_ar", nullable = false, length = 500) private String titleAr;

    /** CRITICAL/HIGH/MEDIUM/LOW/OBSERVATION */
    @Column(nullable = false, length = 20)
    private String severity;

    /** OPEN/MGMT_RESPONSE/IN_REMEDIATION/AWAITING_VERIFICATION/CLOSED */
    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    /** JSON [{framework, controlCode}]. */
    @Column(name = "control_crosswalk", columnDefinition = "NVARCHAR(MAX)")
    private String controlCrosswalk;

    @Column(name = "condition_en", nullable = false, columnDefinition = "NVARCHAR(MAX)") private String conditionEn;
    @Column(name = "condition_ar", nullable = false, columnDefinition = "NVARCHAR(MAX)") private String conditionAr;
    @Column(name = "criteria_en",  columnDefinition = "NVARCHAR(MAX)") private String criteriaEn;
    @Column(name = "criteria_ar",  columnDefinition = "NVARCHAR(MAX)") private String criteriaAr;
    @Column(name = "cause_en",     columnDefinition = "NVARCHAR(MAX)") private String causeEn;
    @Column(name = "cause_ar",     columnDefinition = "NVARCHAR(MAX)") private String causeAr;
    @Column(name = "recommendation_en", columnDefinition = "NVARCHAR(MAX)") private String recommendationEn;
    @Column(name = "recommendation_ar", columnDefinition = "NVARCHAR(MAX)") private String recommendationAr;
    @Column(name = "mgmt_response_en",  columnDefinition = "NVARCHAR(MAX)") private String mgmtResponseEn;
    @Column(name = "mgmt_response_ar",  columnDefinition = "NVARCHAR(MAX)") private String mgmtResponseAr;

    @Column(name = "mgmt_response_author") private String mgmtResponseAuthor;
    @Column(name = "mgmt_accepts")         private Boolean mgmtAccepts;

    @Column(name = "target_date") private LocalDate targetDate;
    @Column(name = "owner_role")  private String ownerRole;

    @Column(name = "raised_at", nullable = false) private LocalDateTime raisedAt = LocalDateTime.now();

    /** Comma-separated CAPA external ids. */
    @Column(name = "capa_ids", columnDefinition = "NVARCHAR(MAX)") private String capaIds;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
}
