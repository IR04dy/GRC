package com.tss.grc.module.ndi.gap.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * NDI Gap — slice 8. Identified deficiency feeding the corrective-action
 * pipeline. Sources: COMPLIANCE / MATURITY / KPI / AUDIT / MANUAL.
 *
 * <p>Rollup fields (capa_count, capa_completed_count, rollup_progress) are
 * service-set whenever a CAPA is created / updated / deleted under this gap.
 */
@Entity
@Table(schema = "ndi", name = "gap")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiGap {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "cycle_id", nullable = false) private Long cycleId;
    @Column(name = "gap_code", length = 40)      private String gapCode;

    @Column(name = "source_type", nullable = false, length = 20) private String sourceType;
    @Column(name = "source_id")                                  private Long sourceId;
    @Column(length = 80)                                         private String domain;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;
    @Column(name = "root_cause",     columnDefinition = "NVARCHAR(MAX)") private String rootCause;
    @Column(name = "impact_summary", columnDefinition = "NVARCHAR(MAX)") private String impactSummary;

    @Column(nullable = false, length = 20) private String severity;
    @Column(length = 20)                   private String likelihood;
    @Column(name = "risk_score")           private Integer riskScore;

    @Column(name = "owner_name",  length = 255) private String ownerName;
    @Column(name = "owner_email", length = 255) private String ownerEmail;

    @Column(name = "open_date",         nullable = false) private LocalDate openDate;
    @Column(name = "target_date")                         private LocalDate targetDate;
    @Column(name = "actual_close_date")                   private LocalDate actualCloseDate;

    @Column(name = "rollup_progress",      nullable = false) private Integer rollupProgress;
    @Column(name = "capa_count",           nullable = false) private Integer capaCount;
    @Column(name = "capa_completed_count", nullable = false) private Integer capaCompletedCount;

    @Column(nullable = false, length = 20) private String status;
    @Column(name = "risk_id")              private Long riskId;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    /**
     * The one state a gap may be created in. Read by three places that would otherwise each hold
     * their own literal: {@link #onCreate()} below, {@code NdiGapService.createGapFromSource} (the
     * intake path, which sets it explicitly), and {@code NdiGapService.createGap}'s
     * {@code requireInitial} refusal.
     *
     * <p>⚠⚠ F2-302 — `onCreate()` has always defaulted this field, and a **default is not a
     * guard** ([[F2-218]]'s headline, here at a lifecycle callback rather than in a service). A
     * `@PrePersist` fires at flush time, after every setter, which makes it *stronger* evidence of
     * server ownership to a reader than a field initialiser — and it still only fills a null.
     * Probed live: `POST /api/ndi/gaps` with `status: "RESOLVED"` returned 200 and stored RESOLVED,
     * so a gap could be created in the state the CAPA rollup derives, on a row the same callback
     * had just given `capaCount = 0`.
     */
    public static final String INITIAL_STATUS = "OPEN";

    /**
     * The status the CAPA rollup derives when every corrective action is complete. It owns this
     * token together with {@link #actualCloseDate} — see
     * {@code NdiGapService.applyStatusAndCloseDate}, the single writer of that pair.
     */
    public static final String RESOLVED = "RESOLVED";

    /**
     * The states in which a gap is <b>not</b> closed, so an {@link #actualCloseDate} cannot stand.
     * Deliberately not the whole vocabulary and deliberately not derived from the CHECK: this is a
     * judgement about which states contradict a close date, not a copy of the five tokens
     * {@code ck_ndi_gap_status} admits. {@code CLOSED} and {@code ACCEPTED_RISK} are absent on
     * purpose — the rollup refuses to move either, and nothing in this platform declares whether
     * they carry a close date, so a rule for them would be invented rather than enforced
     * ([[F2-183]]).
     */
    public static final java.util.Set<String> NOT_CLOSED = java.util.Set.of("OPEN", "IN_PROGRESS");

    @PrePersist
    void onCreate() {
        if (createdAt == null)        createdAt = LocalDateTime.now();
        if (openDate == null)          openDate = LocalDate.now();
        if (severity == null)          severity = "MEDIUM";
        if (status == null)            status = INITIAL_STATUS;
        if (rollupProgress == null)    rollupProgress = 0;
        if (capaCount == null)         capaCount = 0;
        if (capaCompletedCount == null) capaCompletedCount = 0;
        if (isDeleted == null)         isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
