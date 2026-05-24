package com.tss.grc.module.bcm.mtpd_inheritance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slice 10-E — One MTPD-inheritance finding row.
 *
 * <p>Produced by {@code MtpdInheritanceService.computeAssessment()}
 * for every REQUIRED or RECOMMENDED edge in
 * {@link ProcessVendorDependency}. Each row classifies the (process,
 * vendor) pair as {@code BREACH} / {@code WARNING} / {@code OK} /
 * {@code NO_ATTESTATION} and stamps a severity computed from the
 * dependency strength and finding type.
 *
 * <h3>Denormalisation rationale</h3>
 * Both {@code businessProcessNameEn/Ar} and {@code vendorName} are
 * denormalised onto the finding so the audit trail is immune to later
 * renames in either register. An auditor opening a 2-year-old
 * assessment still sees the exact names that were in effect at the
 * compute moment.
 *
 * <h3>CAPA linkage</h3>
 * {@code capaTaskId} and {@code capaExternalId} are populated when
 * {@code MtpdInheritanceService.createCapaForFinding()} opens a
 * remediation task against this finding. The controller gates against
 * double-creation so each finding has at most one CAPA on the wire.
 */
@Entity
@Table(schema = "bcm", name = "mtpd_inheritance_finding")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class MtpdInheritanceFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Column(name = "business_process_id", nullable = false)
    private Long businessProcessId;

    @Column(name = "business_process_name_en", nullable = false, length = 255)
    private String businessProcessNameEn;

    @Column(name = "business_process_name_ar", length = 255)
    private String businessProcessNameAr;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "vendor_name", nullable = false, length = 255)
    private String vendorName;

    @Column(name = "process_mtpd_hours")
    private Integer processMtpdHours;

    @Column(name = "vendor_attested_mtpd_hours")
    private Integer vendorAttestedMtpdHours;

    @Column(name = "variance_hours")
    private Integer varianceHours;

    /** BREACH | WARNING | OK | NO_ATTESTATION. */
    @Column(name = "finding_type", nullable = false, length = 20)
    private String findingType;

    /** CRITICAL | HIGH | MEDIUM | LOW. */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "dependency_strength", nullable = false, length = 20)
    private String dependencyStrength;

    @Column(name = "dependency_type", nullable = false, length = 40)
    private String dependencyType;

    @Column(name = "recommendation_en", length = 1000)
    private String recommendationEn;

    @Column(name = "recommendation_ar", length = 1000)
    private String recommendationAr;

    @Column(name = "capa_task_id")
    private Long capaTaskId;

    @Column(name = "capa_external_id", length = 64)
    private String capaExternalId;

    @Column(name = "capa_created_at")
    private LocalDateTime capaCreatedAt;

    @Column(name = "source_attestation_id")
    private Long sourceAttestationId;

    @Column(name = "source_campaign_code", length = 64)
    private String sourceCampaignCode;

    @Column(name = "source_submitted_at")
    private LocalDateTime sourceSubmittedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
