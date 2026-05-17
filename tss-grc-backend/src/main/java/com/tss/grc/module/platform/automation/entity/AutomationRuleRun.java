package com.tss.grc.module.platform.automation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One firing of one {@link AutomationRule} against one domain event.
 * Outcome is SUCCESS / FAILED / SKIPPED (condition didn't match).
 */
@Entity
@Table(schema = "platform", name = "automation_rule_runs")
@Getter @Setter @NoArgsConstructor
public class AutomationRuleRun {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "fired_at", nullable = false, updatable = false)
    private LocalDateTime firedAt = LocalDateTime.now();

    @Column(name = "trigger_event", nullable = false, length = 80)
    private String triggerEvent;

    @Column(name = "entity_type", length = 120)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "correlation_id", length = 60)
    private String correlationId;

    /** SUCCESS | FAILED | SKIPPED */
    @Column(nullable = false, length = 20)
    private String outcome = "SUCCESS";

    @Column(name = "error_message", columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @Column(name = "result_summary", columnDefinition = "NVARCHAR(MAX)")
    private String resultSummary;
}
