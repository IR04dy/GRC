package com.tss.grc.module.opres.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.opres.dto.OpResDtos.AssessmentResponse;
import com.tss.grc.module.opres.dto.OpResDtos.CellResponse;
import com.tss.grc.module.opres.dto.OpResDtos.CellUpsertRequest;
import com.tss.grc.module.opres.dto.OpResDtos.CreateAssessmentRequest;
import com.tss.grc.module.opres.dto.OpResDtos.PillarResponse;
import com.tss.grc.module.opres.dto.OpResDtos.UpdateAssessmentRequest;
import com.tss.grc.module.opres.entity.OpResAssessment;
import com.tss.grc.module.opres.entity.OpResAssessmentCell;
import com.tss.grc.module.opres.entity.OpResPillar;
import com.tss.grc.module.opres.repository.OpResAssessmentCellRepository;
import com.tss.grc.module.opres.repository.OpResAssessmentRepository;
import com.tss.grc.module.opres.repository.OpResPillarRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Operational Resilience self-assessment service — Sprint 14 PR #8.
 *
 * <p>State machine:
 * <pre>
 *   DRAFT → IN_PROGRESS → APPROVED → CLOSED
 *   DRAFT → CLOSED  (abandoned)
 * </pre>
 *
 * <p>F7 + F8 trigger codes:
 * <ul>
 *   <li>{@code OPRES_ASSESSMENT_CREATED} — new instance opened</li>
 *   <li>{@code OPRES_CELL_UPDATED} — current/target score set on a pillar</li>
 *   <li>{@code OPRES_ASSESSMENT_SUBMITTED} — moved to APPROVED waiting for sign-off</li>
 *   <li>{@code OPRES_ASSESSMENT_APPROVED} — board / CRO approved</li>
 *   <li>{@code OPRES_ASSESSMENT_CLOSED} — terminal</li>
 *   <li>{@code OPRES_PILLAR_GAP} — published whenever a cell upsert sets
 *       target &gt; current (gap exists) — enables auto-CAPA via F8</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class OpResService {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "DRAFT",        Set.of("IN_PROGRESS", "CLOSED"),
            "IN_PROGRESS",  Set.of("APPROVED", "CLOSED"),
            "APPROVED",     Set.of("CLOSED"),
            "CLOSED",       Set.of()
    );

    private final OpResPillarRepository pillarRepo;
    private final OpResAssessmentRepository assessmentRepo;
    private final OpResAssessmentCellRepository cellRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    // ─── Pillars (catalog, read-only) ────────────────────────────────

    @Transactional(readOnly = true)
    public List<PillarResponse> listPillars() {
        return pillarRepo.findActiveForOrg(org()).stream()
                .map(OpResService::toPillar)
                .toList();
    }

    // ─── Assessment CRUD + lifecycle ─────────────────────────────────

    @Transactional
    public AssessmentResponse createAssessment(CreateAssessmentRequest req) {
        Long orgId = org();
        OpResAssessment a = new OpResAssessment();
        a.setOrganisationId(orgId);
        a.setFiscalYear(req.getFiscalYear());
        a.setTitleEn(req.getTitleEn());
        a.setTitleAr(req.getTitleAr());
        a.setDueDate(req.getDueDate());
        a.setSponsorUserId(req.getSponsorUserId());
        a.setStatus("DRAFT");
        a.setCompleteness("0/5");
        OpResAssessment saved = assessmentRepo.save(a);

        audit.log("OPRES_ASSESSMENT_CREATED", "OpResAssessment", saved.getId(),
                "OpRes assessment opened: FY" + saved.getFiscalYear() + " — " + saved.getTitleEn());
        emit("OPRES_ASSESSMENT_CREATED", "OpResAssessment", saved.getId(),
                String.valueOf(saved.getFiscalYear()),
                Map.of("fiscalYear", String.valueOf(saved.getFiscalYear()),
                        "status", saved.getStatus()));
        return toAssessment(saved, List.of());
    }

    @Transactional
    public AssessmentResponse updateAssessment(Long id, UpdateAssessmentRequest req) {
        OpResAssessment a = loadAssessment(id);
        if (!"DRAFT".equals(a.getStatus()) && !"IN_PROGRESS".equals(a.getStatus())) {
            throw new IllegalStateException("Cannot edit assessment in status " + a.getStatus());
        }
        if (req.getTitleEn() != null) a.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null) a.setTitleAr(req.getTitleAr());
        if (req.getDueDate() != null) a.setDueDate(req.getDueDate());
        if (req.getSponsorUserId() != null) a.setSponsorUserId(req.getSponsorUserId());
        if (req.getExecutiveSummaryEn() != null) a.setExecutiveSummaryEn(req.getExecutiveSummaryEn());
        if (req.getExecutiveSummaryAr() != null) a.setExecutiveSummaryAr(req.getExecutiveSummaryAr());
        OpResAssessment saved = assessmentRepo.save(a);
        return toAssessment(saved, loadCells(saved.getId()));
    }

    @Transactional
    public CellResponse upsertCell(Long assessmentId, CellUpsertRequest req) {
        OpResAssessment a = loadAssessment(assessmentId);
        if ("CLOSED".equals(a.getStatus()) || "APPROVED".equals(a.getStatus())) {
            throw new IllegalStateException("Cannot edit cell while assessment is " + a.getStatus());
        }
        OpResPillar pillar = pillarRepo.findById(req.getPillarId())
                .filter(p -> p.getOrganisationId() == null || p.getOrganisationId().equals(a.getOrganisationId()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "OpRes pillar not visible to tenant: " + req.getPillarId()));

        OpResAssessmentCell c = cellRepo.findByAssessmentIdAndPillarIdAndOrganisationId(
                a.getId(), pillar.getId(), a.getOrganisationId())
                .orElseGet(() -> {
                    OpResAssessmentCell nc = new OpResAssessmentCell();
                    nc.setOrganisationId(a.getOrganisationId());
                    nc.setAssessmentId(a.getId());
                    nc.setPillarId(pillar.getId());
                    nc.setPillarCodeSnapshot(pillar.getCode());
                    return nc;
                });
        c.setCurrentScore(req.getCurrentScore());
        c.setTargetScore(req.getTargetScore());
        c.setRationaleEn(req.getRationaleEn());
        c.setRationaleAr(req.getRationaleAr());
        c.setEvidenceReference(req.getEvidenceReference());
        c.setLastEditedByUserId(currentUserId());
        OpResAssessmentCell saved = cellRepo.save(c);

        // Move assessment to IN_PROGRESS if first cell write
        if ("DRAFT".equals(a.getStatus())) {
            a.setStatus("IN_PROGRESS");
        }
        // Re-compute aggregate scores
        List<OpResAssessmentCell> all = loadCells(a.getId());
        long withCurrent = all.stream().filter(x -> x.getCurrentScore() != null).count();
        long totalPillars = pillarRepo.findActiveForOrg(a.getOrganisationId()).size();
        a.setCompleteness(withCurrent + "/" + totalPillars);
        a.setAvgCurrentScore(mean(all, OpResAssessmentCell::getCurrentScore));
        a.setAvgTargetScore(mean(all, OpResAssessmentCell::getTargetScore));
        assessmentRepo.save(a);

        audit.log("OPRES_CELL_UPDATED", "OpResAssessmentCell", saved.getId(),
                "Pillar " + pillar.getCode() + " · current=" + req.getCurrentScore()
                        + ", target=" + req.getTargetScore() + " (assessment " + a.getId() + ")");
        emit("OPRES_CELL_UPDATED", "OpResAssessmentCell", saved.getId(),
                String.valueOf(a.getId()),
                Map.of("assessmentId", String.valueOf(a.getId()),
                        "pillarCode", pillar.getCode(),
                        "currentScore", String.valueOf(req.getCurrentScore()),
                        "targetScore", String.valueOf(req.getTargetScore())));
        // If target > current, emit a gap event so the rules engine can
        // auto-create a CAPA / risk treatment.
        if (req.getCurrentScore() != null && req.getTargetScore() != null
                && req.getTargetScore() > req.getCurrentScore()) {
            int gap = req.getTargetScore() - req.getCurrentScore();
            audit.log("OPRES_PILLAR_GAP", "OpResAssessmentCell", saved.getId(),
                    "Pillar " + pillar.getCode() + " has maturity gap of "
                            + gap + " levels (current=" + req.getCurrentScore()
                            + ", target=" + req.getTargetScore() + ")");
            emit("OPRES_PILLAR_GAP", "OpResAssessmentCell", saved.getId(),
                    String.valueOf(a.getId()),
                    Map.of("assessmentId", String.valueOf(a.getId()),
                            "pillarCode", pillar.getCode(),
                            "gap", String.valueOf(gap),
                            "currentScore", String.valueOf(req.getCurrentScore()),
                            "targetScore", String.valueOf(req.getTargetScore())));
        }
        return toCell(saved);
    }

    @Transactional
    public AssessmentResponse submit(Long id) {
        OpResAssessment a = loadAssessment(id);
        guardTransition(a.getStatus(), "APPROVED");
        a.setStatus("APPROVED");
        a.setSubmittedAt(LocalDateTime.now());
        OpResAssessment saved = assessmentRepo.save(a);

        audit.log("OPRES_ASSESSMENT_SUBMITTED", "OpResAssessment", saved.getId(),
                "Submitted for sign-off — FY" + saved.getFiscalYear());
        emit("OPRES_ASSESSMENT_SUBMITTED", "OpResAssessment", saved.getId(),
                String.valueOf(saved.getFiscalYear()),
                Map.of("fiscalYear", String.valueOf(saved.getFiscalYear()),
                        "avgCurrentScore", String.valueOf(saved.getAvgCurrentScore()),
                        "avgTargetScore", String.valueOf(saved.getAvgTargetScore())));
        return toAssessment(saved, loadCells(saved.getId()));
    }

    @Transactional
    public AssessmentResponse approve(Long id) {
        OpResAssessment a = loadAssessment(id);
        if (!"APPROVED".equals(a.getStatus())) {
            throw new IllegalStateException("Approve only from APPROVED-pending status; current="
                    + a.getStatus());
        }
        a.setApprovedAt(LocalDateTime.now());
        a.setApprovedByUserId(currentUserId());
        OpResAssessment saved = assessmentRepo.save(a);

        audit.log("OPRES_ASSESSMENT_APPROVED", "OpResAssessment", saved.getId(),
                "Approved — FY" + saved.getFiscalYear());
        emit("OPRES_ASSESSMENT_APPROVED", "OpResAssessment", saved.getId(),
                String.valueOf(saved.getFiscalYear()),
                Map.of("fiscalYear", String.valueOf(saved.getFiscalYear())));
        return toAssessment(saved, loadCells(saved.getId()));
    }

    @Transactional
    public AssessmentResponse close(Long id) {
        OpResAssessment a = loadAssessment(id);
        guardTransition(a.getStatus(), "CLOSED");
        a.setStatus("CLOSED");
        a.setClosedAt(LocalDateTime.now());
        OpResAssessment saved = assessmentRepo.save(a);

        audit.log("OPRES_ASSESSMENT_CLOSED", "OpResAssessment", saved.getId(),
                "Closed — FY" + saved.getFiscalYear());
        emit("OPRES_ASSESSMENT_CLOSED", "OpResAssessment", saved.getId(),
                String.valueOf(saved.getFiscalYear()), Map.of());
        return toAssessment(saved, loadCells(saved.getId()));
    }

    // ─── Reads ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AssessmentResponse> listAssessments(int page, int size) {
        return assessmentRepo.findByOrganisationIdOrderByFiscalYearDescCreatedAtDesc(
                org(), PageRequest.of(page, size))
                .map(a -> toAssessment(a, loadCells(a.getId())));
    }

    @Transactional(readOnly = true)
    public AssessmentResponse getAssessment(Long id) {
        OpResAssessment a = loadAssessment(id);
        return toAssessment(a, loadCells(a.getId()));
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private OpResAssessment loadAssessment(Long id) {
        return assessmentRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException("OpRes assessment not found: " + id));
    }

    private List<OpResAssessmentCell> loadCells(Long assessmentId) {
        return cellRepo.findByAssessmentIdAndOrganisationIdOrderByPillarId(assessmentId, org());
    }

    private void guardTransition(String from, String to) {
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(Objects.requireNonNullElse(from, ""), Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Illegal OpRes assessment transition: " + from + " → " + to + " (allowed: " + allowed + ")");
        }
    }

    private static BigDecimal mean(List<OpResAssessmentCell> cells,
                                    java.util.function.Function<OpResAssessmentCell, Integer> extractor) {
        List<Integer> values = cells.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .toList();
        if (values.isEmpty()) return null;
        BigDecimal sum = values.stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private static Long org() { return TenantContext.requireOrganisationId(); }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof GrcUserDetails u)) {
            return null;
        }
        return u.getUserId();
    }

    private void emit(String trigger, String entityType, Long entityId,
                       String correlationId, Map<String, Object> payload) {
        publisher.publishEvent(new DomainEvent(
                TenantContext.getCurrentTenant(),
                trigger, entityType, entityId, correlationId,
                payload == null ? Map.of() : new HashMap<>(payload)));
    }

    // ─── DTO mappers ─────────────────────────────────────────────────

    private AssessmentResponse toAssessment(OpResAssessment a, List<OpResAssessmentCell> cells) {
        return AssessmentResponse.builder()
                .id(a.getId())
                .fiscalYear(a.getFiscalYear())
                .titleEn(a.getTitleEn())
                .titleAr(a.getTitleAr())
                .status(a.getStatus())
                .dueDate(a.getDueDate())
                .sponsorUserId(a.getSponsorUserId())
                .avgCurrentScore(a.getAvgCurrentScore())
                .avgTargetScore(a.getAvgTargetScore())
                .completeness(a.getCompleteness())
                .submittedAt(a.getSubmittedAt())
                .approvedAt(a.getApprovedAt())
                .approvedByUserId(a.getApprovedByUserId())
                .closedAt(a.getClosedAt())
                .executiveSummaryEn(a.getExecutiveSummaryEn())
                .executiveSummaryAr(a.getExecutiveSummaryAr())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .cells(cells.stream().map(OpResService::toCell).toList())
                .build();
    }

    private static CellResponse toCell(OpResAssessmentCell c) {
        return CellResponse.builder()
                .id(c.getId())
                .assessmentId(c.getAssessmentId())
                .pillarId(c.getPillarId())
                .pillarCodeSnapshot(c.getPillarCodeSnapshot())
                .currentScore(c.getCurrentScore())
                .targetScore(c.getTargetScore())
                .rationaleEn(c.getRationaleEn())
                .rationaleAr(c.getRationaleAr())
                .evidenceReference(c.getEvidenceReference())
                .lastEditedByUserId(c.getLastEditedByUserId())
                .build();
    }

    private static PillarResponse toPillar(OpResPillar p) {
        return PillarResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .sortOrder(p.getSortOrder())
                .nameEn(p.getNameEn())
                .nameAr(p.getNameAr())
                .descriptionEn(p.getDescriptionEn())
                .descriptionAr(p.getDescriptionAr())
                .samaReference(p.getSamaReference())
                .maturityRubricJson(p.getMaturityRubricJson())
                .isActive(p.getIsActive())
                .build();
    }
}
