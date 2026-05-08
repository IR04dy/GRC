package com.tss.grc.module.bcm_lc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(schema = "bcm_lc", name = "invocations")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Invocation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "title_en", nullable = false, length = 500) private String titleEn;
    @Column(name = "title_ar", nullable = false, length = 500) private String titleAr;

    /** SEV1/SEV2/SEV3/SEV4 */
    @Column(nullable = false, length = 10) private String severity;

    @Column(name = "trigger_event_en", columnDefinition = "NVARCHAR(MAX)", nullable = false) private String triggerEventEn;
    @Column(name = "trigger_event_ar", columnDefinition = "NVARCHAR(MAX)", nullable = false) private String triggerEventAr;

    /** Comma-separated BIA external_ids */
    @Column(name = "affected_bia_ids", columnDefinition = "NVARCHAR(MAX)", nullable = false) private String affectedBiaIds;
    @Column(name = "affected_sites",   columnDefinition = "NVARCHAR(MAX)") private String affectedSites;

    @Column(name = "incident_commander", length = 255) private String incidentCommander;
    @Column(name = "bcm_coordinator",    length = 255) private String bcmCoordinator;

    /** STANDBY/DECLARED/INVOKED/STABILISED/STAND_DOWN/POST_INCIDENT_REVIEW/CLOSED */
    @Column(nullable = false, length = 30)
    private String status = "STANDBY";

    @Column(name = "detected_at",   nullable = false) private LocalDateTime detectedAt = LocalDateTime.now();
    @Column(name = "declared_at")    private LocalDateTime declaredAt;
    @Column(name = "declared_by", length = 255) private String declaredBy;
    @Column(name = "invoked_at")     private LocalDateTime invokedAt;
    @Column(name = "invoked_strategies", columnDefinition = "NVARCHAR(MAX)") private String invokedStrategies;
    @Column(name = "stabilised_at")  private LocalDateTime stabilisedAt;
    @Column(name = "stand_down_at")  private LocalDateTime standDownAt;
    @Column(name = "stand_down_authoriser", length = 255) private String standDownAuthoriser;
    @Column(name = "review_completed_at") private LocalDateTime reviewCompletedAt;

    @Column(name = "actual_rto_hours", precision = 10, scale = 2) private BigDecimal actualRtoHours;
    @Column(name = "actual_rpo_hours", precision = 10, scale = 2) private BigDecimal actualRpoHours;
    @Column(name = "estimated_loss_sar", precision = 18, scale = 2) private BigDecimal estimatedLossSar;

    @Column(name = "regulator_notified_at") private LocalDateTime regulatorNotifiedAt;
    @Column(name = "regulator_reference", length = 100) private String regulatorReference;
    @Column(name = "sama_notified",  nullable = false) private Boolean samaNotified = false;
    @Column(name = "ncema_notified", nullable = false) private Boolean ncemaNotified = false;

    @Column(name = "pir_findings_en", columnDefinition = "NVARCHAR(MAX)") private String pirFindingsEn;
    @Column(name = "pir_findings_ar", columnDefinition = "NVARCHAR(MAX)") private String pirFindingsAr;
    @Column(name = "pir_capa_count",  nullable = false) private Integer pirCapaCount = 0;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
}
