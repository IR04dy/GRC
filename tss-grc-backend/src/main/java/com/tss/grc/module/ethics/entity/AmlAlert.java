package com.tss.grc.module.ethics.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AML transaction-monitoring alert. Surfaces from the bank's TM engine
 * (or manual escalation) for analyst disposition. State machine:
 * OPEN → INVESTIGATING → CLEARED|ESCALATED|STR_FILED → CLOSED.
 */
@Entity
@Table(schema = "ethics", name = "aml_alerts")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AmlAlert {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", insertable = false, updatable = false)
    private UUID externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "alert_code", length = 50)
    private String alertCode;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "MEDIUM";

    @Column(name = "rule_id", length = 100)
    private String ruleId;

    @Column(name = "rule_name", length = 255)
    private String ruleName;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "description", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "subject_name", length = 500)
    private String subjectName;

    @Column(name = "counterparty_name", length = 500)
    private String counterpartyName;

    @Column(name = "counterparty_country", length = 3)
    private String counterpartyCountry;

    @Column(name = "transaction_count")
    private Integer transactionCount;

    @Column(name = "aggregate_amount", precision = 18, scale = 2)
    private BigDecimal aggregateAmount;

    @Column(name = "currency", length = 3)
    private String currency = "SAR";

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    @Column(name = "first_detected_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime firstDetectedAt;

    /**
     * F2-250 — <b>no initialiser</b>, and {@code AmlAlertService#create} owns the default.
     *
     * <p>It read {@code = "OPEN"}, which did two things. It was a second literal for the state the
     * machine already declares as its root, so the value the server picks and the value it accepts
     * could drift (F2-217) — {@code AmlAlertService.INITIAL_STATUS} is derived from
     * {@code TRANSITIONS} instead. And on an entity-bound PUT it made an <em>omitted</em> status
     * indistinguishable from a supplied "OPEN" (F2-237), so the echo rule that stops this route
     * performing a transition would have refused every partial update — a fix failing on correct
     * code, which is the one failure a fix must never have (F2-214).
     */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "analyst_name", length = 255)
    private String analystName;

    @Column(name = "analyst_id")
    private Long analystId;

    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    @Column(name = "disposition", length = 40)
    private String disposition;

    @Column(name = "disposition_notes", columnDefinition = "NVARCHAR(MAX)")
    private String dispositionNotes;

    @Column(name = "decided_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime decidedAt;

    @Column(name = "decided_by_name", length = 255)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String decidedByName;

    @Column(name = "str_filed", nullable = false)
    private Boolean strFiled = false;

    @Column(name = "str_reference", length = 100)
    private String strReference;

    @Column(name = "str_filed_at")
    private LocalDateTime strFiledAt;

    @Column(name = "framework_id")
    private Long frameworkId;

    @Column(name = "clause_ref", length = 100)
    private String clauseRef;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
