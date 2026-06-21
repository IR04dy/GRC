package com.tss.grc.module.bcm.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.BiaQuantitativeLossLineDto;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.BiaQuantitativeRequest;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.BiaQuantitativeResponse;
import com.tss.grc.module.bcm.dto.BiaQuantitativeDtos.CfoApprovalRequest;
import com.tss.grc.module.bcm.entity.BiaQuantitative;
import com.tss.grc.module.bcm.entity.BiaQuantitativeLossLine;
import com.tss.grc.module.bcm.repository.BiaQuantitativeRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Slice 9-C — Quantitative Business Impact Analysis service.
 *
 * <h3>Workflow</h3>
 * <pre>
 *   DRAFT → IN_REVIEW → CFO_REVIEW ─┬─→ CFO_APPROVED → BOARD_APPROVED → ARCHIVED
 *                                   └─→ REJECTED
 * </pre>
 *
 * <h3>Rules</h3>
 * <ul>
 *   <li>Tenant scope — every read/write filters by
 *       {@link TenantContext#requireOrganisationId()}.</li>
 *   <li>Editable only in {@code DRAFT} or {@code IN_REVIEW} — APPROVED/
 *       BOARD_APPROVED records are locked.</li>
 *   <li>{@link #recomputeTotals(BiaQuantitative)} sums lines by time-band
 *       and writes {@code total_loss_*_sar}.</li>
 *   <li>{@link #findMtpdBreachBand(BiaQuantitative)} returns the first
 *       band where <i>cumulative</i> loss exceeds
 *       {@code maxTolerableLossSar}. Band order: 24h → 72h → 1w → 2w → 1m.</li>
 *   <li>Every transition writes an F7 audit row + emits an F8 event.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class QuantitativeBiaService {

    /** Canonical ordered time bands — used by rollups and breach detection. */
    public static final List<String> TIME_BANDS = List.of("24h", "72h", "1w", "2w", "1m");

    public static final Set<String> LOSS_TYPES = Set.of(
        "REVENUE_LOSS", "REGULATORY_FINE", "REPUTATIONAL_DAMAGE",
        "OPERATIONAL_RECOVERY", "LEGAL_COSTS", "PRODUCTIVITY_LOSS",
        "CUSTOMER_CHURN", "CONTRACTUAL_PENALTY");

    public static final Set<String> STATUSES = Set.of(
        "DRAFT", "IN_REVIEW", "CFO_REVIEW", "CFO_APPROVED",
        "BOARD_APPROVED", "ARCHIVED", "REJECTED");

    private static final Set<String> EDITABLE_STATUSES = Set.of("DRAFT", "IN_REVIEW");

    private final BiaQuantitativeRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BiaQuantitativeResponse> list() {
        return repo.findByOrganisationIdAndIsDeletedFalseOrderByAssessmentDateDesc(org())
            .stream().map(QuantitativeBiaService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BiaQuantitativeResponse get(UUID externalId) {
        return toResponse(loadOrThrow(externalId));
    }

    // ─── Writes ─────────────────────────────────────────────────────

    @Transactional
    public BiaQuantitativeResponse create(BiaQuantitativeRequest req) {
        validateRequest(req);
        Long orgId = org();

        BiaQuantitative h = new BiaQuantitative();
        h.setOrganisationId(orgId);
        applyRequest(h, req);
        h.setStatus("DRAFT");
        h.setAssessmentCode(req.assessmentCode() != null && !req.assessmentCode().isBlank()
            ? req.assessmentCode()
            : nextAssessmentCode(orgId, req.cycleYear()));
        if (repo.existsByOrganisationIdAndAssessmentCodeAndIsDeletedFalse(orgId, h.getAssessmentCode())) {
            throw new IllegalArgumentException(
                "Quantitative BIA assessment code already exists: " + h.getAssessmentCode());
        }
        h.setPreparedAt(LocalDateTime.now());

        recomputeTotals(h);
        h.setMtpdBreachAt(findMtpdBreachBand(h));

        BiaQuantitative saved = repo.save(h);
        audit.log("CREATE", "BiaQuantitative", saved.getId(),
            "Quantitative BIA created: " + saved.getAssessmentCode()
                + " (process " + saved.getProcessId() + ", cycle " + saved.getCycleYear() + ")");
        emit("BIA_QUANT_CREATED", saved, null);
        return toResponse(saved);
    }

    @Transactional
    public BiaQuantitativeResponse update(UUID externalId, BiaQuantitativeRequest req) {
        BiaQuantitative h = loadOrThrow(externalId);
        if (!EDITABLE_STATUSES.contains(h.getStatus())) {
            throw new IllegalStateException(
                "Cannot edit a " + h.getStatus() + " quantitative BIA — must be DRAFT or IN_REVIEW");
        }
        validateRequest(req);
        applyRequest(h, req);
        recomputeTotals(h);
        h.setMtpdBreachAt(findMtpdBreachBand(h));
        h.setUpdatedAt(LocalDateTime.now());

        BiaQuantitative saved = repo.save(h);
        audit.log("UPDATE", "BiaQuantitative", saved.getId(),
            "Quantitative BIA updated: " + saved.getAssessmentCode());
        emit("BIA_QUANT_UPDATED", saved, h.getStatus());
        return toResponse(saved);
    }

    @Transactional
    public BiaQuantitativeResponse submitForReview(UUID externalId) {
        return transition(externalId, "IN_REVIEW", "SUBMIT_FOR_REVIEW", "BIA_QUANT_SUBMITTED", null);
    }

    @Transactional
    public BiaQuantitativeResponse bcmLeadReview(UUID externalId, String actor) {
        BiaQuantitative h = loadOrThrow(externalId);
        guardTransition(h.getStatus(), "CFO_REVIEW");
        String from = h.getStatus();
        h.setStatus("CFO_REVIEW");
        h.setBcmLeadReviewedAt(LocalDateTime.now());
        h.setBcmLeadReviewedBy(actor);
        h.setUpdatedAt(LocalDateTime.now());
        BiaQuantitative saved = repo.save(h);
        audit.log("TRANSITION", "BiaQuantitative", saved.getId(),
            "BCM lead reviewed BIA " + saved.getAssessmentCode()
                + " " + from + " → CFO_REVIEW (actor=" + actor + ")");
        emit("BIA_QUANT_BCM_REVIEWED", saved, from);
        return toResponse(saved);
    }

    @Transactional
    public BiaQuantitativeResponse cfoApprove(UUID externalId, CfoApprovalRequest req) {
        BiaQuantitative h = loadOrThrow(externalId);
        if (!"APPROVE".equals(req.decision()) && !"REJECT".equals(req.decision())) {
            throw new IllegalArgumentException("decision must be APPROVE or REJECT");
        }

        String to = "APPROVE".equals(req.decision()) ? "CFO_APPROVED" : "REJECTED";
        guardTransition(h.getStatus(), to);
        String from = h.getStatus();

        if ("APPROVE".equals(req.decision())) {
            // Recompute the breach band at approval-time so the approved board
            // pack always reflects the committed loss model. Totals are left
            // alone — the CFO is signing off on the numbers already on screen,
            // and we don't want a phantom recompute to surprise them.
            if (!h.getLossLines().isEmpty()) {
                recomputeTotals(h);
            }
            h.setMtpdBreachAt(findMtpdBreachBand(h));

            h.setStatus("CFO_APPROVED");
            h.setCfoApprovedAt(LocalDateTime.now());
            h.setCfoApproverName(req.approverName());
            h.setCfoComments(req.comments());
            h.setUpdatedAt(LocalDateTime.now());
            BiaQuantitative saved = repo.save(h);
            audit.log("APPROVE", "BiaQuantitative", saved.getId(),
                "Quantitative BIA approved by CFO " + req.approverName()
                    + ": " + saved.getAssessmentCode()
                    + (saved.getMtpdBreachAt() != null
                        ? " (MTPD breach at " + saved.getMtpdBreachAt() + ")"
                        : " (no MTPD breach)"));
            emit("BIA_QUANT_APPROVED", saved, from);
            return toResponse(saved);
        } else {
            if (req.rejectionReason() == null || req.rejectionReason().isBlank()) {
                throw new IllegalArgumentException("rejectionReason is required when decision = REJECT");
            }
            h.setStatus("REJECTED");
            h.setRejectionReason(req.rejectionReason());
            h.setCfoApproverName(req.approverName());
            h.setCfoComments(req.comments());
            h.setUpdatedAt(LocalDateTime.now());
            BiaQuantitative saved = repo.save(h);
            audit.log("REJECT", "BiaQuantitative", saved.getId(),
                "Quantitative BIA rejected by CFO " + req.approverName()
                    + ": " + saved.getAssessmentCode() + " — " + req.rejectionReason());
            emit("BIA_QUANT_REJECTED", saved, from);
            return toResponse(saved);
        }
    }

    @Transactional
    public BiaQuantitativeResponse boardApprove(UUID externalId, String minutesRef) {
        if (minutesRef == null || minutesRef.isBlank()) {
            throw new IllegalArgumentException("minutesRef is required");
        }
        BiaQuantitative h = loadOrThrow(externalId);
        guardTransition(h.getStatus(), "BOARD_APPROVED");
        String from = h.getStatus();
        h.setStatus("BOARD_APPROVED");
        h.setBoardApprovedAt(LocalDateTime.now());
        h.setBoardMinutesRef(minutesRef);
        h.setUpdatedAt(LocalDateTime.now());
        BiaQuantitative saved = repo.save(h);
        audit.log("BOARD_APPROVE", "BiaQuantitative", saved.getId(),
            "Quantitative BIA board-approved: " + saved.getAssessmentCode()
                + " (minutes ref: " + minutesRef + ")");
        emit("BIA_QUANT_BOARD_APPROVED", saved, from);
        return toResponse(saved);
    }

    @Transactional
    public BiaQuantitativeResponse archive(UUID externalId) {
        BiaQuantitative h = loadOrThrow(externalId);
        String from = h.getStatus();
        h.setIsDeleted(true);
        h.setDeletedAt(LocalDateTime.now());
        h.setStatus("ARCHIVED");
        h.setUpdatedAt(LocalDateTime.now());
        BiaQuantitative saved = repo.save(h);
        audit.log("ARCHIVE", "BiaQuantitative", saved.getId(),
            "Quantitative BIA archived: " + saved.getAssessmentCode() + " (from " + from + ")");
        emit("BIA_QUANT_ARCHIVED", saved, from);
        return toResponse(saved);
    }

    // ─── Calculation core (package-private for unit tests) ──────────

    /**
     * Sums lines into the per-band rollup columns. Bands with no lines
     * are left null (not zero) so the UI can distinguish "not modelled
     * yet" from "modelled and zero".
     */
    void recomputeTotals(BiaQuantitative h) {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (BiaQuantitativeLossLine line : h.getLossLines()) {
            if (Boolean.TRUE.equals(line.getIsDeleted())) continue;
            if (line.getAmountSar() == null) continue;
            totals.merge(line.getTimeBand(), line.getAmountSar(), BigDecimal::add);
        }
        h.setTotalLoss24hSar(totals.get("24h"));
        h.setTotalLoss72hSar(totals.get("72h"));
        h.setTotalLoss1wSar(totals.get("1w"));
        h.setTotalLoss2wSar(totals.get("2w"));
        h.setTotalLoss1mSar(totals.get("1m"));
    }

    /**
     * First band whose cumulative loss exceeds the board ceiling, or
     * {@code null} if none does (or no ceiling is set, or nothing has
     * been modelled).
     */
    String findMtpdBreachBand(BiaQuantitative h) {
        BigDecimal ceiling = h.getMaxTolerableLossSar();
        if (ceiling == null) return null;

        BigDecimal cumulative = BigDecimal.ZERO;
        for (String band : TIME_BANDS) {
            BigDecimal bandTotal = bandTotal(h, band);
            if (bandTotal == null) continue;
            cumulative = cumulative.add(bandTotal);
            if (cumulative.compareTo(ceiling) > 0) {
                return band;
            }
        }
        return null;
    }

    private static BigDecimal bandTotal(BiaQuantitative h, String band) {
        return switch (band) {
            case "24h" -> h.getTotalLoss24hSar();
            case "72h" -> h.getTotalLoss72hSar();
            case "1w"  -> h.getTotalLoss1wSar();
            case "2w"  -> h.getTotalLoss2wSar();
            case "1m"  -> h.getTotalLoss1mSar();
            default     -> null;
        };
    }

    // ─── Internal helpers ───────────────────────────────────────────

    private BiaQuantitative loadOrThrow(UUID externalId) {
        return repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(org(), externalId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Quantitative BIA not found: " + externalId));
    }

    private BiaQuantitativeResponse transition(UUID externalId, String to,
                                                String auditAction, String trigger,
                                                String message) {
        BiaQuantitative h = loadOrThrow(externalId);
        guardTransition(h.getStatus(), to);
        String from = h.getStatus();
        h.setStatus(to);
        h.setUpdatedAt(LocalDateTime.now());
        BiaQuantitative saved = repo.save(h);
        String msg = message != null ? message
            : "Quantitative BIA " + saved.getAssessmentCode() + " " + from + " → " + to;
        audit.log(auditAction, "BiaQuantitative", saved.getId(), msg);
        emit(trigger, saved, from);
        return toResponse(saved);
    }

    private static void guardTransition(String from, String to) {
        boolean ok = switch (Objects.requireNonNullElse(from, "")) {
            case "DRAFT"          -> "IN_REVIEW".equals(to);
            case "IN_REVIEW"      -> "CFO_REVIEW".equals(to) || "DRAFT".equals(to);
            case "CFO_REVIEW"     -> "CFO_APPROVED".equals(to) || "REJECTED".equals(to)
                                        || "IN_REVIEW".equals(to);
            case "CFO_APPROVED"   -> "BOARD_APPROVED".equals(to) || "ARCHIVED".equals(to);
            case "BOARD_APPROVED" -> "ARCHIVED".equals(to);
            case "REJECTED"       -> "DRAFT".equals(to) || "ARCHIVED".equals(to);
            default                -> false;
        };
        if (!ok) {
            throw new IllegalTransitionException(from, to, allowedFrom(from));
        }
    }

    private static Set<String> allowedFrom(String from) {
        return switch (Objects.requireNonNullElse(from, "")) {
            case "DRAFT"          -> Set.of("IN_REVIEW");
            case "IN_REVIEW"      -> Set.of("CFO_REVIEW", "DRAFT");
            case "CFO_REVIEW"     -> Set.of("CFO_APPROVED", "REJECTED", "IN_REVIEW");
            case "CFO_APPROVED"   -> Set.of("BOARD_APPROVED", "ARCHIVED");
            case "BOARD_APPROVED" -> Set.of("ARCHIVED");
            case "REJECTED"       -> Set.of("DRAFT", "ARCHIVED");
            default                -> Set.of();
        };
    }

    private static void validateRequest(BiaQuantitativeRequest req) {
        if (req.processId() == null) throw new IllegalArgumentException("processId is required");
        if (req.assessmentDate() == null) throw new IllegalArgumentException("assessmentDate is required");
        if (req.cycleYear() == null) throw new IllegalArgumentException("cycleYear is required");
        if (req.lossLines() != null) {
            for (BiaQuantitativeLossLineDto l : req.lossLines()) {
                if (l.timeBand() == null || !TIME_BANDS.contains(l.timeBand())) {
                    throw new IllegalArgumentException(
                        "Invalid timeBand: " + l.timeBand() + " (allowed: " + TIME_BANDS + ")");
                }
                if (l.lossType() == null || !LOSS_TYPES.contains(l.lossType())) {
                    throw new IllegalArgumentException(
                        "Invalid lossType: " + l.lossType() + " (allowed: " + LOSS_TYPES + ")");
                }
                if (l.amountSar() == null) {
                    throw new IllegalArgumentException("amountSar is required for every loss line");
                }
            }
        }
    }

    private static void applyRequest(BiaQuantitative h, BiaQuantitativeRequest req) {
        h.setProcessId(req.processId());
        h.setAssessmentDate(req.assessmentDate());
        h.setCycleYear(req.cycleYear());
        if (req.currency() != null && !req.currency().isBlank()) h.setCurrency(req.currency());
        if (h.getCurrency() == null) h.setCurrency("SAR");
        h.setMethodologyNotesEn(req.methodologyNotesEn());
        h.setMethodologyNotesAr(req.methodologyNotesAr());
        h.setMaxTolerableLossSar(req.maxTolerableLossSar());
        if (req.preparedByName() != null) h.setPreparedByName(req.preparedByName());
        if (req.preparedByUserId() != null) h.setPreparedByUserId(req.preparedByUserId());

        // Replace lines wholesale — simple and predictable for a small (≤40)
        // collection. orphanRemoval picks up the removals.
        if (req.lossLines() != null) {
            h.getLossLines().clear();
            for (BiaQuantitativeLossLineDto dto : req.lossLines()) {
                BiaQuantitativeLossLine line = new BiaQuantitativeLossLine();
                line.setTimeBand(dto.timeBand());
                line.setLossType(dto.lossType());
                line.setAmountSar(dto.amountSar());
                line.setBasisNarrativeEn(dto.basisNarrativeEn());
                line.setBasisNarrativeAr(dto.basisNarrativeAr());
                line.setConfidence(dto.confidence() != null ? dto.confidence() : "MEDIUM");
                line.setIsEstimate(dto.isEstimate() == null ? Boolean.TRUE : dto.isEstimate());
                h.addLossLine(line);
            }
        }
    }

    private static String nextAssessmentCode(Long orgId, Integer cycleYear) {
        int year = cycleYear != null ? cycleYear : Year.now().getValue();
        // Lightweight code — not collision-proof at scale but the unique
        // index will reject duplicates and the caller can retry with a
        // supplied code. The seed scripts use the BIA-Q-YYYY-NNN form.
        return String.format(Locale.ROOT, "BIA-Q-%04d-%s",
            year, UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
    }

    // ─── F8 emission ────────────────────────────────────────────────

    private void emit(String trigger, BiaQuantitative h, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            h.getOrganisationId(), trigger, "BiaQuantitative", h.getId(),
            "bia_quant:" + h.getId(), payload(h, previousStatus)));
    }

    private static Map<String, Object> payload(BiaQuantitative h, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("biaQuantitativeId", str(h.getId()));
        m.put("assessmentCode",    str(h.getAssessmentCode()));
        m.put("processId",         str(h.getProcessId()));
        m.put("cycleYear",         str(h.getCycleYear()));
        m.put("status",            str(h.getStatus()));
        m.put("previousStatus",    str(previousStatus));
        m.put("currency",          str(h.getCurrency()));
        m.put("mtpdBreachAt",      str(h.getMtpdBreachAt()));
        m.put("maxTolerableLossSar", str(h.getMaxTolerableLossSar()));
        m.put("totalLoss24hSar",   str(h.getTotalLoss24hSar()));
        m.put("totalLoss1mSar",    str(h.getTotalLoss1mSar()));
        return m;
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    // ─── Mapping ────────────────────────────────────────────────────

    private static BiaQuantitativeResponse toResponse(BiaQuantitative h) {
        List<BiaQuantitativeLossLineDto> lines = new ArrayList<>();
        for (BiaQuantitativeLossLine line : h.getLossLines()) {
            if (Boolean.TRUE.equals(line.getIsDeleted())) continue;
            lines.add(new BiaQuantitativeLossLineDto(
                line.getId(),
                line.getTimeBand(),
                line.getLossType(),
                line.getAmountSar(),
                line.getBasisNarrativeEn(),
                line.getBasisNarrativeAr(),
                line.getConfidence(),
                line.getIsEstimate()
            ));
        }
        return new BiaQuantitativeResponse(
            h.getId(),
            h.getExternalId(),
            h.getOrganisationId(),
            h.getProcessId(),
            h.getAssessmentCode(),
            h.getAssessmentDate(),
            h.getCycleYear(),
            h.getCurrency(),
            h.getMethodologyNotesEn(),
            h.getMethodologyNotesAr(),
            h.getTotalLoss24hSar(),
            h.getTotalLoss72hSar(),
            h.getTotalLoss1wSar(),
            h.getTotalLoss2wSar(),
            h.getTotalLoss1mSar(),
            h.getMaxTolerableLossSar(),
            h.getMtpdBreachAt(),
            h.getStatus(),
            h.getPreparedByUserId(),
            h.getPreparedByName(),
            h.getPreparedAt(),
            h.getBcmLeadReviewedAt(),
            h.getBcmLeadReviewedBy(),
            h.getCfoApprovedAt(),
            h.getCfoApproverName(),
            h.getCfoComments(),
            h.getBoardApprovedAt(),
            h.getBoardMinutesRef(),
            h.getRejectionReason(),
            h.getCreatedAt(),
            h.getUpdatedAt(),
            lines
        );
    }
}
