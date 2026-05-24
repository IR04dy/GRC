package com.tss.grc.module.audit_committee.dashboard.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One frozen Audit Committee dashboard snapshot — Sprint 11.
 *
 * <p>A snapshot is a point-in-time freeze of the live dashboard taken
 * the morning of a Committee meeting. The frozen JSON in
 * {@link #kpisJson} is the stable record the Committee acts on —
 * resolutions, attestations and the Chair's executive summary are
 * all bound to this row.
 *
 * <p>Live vs snapshot:
 * <ul>
 *   <li><b>Live</b> mode is recomputed on every read by aggregating
 *       across the existing module services.</li>
 *   <li><b>Snapshot</b> mode reads {@link #kpisJson} verbatim — even
 *       if the underlying source rows change later, the Committee's
 *       minutes still reconcile to the original numbers.</li>
 * </ul>
 *
 * <p>Once {@link #isFinalized} is set the row is treated as
 * immutable by the service layer — further attestations or resolutions
 * are rejected.
 */
@Entity
@Table(schema = "audit_committee", name = "dashboard_snapshot")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DashboardSnapshot extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    /** Human-readable code, e.g. {@code AC-DASH-2026-Q2-MEETING}. */
    @Column(name = "snapshot_code", nullable = false, length = 64)
    private String snapshotCode;

    /** When the Committee will / did meet. */
    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    /** QUARTERLY | ANNUAL | AD_HOC | OFFCYCLE. */
    @Column(name = "meeting_type", nullable = false, length = 20)
    private String meetingType;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Column(name = "generated_by_user_id", nullable = false)
    private Long generatedByUserId;

    /** Frozen JSON document — entire dashboard payload at generation time. */
    @Column(name = "kpis_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String kpisJson;

    @Column(name = "executive_summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String executiveSummaryEn;

    @Column(name = "executive_summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String executiveSummaryAr;

    /** JSON array of captured resolutions — appended in service. */
    @Column(name = "committee_resolutions", columnDefinition = "NVARCHAR(MAX)")
    private String committeeResolutions;

    @Column(name = "is_finalized", nullable = false)
    private Boolean isFinalized = Boolean.FALSE;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "finalized_by_user_id")
    private Long finalizedByUserId;
}
