package com.tss.grc.module.iam.governance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * RBAC Access-Governance P2 (plan §6.4) — one segregation-of-duties rule.
 * PAIR = two conflicting module/action grants; STRUCTURAL = persona-level constraint
 * keyed by {@code structuralKey} over {@code rolePatternCsv} (names or LIKE patterns).
 * {@code organisationId} NULL = global starter pack (SOD-01…SOD-08, seeded in V283).
 */
@Entity
@Table(schema = "iam", name = "sod_rules")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SodRule {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id") private Long organisationId;
    @Column(name = "rule_code", nullable = false, length = 20) private String ruleCode;
    /** PAIR | STRUCTURAL. */
    @Column(name = "rule_type", nullable = false, length = 15) private String ruleType;
    @Column(name = "module_a", length = 40) private String moduleA;
    @Column(name = "action_a", length = 20) private String actionA;
    @Column(name = "module_b", length = 40) private String moduleB;
    @Column(name = "action_b", length = 20) private String actionB;
    /** READONLY_PERSONA_WRITE | SERVICE_MULTI_MODULE. */
    @Column(name = "structural_key", length = 40) private String structuralKey;
    @Column(name = "role_pattern_csv", length = 400) private String rolePatternCsv;
    /** CRITICAL | HIGH | MEDIUM. */
    @Column(nullable = false, length = 10) private String severity = "HIGH";
    @Column(name = "rationale_en", nullable = false, length = 500) private String rationaleEn;
    @Column(name = "rationale_ar", length = 500) private String rationaleAr;
    @Column(name = "is_active", nullable = false) private Boolean isActive = true;
    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
