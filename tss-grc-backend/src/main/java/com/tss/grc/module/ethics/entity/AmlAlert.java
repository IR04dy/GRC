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

    // F2-286 — the column is insertable = false, so the DATABASE refuses a caller's value
    // and the create hands back the instance Jackson bound: without this the response
    // echoed a value the row does not carry, and only a re-fetch showed the truth. This
    // says the mapping refuses it, not that the caller should never state it — where the
    // caller should, the fix is to make the column insertable (F2-286 did that for
    // ethics.screening_hits.screened_at, whose value nothing could ever state).
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "external_id", insertable = false, updatable = false)
    private UUID externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "alert_code", length = 50)
    private String alertCode;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    // ⚠⚠ F2-294 removed `= "MEDIUM"`. `AmlAlertService#update` guards this with
    // `if (src.getSeverity() != null)`, and on an entity-bound PUT the initialiser made
    // that guard true on EVERY request — so a partial body reset the severity of a SAMA
    // AML/CFT alert. Measured identical in dev and prod over 10 live rows: 7 differ, and
    // the reset DOWNGRADES three CRITICAL and three HIGH alerts to MEDIUM while promoting
    // one LOW. `create` now owns the default (F2-238's remedy), so an omitted severity
    // still lands MEDIUM and an omitted severity on an UPDATE leaves the stored band alone.
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

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

    /**
     * F2-277 — the Arabic half of the derived subject label, from
     * {@code ethics.kyc_subjects.full_name_ar} (16 of 16 rows hold it). F2-250 built
     * {@code KycSubjectDirectory} so an AML alert could not name a subject the caller invented,
     * and derived the English name alone; on an Arabic-primary UI the party an alert is ABOUT then
     * rendered in English only.
     */
    @Column(name = "subject_name_ar", length = 500)
    private String subjectNameAr;

    @Column(name = "counterparty_name", length = 500)
    private String counterpartyName;

    @Column(name = "counterparty_country", length = 3)
    private String counterpartyCountry;

    @Column(name = "transaction_count")
    private Integer transactionCount;

    @Column(name = "aggregate_amount", precision = 18, scale = 2)
    private BigDecimal aggregateAmount;

    // ⚠ F2-294 removed `= "SAR"`. The only NULLABLE column in this sweep, and the one
    // whose create-path default was ALREADY WRITTEN — `create` ends
    // `if (a.getCurrency() == null) a.setCurrency("SAR")`, which could never fire while
    // this initialiser stood. 0 of 10 rows differ, so the reset was latent; the dead
    // default is the finding (F2-292's shape, measured across a whole population here).
    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    /**
     * When the transaction-monitoring engine first <b>detected</b> the suspicious activity —
     * the start of the FIU reporting clock, and the anchor the seed used for every
     * {@code sla_due_at} in this register (detection + 30 days on all 10 live rows in both
     * databases).
     *
     * <p>F2-287 — it was {@code insertable = false}, which refuses <em>Hibernate</em> and not
     * just the caller (F2-286), so the column could only ever hold {@code sysutcdatetime()} at
     * the instant the row was inserted. F2-286 filed that as an owner decision — <i>"enabling
     * it means widening applyEditable, a product call"</i> — and both halves of that premise
     * were wrong:
     *
     * <ul>
     *   <li><b>The mechanism.</b> {@code applyEditable} is shared by {@code create} and
     *       {@code update}, and this column is {@code updatable = false}. A write there would
     *       compile, run on the update path, and change nothing — and the census of that shape
     *       in this tree is <b>zero</b>, so the filed fix would have created the first
     *       instance of it. The write belongs on the create path alone.</li>
     *   <li><b>The ownership.</b> This alert "surfaces from the bank's TM engine" (see the
     *       class javadoc), so the detection time is <em>received from outside</em> —
     *       [[F2-268]]'s third kind, which F2-209 already settles: received and recorded, or
     *       absent. Not a product decision.</li>
     * </ul>
     *
     * <p>🔑 The precision was the tell in the other direction too. F2-286 identified
     * {@code screened_at} as default-filled because every value was a microsecond instant in
     * an insert-shaped batch; here <b>0 of 10</b> rows carry sub-second precision, <b>0</b>
     * equal {@code created_at}, and there are 8 distinct round business times — the signature
     * of a value somebody <em>stated</em>.
     *
     * <p>Still {@code updatable = false}: a detection time is stated once. Because the mapping
     * refuses a change, {@code update} must not appear to accept one either — it takes an echo
     * and refuses a different value (F2-231/F2-234).
     */
    @Column(name = "first_detected_at", nullable = false, updatable = false)
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
