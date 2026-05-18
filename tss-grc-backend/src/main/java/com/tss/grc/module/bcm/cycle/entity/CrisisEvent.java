package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Crisis Event — independent audit Tier-1 fix.
 *
 * <p>Real crisis event entity with the columns ISO 22301 §8.4.2 and NCEMA expect:
 * <ul>
 *   <li>5-level severity scale (OPERATIONAL_EVENT → NATIONAL_EMERGENCY)</li>
 *   <li>Affected services / processes / sites / suppliers / systems as JSON arrays of soft FKs</li>
 *   <li>Status lifecycle: DETECTED → ASSESSING → ACTIVATED → CONTAINED → RECOVERED → CLOSED</li>
 *   <li>Trigger flags (personal-data / cyber / supplier) so F8 rules can fan out follow-ups</li>
 *   <li>Soft links to activated BCPs and DRPs</li>
 * </ul>
 *
 * <p>Three sibling tables (SitReps, Decisions, RegulatoryNotifications) hold the
 * append-only timeline streams — see {@link CrisisSituationReport},
 * {@link CrisisDecision}, {@link CrisisRegulatoryNotification}.
 *
 * <p>Distinct from the legacy {@code BcmInvocation} (under {@code module/bcm_lc/})
 * which models the NCEMA-tier invocation lifecycle without the rich event context.
 */
@Entity
@Table(schema = "bcm", name = "crisis_events")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CrisisEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    /** Tenant-unique short code (e.g. CRIS-2026-000123). Assigned by the service. */
    @Column(name = "event_code", nullable = false, length = 50)
    private String eventCode;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String description;

    /**
     * Severity 1..5 (CHECK constraint in DB).
     *   1 = OPERATIONAL_EVENT       — حدث تشغيلي
     *   2 = SIGNIFICANT_INCIDENT    — حادث مؤثر
     *   3 = MAJOR_DISRUPTION        — تعطل جوهري
     *   4 = CRISIS                  — أزمة
     *   5 = NATIONAL_EMERGENCY      — طارئ وطني أو قطاعي
     * Severity ≥ 4 triggers executive notification per spec §7.16.
     */
    @Column(nullable = false)
    private Integer severity = 1;

    /** MANUAL | INCIDENT | EXERCISE | THREAT_INTEL | SUPPLIER | CYBER | EXTERNAL_ALERT */
    @Column(name = "trigger_source", length = 50)
    private String triggerSource;

    @Column(name = "affected_services_json",  columnDefinition = "NVARCHAR(MAX)") private String affectedServicesJson;
    @Column(name = "affected_processes_json", columnDefinition = "NVARCHAR(MAX)") private String affectedProcessesJson;
    @Column(name = "affected_sites_json",     columnDefinition = "NVARCHAR(MAX)") private String affectedSitesJson;
    @Column(name = "affected_suppliers_json", columnDefinition = "NVARCHAR(MAX)") private String affectedSuppliersJson;
    @Column(name = "affected_systems_json",   columnDefinition = "NVARCHAR(MAX)") private String affectedSystemsJson;

    @Column(name = "detection_time")  private LocalDateTime detectionTime;
    @Column(name = "activation_time") private LocalDateTime activationTime;
    @Column(name = "start_time")      private LocalDateTime startTime;
    @Column(name = "closure_time")    private LocalDateTime closureTime;

    @Column(name = "crisis_manager_id")
    private Long crisisManagerId;

    /** JSON array of {userId, role} for the activated crisis team. */
    @Column(name = "crisis_team_json", columnDefinition = "NVARCHAR(MAX)")
    private String crisisTeamJson;

    @Column(name = "root_cause", columnDefinition = "NVARCHAR(MAX)")
    private String rootCause;

    /** DETECTED | ASSESSING | ACTIVATED | CONTAINED | RECOVERED | CLOSED | CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "DETECTED";

    /** JSON arrays of BCP/DRP IDs activated for this event. */
    @Column(name = "related_bcp_ids_json", columnDefinition = "NVARCHAR(MAX)") private String relatedBcpIdsJson;
    @Column(name = "related_drp_ids_json", columnDefinition = "NVARCHAR(MAX)") private String relatedDrpIdsJson;

    /** Trigger flags — F8 rules read these to fan out cross-module follow-ups. */
    @Column(name = "personal_data_affected", nullable = false) private Boolean personalDataAffected = false;
    @Column(name = "cyber_related",          nullable = false) private Boolean cyberRelated         = false;
    @Column(name = "supplier_involved",      nullable = false) private Boolean supplierInvolved     = false;

    /** Soft FK to the lesson learned that captured this event's findings. */
    @Column(name = "lessons_learned_id")
    private Long lessonsLearnedId;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at")                    private LocalDateTime updatedAt;
    @Column(name = "created_by")                    private Long createdBy;
    @Column(name = "updated_by")                    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
