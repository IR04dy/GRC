package com.tss.grc.module.platform.controls.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Control — the spine of the Framework Intersection design (F2).
 *
 * <p>One control can be mapped to many citations across many
 * authority documents (NCA, PDPL, SAMA, ISO …) via
 * {@link ControlMapping}. Domain modules (cyber, bcm, privacy, …)
 * eventually point here instead of carrying parallel catalogues.
 *
 * <p>Control taxonomy follows NIST / COBIT:
 * <ul>
 *   <li>{@code control_type}: PREVENTIVE | DETECTIVE | CORRECTIVE
 *       | DETERRENT | RECOVERY</li>
 *   <li>{@code automation_level}: MANUAL | SEMI | AUTOMATED</li>
 *   <li>{@code frequency}: CONTINUOUS | DAILY | WEEKLY | MONTHLY
 *       | QUARTERLY | ANNUAL | ON_EVENT</li>
 * </ul>
 *
 * <p>{@code legacy_source_module} + {@code legacy_id} support
 * coexistence with the legacy per-module control tables during the
 * F3-F4 cutover.
 */
@Entity
@Table(schema = "platform", name = "controls")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Control {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "control_code", nullable = false, length = 60)
    private String controlCode;

    @Column(nullable = false, length = 20)
    private String version = "1.0";

    @Column(nullable = false, length = 40)
    private String domain;

    @Column(name = "objective_id")
    private Long objectiveId;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "control_type", nullable = false, length = 20)
    // ⚠⚠ F2-294 removed `= "PREVENTIVE"`. 15 of 32 live rows differ in both databases:
    // the reset turns 9 DETECTIVE, 4 CORRECTIVE and 2 RECOVERY controls into PREVENTIVE,
    // which misstates each one's position in the defence chain. `createControl` already
    // defaulted AND vocabulary-checked it; the initialiser made both dead on the update.
    // API-reachable only — `lib/controlLibrary.ts#updateControl` has NO call site, which
    // is F2-214's invitation-to-build shape rather than a defence.
    private String controlType;

    @Column(name = "automation_level", nullable = false, length = 20)
    // ⚠ F2-294 removed `= "MANUAL"`. 12 of 32 rows differ — 4 AUTOMATED and 8 SEMI would
    // read MANUAL, so this one errs PESSIMISTICALLY. 🔑 Directions are MIXED across this
    // sweep (five flattering, four pessimistic), which is F2-196's rule that a reset has
    // no direction until you look — do not assume the F2-193/F2-195/F2-205 bias.
    private String automationLevel;

    @Column(length = 20)
    private String frequency;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "legacy_source_module", length = 40)
    private String legacySourceModule;

    @Column(name = "legacy_id")
    private Long legacyId;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
