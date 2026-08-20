package com.tss.grc.module.bcm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Slice 9-C — Quantitative Business Impact Analysis header.
 *
 * <p>Sits alongside the legacy qualitative {@link BiaRecord} (RTO/RPO/MTPD +
 * impact label) and the rich {@code BcmBiaAssessment} (impact-dimension
 * matrix). This entity captures the CFO-grade financial loss profile that
 * SAMA's Operational Resilience Framework §5.3 and NCA ECC-2 expect:
 *
 * <ul>
 *   <li>five outage time-bands (24h / 72h / 1w / 2w / 1m)</li>
 *   <li>eight loss categories per band (loss lines, see
 *       {@link BiaQuantitativeLossLine})</li>
 *   <li>board-set maximum tolerable loss (SAR), and the first time-band
 *       where cumulative loss breaches it ({@link #mtpdBreachAt})</li>
 *   <li>workflow: DRAFT → IN_REVIEW → CFO_REVIEW → CFO_APPROVED →
 *       BOARD_APPROVED → ARCHIVED, with REJECTED as a side branch</li>
 * </ul>
 *
 * <p>The per-band {@code total_loss_*_sar} columns are denormalised
 * rollups recomputed by the service every save — readers never have to
 * sum the lines themselves.
 */
@Entity
@Table(schema = "bcm", name = "bia_quantitative")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BiaQuantitative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "process_id", nullable = false)
    private Long processId;

    @Column(name = "assessment_code", nullable = false, length = 80)
    private String assessmentCode;

    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate;

    @Column(name = "cycle_year", nullable = false)
    private Integer cycleYear;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "SAR";

    @Column(name = "methodology_notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String methodologyNotesEn;

    @Column(name = "methodology_notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String methodologyNotesAr;

    @Column(name = "total_loss_24h_sar", precision = 18, scale = 2)
    private BigDecimal totalLoss24hSar;

    @Column(name = "total_loss_72h_sar", precision = 18, scale = 2)
    private BigDecimal totalLoss72hSar;

    @Column(name = "total_loss_1w_sar", precision = 18, scale = 2)
    private BigDecimal totalLoss1wSar;

    @Column(name = "total_loss_2w_sar", precision = 18, scale = 2)
    private BigDecimal totalLoss2wSar;

    @Column(name = "total_loss_1m_sar", precision = 18, scale = 2)
    private BigDecimal totalLoss1mSar;

    @Column(name = "max_tolerable_loss_sar", precision = 18, scale = 2)
    private BigDecimal maxTolerableLossSar;

    @Column(name = "mtpd_breach_at", length = 20)
    private String mtpdBreachAt;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "prepared_by_user_id")
    private Long preparedByUserId;

    @Column(name = "prepared_by_name", length = 255)
    private String preparedByName;

    @Column(name = "prepared_at")
    private LocalDateTime preparedAt;

    @Column(name = "bcm_lead_reviewed_at")
    private LocalDateTime bcmLeadReviewedAt;

    @Column(name = "bcm_lead_reviewed_by", length = 255)
    private String bcmLeadReviewedBy;

    @Column(name = "cfo_approved_at")
    private LocalDateTime cfoApprovedAt;

    @Column(name = "cfo_approver_name", length = 255)
    private String cfoApproverName;

    @Column(name = "cfo_comments", columnDefinition = "NVARCHAR(MAX)")
    private String cfoComments;

    @Column(name = "board_approved_at")
    private LocalDateTime boardApprovedAt;

    /**
     * F2-261 — the board signatory, stamped from the authenticated principal.
     *
     * <p>This column did not exist until V384. {@code boardApprove} stamped {@code boardApprovedAt}
     * from the server's clock and recorded no actor of any kind, while both lower tiers of the same
     * ladder ({@code bcmLeadReviewedBy}, and {@code cfoApproverName} since F2-223) record theirs —
     * so the attribution quality decreased as the authority increased, and the tier with none was
     * the board sign-off.
     *
     * <p>⚠ {@code NVARCHAR(255)} holding a username, deliberately mirroring its two siblings rather
     * than being the {@code BIGINT} an id would want: the four-eyes bar in
     * {@code QuantitativeBiaService.boardApprove} compares all three parties, and they have to be
     * comparable. A second mechanism beside an existing one is F2-190's defect. Converting all
     * three to ids together is a coherent slice and is filed; doing one of the three is what
     * produced the divergence this fixes.
     */
    @Column(name = "board_approved_by", length = 255)
    private String boardApprovedBy;

    @Column(name = "board_minutes_ref", length = 255)
    private String boardMinutesRef;

    @Column(name = "rejection_reason", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;

    @OneToMany(mappedBy = "biaQuantitative", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Where(clause = "is_deleted = 0")
    private List<BiaQuantitativeLossLine> lossLines = new ArrayList<>();

    /** Convenience: add a line and back-link the FK in one call. */
    public void addLossLine(BiaQuantitativeLossLine line) {
        line.setBiaQuantitative(this);
        line.setOrganisationId(this.organisationId);
        this.lossLines.add(line);
    }

    /** Convenience: detach a line so cascade orphanRemoval picks it up on save. */
    public void removeLossLine(BiaQuantitativeLossLine line) {
        this.lossLines.remove(line);
        line.setBiaQuantitative(null);
    }
}
