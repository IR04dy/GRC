package com.tss.grc.module.cyber.asm.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One issue attached to an {@link AttackSurfaceAsset} — Sprint 11 ASM.
 *
 * <p>Lifecycle: {@code OPEN → INVESTIGATING → FIXED | FALSE_POSITIVE |
 * ACCEPTED_RISK | OUT_OF_SCOPE}. Transitions and the recompute of the
 * parent asset's risk score are owned by {@code AttackSurfaceFindingService}.
 *
 * <p>{@link #capaTaskId} is a soft FK — set when the finding is
 * escalated to a CAPA task so the dashboard can render the link
 * without a join.
 */
@Entity
@Table(schema = "cyber", name = "attack_surface_finding")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AttackSurfaceFinding extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    /**
     * EXPIRED_CERT | WEAK_CIPHER | UNPATCHED | EXPOSED_PORT | MISSING_HEADER |
     * OUTDATED_FRAMEWORK | UNVERIFIED_OWNER | NO_WAF | NO_RATE_LIMIT |
     * DNS_TAKEOVER_RISK
     */
    @Column(name = "finding_type", nullable = false, length = 40)
    private String findingType;

    /** CRITICAL | HIGH | MEDIUM | LOW | INFO */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    /** TENABLE | CROWDSTRIKE | CCM_RUNNER | MANUAL | AUTOMATED_PROBE */
    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @Column(name = "source_ref", length = 255)
    private String sourceRef;

    @Column(name = "description_en", nullable = false, length = 2000)
    private String descriptionEn;

    @Column(name = "description_ar", length = 2000)
    private String descriptionAr;

    @Column(name = "recommendation_en", length = 1000)
    private String recommendationEn;

    @Column(name = "recommendation_ar", length = 1000)
    private String recommendationAr;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt = LocalDateTime.now();

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** OPEN | INVESTIGATING | ACCEPTED_RISK | FIXED | FALSE_POSITIVE | OUT_OF_SCOPE */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN";

    /** Soft FK to capa.capa_tasks — set when escalated. */
    @Column(name = "capa_task_id")
    private Long capaTaskId;
}
