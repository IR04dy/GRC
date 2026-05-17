package com.tss.grc.module.platform.automation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Automation rule — Framework Intersection F8 + Saudi GRC prompt §14.
 *
 * <p>Each row is one "when X happens, do Y" rule. The 20 rules from
 * the prompt are seeded as PROMPT_14 source rows; orgs can add their
 * own with source=CUSTOM. AutomationRuleEngine listens to domain
 * events and dispatches the matching rules.
 *
 * <p>Trigger / condition / action are intentionally string + JSON so
 * the engine can evolve without a schema change.
 */
@Entity
@Table(schema = "platform", name = "automation_rules")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AutomationRule {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "rule_code", nullable = false, length = 40)
    private String ruleCode;

    @Column(name = "name_en", nullable = false, length = 200)
    private String nameEn;

    @Column(name = "name_ar", length = 200)
    private String nameAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "trigger_event", nullable = false, length = 80)
    private String triggerEvent;

    @Column(name = "condition_json", columnDefinition = "NVARCHAR(MAX)")
    private String conditionJson;

    @Column(name = "action_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String actionJson;

    @Column(nullable = false)
    private Integer priority = 50;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "audit_required", nullable = false)
    private Boolean auditRequired = true;

    @Column(nullable = false, length = 40)
    private String source = "PROMPT_14";

    @Column(name = "last_fired_at")
    private LocalDateTime lastFiredAt;

    @Column(name = "fire_count", nullable = false)
    private Long fireCount = 0L;

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
