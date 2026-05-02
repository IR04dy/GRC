package com.tss.grc.module.evidence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Connector library definition — assessment §5.9 recommendation #2.
 * A connector documents an automated evidence-collection capability against a
 * specific source system (e.g. Active Directory, Microsoft Defender, AWS Config,
 * NCA HEY). The MVP shipped this session ships seeded definitions; the actual
 * scheduled-collection runtime is a follow-up.
 */
@Entity
@Table(schema = "evidence", name = "connector_definitions")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ConnectorDefinition {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name_en", nullable = false, length = 255)
    private String nameEn;

    @Column(name = "name_ar", length = 255)
    private String nameAr;

    /** IDP | EDR | CLOUD_SECURITY | VULN_SCANNER | SIEM | THREAT_INTEL | OTHER */
    @Column(name = "connector_type", nullable = false, length = 50)
    private String connectorType;

    @Column(name = "vendor", length = 100)
    private String vendor;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "config_schema_json", columnDefinition = "NVARCHAR(MAX)")
    private String configSchemaJson;

    @Column(name = "collection_plan_en", columnDefinition = "NVARCHAR(MAX)")
    private String collectionPlanEn;

    /** Comma-separated list of framework codes this connector helps satisfy. */
    @Column(name = "target_frameworks", length = 500)
    private String targetFrameworks;

    /** AVAILABLE | CONFIGURED | RUNNING | DISABLED */
    @Column(name = "status", nullable = false, length = 30)
    private String status = "AVAILABLE";

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_run_status", length = 30)
    private String lastRunStatus;

    @Column(name = "last_run_summary", columnDefinition = "NVARCHAR(MAX)")
    private String lastRunSummary;

    @Column(name = "evidence_collected_count", nullable = false)
    private Integer evidenceCollectedCount = 0;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
