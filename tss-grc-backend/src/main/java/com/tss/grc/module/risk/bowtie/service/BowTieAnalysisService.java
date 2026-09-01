package com.tss.grc.module.risk.bowtie.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.ApproveRequest;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieAnalysisRequest;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieAnalysisResponse;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieConsequenceDto;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieFullResponse;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieMitigatingControlDto;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTiePreventiveControlDto;
import com.tss.grc.module.risk.bowtie.dto.BowTieDtos.BowTieThreatDto;
import com.tss.grc.module.risk.bowtie.entity.BowTieAnalysis;
import com.tss.grc.module.risk.bowtie.entity.BowTieConsequence;
import com.tss.grc.module.risk.bowtie.entity.BowTieMitigatingControl;
import com.tss.grc.module.risk.bowtie.entity.BowTiePreventiveControl;
import com.tss.grc.module.risk.bowtie.entity.BowTieThreat;
import com.tss.grc.module.risk.bowtie.repository.BowTieAnalysisRepository;
import com.tss.grc.module.risk.bowtie.repository.BowTieConsequenceRepository;
import com.tss.grc.module.risk.bowtie.repository.BowTieMitigatingControlRepository;
import com.tss.grc.module.risk.bowtie.repository.BowTiePreventiveControlRepository;
import com.tss.grc.module.risk.bowtie.repository.BowTieThreatRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lifecycle service for {@link BowTieAnalysis} and its four children.
 *
 * <p>State machine:
 * <pre>
 *   DRAFT ─submitForReview()─▶ IN_REVIEW ─approve()─▶ APPROVED
 *                                                  └─archive()─▶ ARCHIVED
 *   (any state) ─archive()─▶ ARCHIVED   (soft-delete)
 * </pre>
 *
 * <p>F8 events emitted:
 * <ul>
 *   <li>BOWTIE_CREATED — on create()</li>
 *   <li>BOWTIE_UPDATED — on update()</li>
 *   <li>BOWTIE_SUBMITTED — DRAFT → IN_REVIEW</li>
 *   <li>BOWTIE_APPROVED — IN_REVIEW → APPROVED (with overall effectiveness)</li>
 *   <li>BOWTIE_ARCHIVED — soft-delete</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BowTieAnalysisService {

    /** Effectiveness rating → weight (0–100). */
    private static final Map<String, Integer> EFFECTIVENESS_WEIGHT = Map.of(
            "VERY_LOW", 10,
            "LOW",      30,
            "MEDIUM",   50,
            "HIGH",     70,
            "VERY_HIGH", 90
    );

    /** Standard ISO 31010 5×5 severity / likelihood ordering. */
    private static final List<String> LIKELIHOOD_ORDER = List.of(
            "RARE", "UNLIKELY", "POSSIBLE", "LIKELY", "ALMOST_CERTAIN");
    private static final List<String> SEVERITY_ORDER = List.of(
            "INSIGNIFICANT", "MINOR", "MODERATE", "MAJOR", "CATASTROPHIC");

    private final BowTieAnalysisRepository bowtieRepo;
    private final BowTieThreatRepository threatRepo;
    private final BowTieConsequenceRepository consequenceRepo;
    private final BowTiePreventiveControlRepository preventiveRepo;
    private final BowTieMitigatingControlRepository mitigatingRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BowTieAnalysisResponse> list() {
        Long orgId = TenantContext.getCurrentTenant();
        return bowtieRepo
                .findByOrganisationIdAndIsDeletedFalseOrderByAssessmentDateDesc(orgId)
                .stream()
                .map(BowTieAnalysisResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BowTieFullResponse getFull(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        BowTieAnalysis b = mustGet(externalId);

        List<BowTieThreat> threats = threatRepo
                .findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByThreatOrderAsc(orgId, b.getId());
        List<BowTieConsequence> consequences = consequenceRepo
                .findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByConsequenceOrderAsc(orgId, b.getId());
        List<BowTiePreventiveControl> preventives = preventiveRepo
                .findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(orgId, b.getId());
        List<BowTieMitigatingControl> mitigatings = mitigatingRepo
                .findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(orgId, b.getId());

        // Index threat id → external id so the preventive-control DTO can
        // carry the threat's UUID (frontend keys controls by external id,
        // not surrogate id — keeps SVG rendering stable across re-saves).
        Map<Long, UUID> threatExt = new HashMap<>();
        for (BowTieThreat t : threats) threatExt.put(t.getId(), t.getExternalId());
        Map<Long, UUID> consExt = new HashMap<>();
        for (BowTieConsequence c : consequences) consExt.put(c.getId(), c.getExternalId());

        return new BowTieFullResponse(
                BowTieAnalysisResponse.from(b),
                threats.stream().map(BowTieThreatDto::from).toList(),
                consequences.stream().map(BowTieConsequenceDto::from).toList(),
                preventives.stream()
                        .map(p -> BowTiePreventiveControlDto.from(p, threatExt.get(p.getThreatId())))
                        .toList(),
                mitigatings.stream()
                        .map(m -> BowTieMitigatingControlDto.from(m, consExt.get(m.getConsequenceId())))
                        .toList()
        );
    }

    // ─── Writes ───────────────────────────────────────────────────

    @Transactional
    public BowTieAnalysisResponse create(BowTieAnalysisRequest req) {
        Long orgId = TenantContext.getCurrentTenant();

        BowTieAnalysis b = new BowTieAnalysis();
        b.setOrganisationId(orgId);
        b.setBowtieCode(req.bowtieCode() == null || req.bowtieCode().isBlank()
                ? generateCode(orgId)
                : req.bowtieCode());
        applyHeaderFields(b, req);
        if (b.getAssessmentDate() == null) b.setAssessmentDate(LocalDate.now());
        if (b.getMethodology() == null || b.getMethodology().isBlank()) b.setMethodology("ISO_31010");
        b.setStatus("DRAFT");
        b = bowtieRepo.save(b);

        // Cascade children if the request carries them.
        if (req.threats() != null) for (BowTieThreatDto t : req.threats()) saveNewThreat(b, t);
        if (req.consequences() != null) for (BowTieConsequenceDto c : req.consequences()) saveNewConsequence(b, c);
        // F2-413: this comment used to read "Preventive + mitigating controls require parent
        // UUIDs to be present — when the cascade is used these must be supplied in the same
        // request body (frontend pre-mints UUIDs locally)", which described a capability the code
        // does not have: neither list was ever read, here or in update(). Worse than an inert
        // field, because it explained a mechanism (pre-minted UUIDs) that nothing consumes
        // (F2-184). The two request components are gone; controls are created through their own
        // routes, which resolve the parent from the path rather than from a client-minted UUID.

        audit.log("CREATE", "BowTieAnalysis", b.getId(),
                "Bow-tie " + b.getBowtieCode() + " created · " + safe(b.getTopEventTitleEn()));
        eventPublisher.publishEvent(new DomainEvent(
                orgId, "BOWTIE_CREATED",
                "BowTieAnalysis", b.getId(), b.getBowtieCode(),
                payloadFor(b, null)));
        return BowTieAnalysisResponse.from(b);
    }

    @Transactional
    public BowTieAnalysisResponse update(UUID externalId, BowTieAnalysisRequest req) {
        BowTieAnalysis b = mustGet(externalId);
        if (!"DRAFT".equals(b.getStatus()) && !"IN_REVIEW".equals(b.getStatus())) {
            throw new IllegalTransitionException(b.getStatus(), "UPDATED", Set.of("DRAFT", "IN_REVIEW"));
        }
        applyHeaderFields(b, req);
        b = bowtieRepo.save(b);
        audit.log("UPDATE", "BowTieAnalysis", b.getId(),
                "Bow-tie " + b.getBowtieCode() + " updated");
        eventPublisher.publishEvent(new DomainEvent(
                b.getOrganisationId(), "BOWTIE_UPDATED",
                "BowTieAnalysis", b.getId(), b.getBowtieCode(),
                payloadFor(b, null)));
        return BowTieAnalysisResponse.from(b);
    }

    @Transactional
    public BowTieThreatDto addThreat(UUID bowtieExternalId, BowTieThreatDto dto) {
        BowTieAnalysis b = mustGet(bowtieExternalId);
        BowTieThreat t = saveNewThreat(b, dto);
        audit.log("CREATE", "BowTieThreat", t.getId(),
                "Threat added to bow-tie " + b.getBowtieCode() + " · " + safe(t.getTitleEn()));
        return BowTieThreatDto.from(t);
    }

    @Transactional
    public void removeThreat(UUID threatExternalId) {
        Long orgId = TenantContext.getCurrentTenant();
        BowTieThreat t = threatRepo
                .findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, threatExternalId)
                .orElseThrow(() -> new EntityNotFoundException("BowTieThreat not found: " + threatExternalId));
        t.setIsDeleted(true);
        t.setDeletedAt(LocalDateTime.now());
        threatRepo.save(t);
        // Soft-delete any preventive controls attached to this threat — the
        // chain stops here; orphan controls would confuse the renderer.
        for (BowTiePreventiveControl c : preventiveRepo
                .findByOrganisationIdAndThreatIdAndIsDeletedFalseOrderByControlOrderAsc(orgId, t.getId())) {
            c.setIsDeleted(true);
            c.setDeletedAt(LocalDateTime.now());
            preventiveRepo.save(c);
        }
        audit.log("DELETE", "BowTieThreat", t.getId(),
                "Threat removed · " + safe(t.getTitleEn()));
    }

    @Transactional
    public BowTieConsequenceDto addConsequence(UUID bowtieExternalId, BowTieConsequenceDto dto) {
        BowTieAnalysis b = mustGet(bowtieExternalId);
        BowTieConsequence c = saveNewConsequence(b, dto);
        audit.log("CREATE", "BowTieConsequence", c.getId(),
                "Consequence added to bow-tie " + b.getBowtieCode() + " · " + safe(c.getTitleEn()));
        return BowTieConsequenceDto.from(c);
    }

    @Transactional
    public void removeConsequence(UUID consequenceExternalId) {
        Long orgId = TenantContext.getCurrentTenant();
        BowTieConsequence c = consequenceRepo
                .findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, consequenceExternalId)
                .orElseThrow(() -> new EntityNotFoundException("BowTieConsequence not found: " + consequenceExternalId));
        c.setIsDeleted(true);
        c.setDeletedAt(LocalDateTime.now());
        consequenceRepo.save(c);
        for (BowTieMitigatingControl m : mitigatingRepo
                .findByOrganisationIdAndConsequenceIdAndIsDeletedFalseOrderByControlOrderAsc(orgId, c.getId())) {
            m.setIsDeleted(true);
            m.setDeletedAt(LocalDateTime.now());
            mitigatingRepo.save(m);
        }
        audit.log("DELETE", "BowTieConsequence", c.getId(),
                "Consequence removed · " + safe(c.getTitleEn()));
    }

    @Transactional
    public BowTiePreventiveControlDto addPreventiveControl(UUID threatExternalId, BowTiePreventiveControlDto dto) {
        Long orgId = TenantContext.getCurrentTenant();
        BowTieThreat t = threatRepo
                .findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, threatExternalId)
                .orElseThrow(() -> new EntityNotFoundException("BowTieThreat not found: " + threatExternalId));
        BowTiePreventiveControl c = new BowTiePreventiveControl();
        c.setOrganisationId(orgId);
        c.setBowtieId(t.getBowtieId());
        c.setThreatId(t.getId());
        applyPreventive(c, dto);
        c = preventiveRepo.save(c);
        audit.log("CREATE", "BowTiePreventiveControl", c.getId(),
                "Preventive control added · " + safe(c.getTitleEn()));
        return BowTiePreventiveControlDto.from(c, t.getExternalId());
    }

    @Transactional
    public BowTieMitigatingControlDto addMitigatingControl(UUID consequenceExternalId, BowTieMitigatingControlDto dto) {
        Long orgId = TenantContext.getCurrentTenant();
        BowTieConsequence cons = consequenceRepo
                .findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, consequenceExternalId)
                .orElseThrow(() -> new EntityNotFoundException("BowTieConsequence not found: " + consequenceExternalId));
        BowTieMitigatingControl c = new BowTieMitigatingControl();
        c.setOrganisationId(orgId);
        c.setBowtieId(cons.getBowtieId());
        c.setConsequenceId(cons.getId());
        applyMitigating(c, dto);
        c = mitigatingRepo.save(c);
        audit.log("CREATE", "BowTieMitigatingControl", c.getId(),
                "Mitigating control added · " + safe(c.getTitleEn()));
        return BowTieMitigatingControlDto.from(c, cons.getExternalId());
    }

    // ─── Lifecycle transitions ────────────────────────────────────

    @Transactional
    public BowTieAnalysisResponse submitForReview(UUID externalId) {
        BowTieAnalysis b = mustGet(externalId);
        if (!"DRAFT".equals(b.getStatus())) {
            throw new IllegalTransitionException(b.getStatus(), "IN_REVIEW", Set.of("DRAFT"));
        }
        String prev = b.getStatus();
        b.setStatus("IN_REVIEW");
        b = bowtieRepo.save(b);
        audit.log("TRANSITION", "BowTieAnalysis", b.getId(),
                "Bow-tie " + b.getBowtieCode() + " " + prev + " → IN_REVIEW");
        eventPublisher.publishEvent(new DomainEvent(
                b.getOrganisationId(), "BOWTIE_SUBMITTED",
                "BowTieAnalysis", b.getId(), b.getBowtieCode(),
                payloadFor(b, prev)));
        return BowTieAnalysisResponse.from(b);
    }

    @Transactional
    public BowTieAnalysisResponse approve(UUID externalId, ApproveRequest req) {
        BowTieAnalysis b = mustGet(externalId);
        if (!"IN_REVIEW".equals(b.getStatus())) {
            throw new IllegalTransitionException(b.getStatus(), "APPROVED", Set.of("IN_REVIEW"));
        }
        String prev = b.getStatus();
        b.setStatus("APPROVED");
        b.setApprovedAt(LocalDateTime.now());
        // F2-223 — the approver is the authenticated principal. The old form was conditional, so
        // omitting the field left the PREVIOUS approver's name against this approval, which is a
        // second way for the record to name somebody who did not perform the act.
        b.setApprovedByName(CurrentActor.username());
        // Recompute scores now that the bow-tie is being signed off.
        recomputeOverallEffectiveness(b);
        b.setOverallResidualRating(inferResidualRating(b));
        b = bowtieRepo.save(b);
        audit.log("TRANSITION", "BowTieAnalysis", b.getId(),
                "Bow-tie " + b.getBowtieCode() + " approved · effectiveness="
                        + b.getOverallControlEffectiveness()
                        + " · residual=" + b.getOverallResidualRating());
        eventPublisher.publishEvent(new DomainEvent(
                b.getOrganisationId(), "BOWTIE_APPROVED",
                "BowTieAnalysis", b.getId(), b.getBowtieCode(),
                payloadFor(b, prev)));
        return BowTieAnalysisResponse.from(b);
    }

    @Transactional
    public void archive(UUID externalId) {
        BowTieAnalysis b = mustGet(externalId);
        String prev = b.getStatus();
        b.setStatus("ARCHIVED");
        b.setIsDeleted(true);
        b.setDeletedAt(LocalDateTime.now());
        b = bowtieRepo.save(b);
        audit.log("DELETE", "BowTieAnalysis", b.getId(),
                "Bow-tie " + b.getBowtieCode() + " archived (was " + prev + ")");
        eventPublisher.publishEvent(new DomainEvent(
                b.getOrganisationId(), "BOWTIE_ARCHIVED",
                "BowTieAnalysis", b.getId(), b.getBowtieCode(),
                payloadFor(b, prev)));
    }

    // ─── Computation ──────────────────────────────────────────────

    /**
     * Compute overall control effectiveness as a 0–100 score.
     *
     * <p>Each control's effectiveness is weighted (VERY_LOW=10, LOW=30,
     * MEDIUM=50, HIGH=70, VERY_HIGH=90). The bow-tie's score is the
     * average of preventive-control weights blended 50/50 with the
     * average of mitigating-control weights — that way one side being
     * empty doesn't drag the whole score to zero, but having strong
     * controls only on one side is properly penalised.
     */
    public void recomputeOverallEffectiveness(BowTieAnalysis b) {
        Long orgId = b.getOrganisationId();
        List<BowTiePreventiveControl> preventives = preventiveRepo
                .findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(orgId, b.getId());
        List<BowTieMitigatingControl> mitigatings = mitigatingRepo
                .findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(orgId, b.getId());

        double pAvg = avgEffectiveness(preventives.stream().map(BowTiePreventiveControl::getEffectiveness).toList());
        double mAvg = avgEffectiveness(mitigatings.stream().map(BowTieMitigatingControl::getEffectiveness).toList());

        double blended;
        if (preventives.isEmpty() && mitigatings.isEmpty()) {
            blended = 0.0;
        } else if (preventives.isEmpty()) {
            blended = mAvg;     // only one side has data — use it directly
        } else if (mitigatings.isEmpty()) {
            blended = pAvg;
        } else {
            blended = (pAvg + mAvg) / 2.0;
        }
        b.setOverallControlEffectiveness(BigDecimal.valueOf(blended).setScale(2, RoundingMode.HALF_UP));
    }

    private static double avgEffectiveness(List<String> ratings) {
        if (ratings == null || ratings.isEmpty()) return 0.0;
        int sum = 0, n = 0;
        for (String r : ratings) {
            Integer w = EFFECTIVENESS_WEIGHT.get(r);
            if (w != null) { sum += w; n++; }
        }
        return n == 0 ? 0.0 : (double) sum / n;
    }

    /**
     * Derive overall residual rating from worst residual likelihood across
     * threats × worst residual severity across consequences using the
     * standard 5×5 matrix. Returns CRITICAL/HIGH/MEDIUM/LOW.
     */
    public String inferResidualRating(BowTieAnalysis b) {
        Long orgId = b.getOrganisationId();
        List<BowTieThreat> threats = threatRepo
                .findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByThreatOrderAsc(orgId, b.getId());
        List<BowTieConsequence> consequences = consequenceRepo
                .findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByConsequenceOrderAsc(orgId, b.getId());

        int worstL = worstIndex(threats.stream().map(BowTieThreat::getResidualLikelihood).toList(), LIKELIHOOD_ORDER);
        int worstS = worstIndex(consequences.stream().map(BowTieConsequence::getResidualSeverity).toList(), SEVERITY_ORDER);
        if (worstL < 0 || worstS < 0) return null;

        int lScore = worstL + 1;   // 1..5
        int sScore = worstS + 1;   // 1..5
        int product = lScore * sScore;
        // Standard 5×5 bandings: 1–4 LOW, 5–9 MEDIUM, 10–15 HIGH, 16+ CRITICAL.
        if (product >= 16) return "CRITICAL";
        if (product >= 10) return "HIGH";
        if (product >= 5)  return "MEDIUM";
        return "LOW";
    }

    private static int worstIndex(List<String> values, List<String> order) {
        int worst = -1;
        for (String v : values) {
            if (v == null) continue;
            int idx = order.indexOf(v);
            if (idx > worst) worst = idx;
        }
        return worst;
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private BowTieAnalysis mustGet(UUID externalId) {
        Long orgId = TenantContext.getCurrentTenant();
        return bowtieRepo
                .findByOrganisationIdAndExternalIdAndIsDeletedFalse(orgId, externalId)
                .orElseThrow(() -> new EntityNotFoundException("BowTieAnalysis not found: " + externalId));
    }

    private void applyHeaderFields(BowTieAnalysis b, BowTieAnalysisRequest req) {
        if (req.topEventTitleEn() != null) b.setTopEventTitleEn(req.topEventTitleEn());
        if (req.topEventTitleAr() != null) b.setTopEventTitleAr(req.topEventTitleAr());
        if (req.topEventDescriptionEn() != null) b.setTopEventDescriptionEn(req.topEventDescriptionEn());
        if (req.topEventDescriptionAr() != null) b.setTopEventDescriptionAr(req.topEventDescriptionAr());
        if (req.riskCategory() != null) b.setRiskCategory(req.riskCategory());
        if (req.assessmentDate() != null) b.setAssessmentDate(req.assessmentDate());
        if (req.assessedByName() != null) b.setAssessedByName(req.assessedByName());
        if (req.methodology() != null) b.setMethodology(req.methodology());
        if (req.linkedRiskId() != null) b.setLinkedRiskId(req.linkedRiskId());
        if (req.nextReviewAt() != null) b.setNextReviewAt(req.nextReviewAt());
    }

    private BowTieThreat saveNewThreat(BowTieAnalysis b, BowTieThreatDto dto) {
        BowTieThreat t = new BowTieThreat();
        t.setOrganisationId(b.getOrganisationId());
        t.setBowtieId(b.getId());
        t.setThreatOrder(dto.threatOrder() == null ? 0 : dto.threatOrder());
        t.setTitleEn(dto.titleEn());
        t.setTitleAr(dto.titleAr());
        t.setDescriptionEn(dto.descriptionEn());
        t.setDescriptionAr(dto.descriptionAr());
        t.setInherentLikelihood(dto.inherentLikelihood());
        t.setResidualLikelihood(dto.residualLikelihood());
        t.setSource(dto.source());
        return threatRepo.save(t);
    }

    private BowTieConsequence saveNewConsequence(BowTieAnalysis b, BowTieConsequenceDto dto) {
        BowTieConsequence c = new BowTieConsequence();
        c.setOrganisationId(b.getOrganisationId());
        c.setBowtieId(b.getId());
        c.setConsequenceOrder(dto.consequenceOrder() == null ? 0 : dto.consequenceOrder());
        c.setTitleEn(dto.titleEn());
        c.setTitleAr(dto.titleAr());
        c.setDescriptionEn(dto.descriptionEn());
        c.setDescriptionAr(dto.descriptionAr());
        c.setConsequenceType(dto.consequenceType());
        c.setInherentSeverity(dto.inherentSeverity());
        c.setResidualSeverity(dto.residualSeverity());
        c.setEstimatedLossSar(dto.estimatedLossSar());
        return consequenceRepo.save(c);
    }

    private void applyPreventive(BowTiePreventiveControl c, BowTiePreventiveControlDto dto) {
        c.setControlOrder(dto.controlOrder() == null ? 0 : dto.controlOrder());
        c.setTitleEn(dto.titleEn());
        c.setTitleAr(dto.titleAr());
        c.setDescriptionEn(dto.descriptionEn());
        c.setDescriptionAr(dto.descriptionAr());
        c.setControlType(dto.controlType());
        c.setLinkedControlId(dto.linkedControlId());
        c.setEffectiveness(dto.effectiveness() == null ? "MEDIUM" : dto.effectiveness());
        c.setLastTestedAt(dto.lastTestedAt());
        c.setLastTestResult(dto.lastTestResult());
        c.setResponsibleRole(dto.responsibleRole());
        c.setResponsibleOwner(dto.responsibleOwner());
    }

    private void applyMitigating(BowTieMitigatingControl c, BowTieMitigatingControlDto dto) {
        c.setControlOrder(dto.controlOrder() == null ? 0 : dto.controlOrder());
        c.setTitleEn(dto.titleEn());
        c.setTitleAr(dto.titleAr());
        c.setDescriptionEn(dto.descriptionEn());
        c.setDescriptionAr(dto.descriptionAr());
        c.setControlType(dto.controlType());
        c.setLinkedControlId(dto.linkedControlId());
        c.setEffectiveness(dto.effectiveness() == null ? "MEDIUM" : dto.effectiveness());
        c.setLastTestedAt(dto.lastTestedAt());
        c.setLastTestResult(dto.lastTestResult());
        c.setResponsibleRole(dto.responsibleRole());
        c.setResponsibleOwner(dto.responsibleOwner());
    }

    private String generateCode(Long orgId) {
        // BOWTIE-{year}-{seq}. Pick a 3-digit number from a small random
        // search — collisions on a fresh tenant are vanishingly rare;
        // retry once if we hit one.
        int year = Year.now().getValue();
        for (int attempt = 0; attempt < 5; attempt++) {
            int seq = 1 + (int) (Math.random() * 899);
            String candidate = String.format(Locale.ROOT, "BOWTIE-%d-%03d", year, seq);
            if (bowtieRepo.countByOrganisationIdAndBowtieCode(orgId, candidate) == 0) {
                return candidate;
            }
        }
        // Fall back to UUID suffix if random retries exhausted.
        return "BOWTIE-" + year + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private static Map<String, Object> payloadFor(BowTieAnalysis b, String previousStatus) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bowtieCode",                safe(b.getBowtieCode()));
        m.put("externalId",                b.getExternalId() == null ? "" : b.getExternalId().toString());
        m.put("status",                    safe(b.getStatus()));
        m.put("riskCategory",              safe(b.getRiskCategory()));
        m.put("topEventTitleEn",           safe(b.getTopEventTitleEn()));
        m.put("overallResidualRating",     safe(b.getOverallResidualRating()));
        m.put("overallControlEffectiveness",
                b.getOverallControlEffectiveness() == null ? "" : b.getOverallControlEffectiveness().toPlainString());
        m.put("linkedRiskId",              b.getLinkedRiskId() == null ? "" : String.valueOf(b.getLinkedRiskId()));
        if (previousStatus != null) m.put("previousStatus", previousStatus);
        return m;
    }

    private static String safe(String v) { return v == null ? "" : v; }
}
