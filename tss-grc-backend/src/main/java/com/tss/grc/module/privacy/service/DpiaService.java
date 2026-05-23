package com.tss.grc.module.privacy.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.privacy.entity.Dpia;
import com.tss.grc.module.privacy.entity.DpiaMitigation;
import com.tss.grc.module.privacy.entity.DpiaQuestion;
import com.tss.grc.module.privacy.entity.DpiaReview;
import com.tss.grc.module.privacy.repository.DpiaMitigationRepository;
import com.tss.grc.module.privacy.repository.DpiaQuestionRepository;
import com.tss.grc.module.privacy.repository.DpiaRepository;
import com.tss.grc.module.privacy.repository.DpiaReviewRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DPIA workflow service — assessment §5.4 critical recommendation #3.
 * State machine:
 *   DRAFT → IN_REVIEW → APPROVED   (next_review_date drives EXPIRED)
 *   IN_REVIEW → REJECTED (with reason)
 *   APPROVED → EXPIRED (when next_review_date passes — handled by scheduler follow-up)
 */
@Service
@RequiredArgsConstructor
public class DpiaService {

    private final DpiaRepository           dpiaRepo;
    private final DpiaQuestionRepository   questionRepo;
    private final DpiaMitigationRepository mitigationRepo;
    private final DpiaReviewRepository     reviewRepo;
    private final AuditLogService          audit;
    private final ApplicationEventPublisher publisher;

    // ── F8 payload + emit ─────────────────────────────────────────────────────
    //
    // DPIA is the PDPL Article 25 risk-assessment workflow for any new
    // processing of personal data. F8 lets downstream privacy program
    // tasks (re-assessment scheduling, mitigation tracking, DPO
    // attention to HIGH residual risk) react automatically. Events:
    //
    //   DPIA_DRAFTED       — new assessment opened
    //   DPIA_SUBMITTED     — DRAFT → IN_REVIEW
    //   DPIA_APPROVED      — IN_REVIEW → APPROVED
    //   DPIA_REJECTED      — IN_REVIEW → REJECTED
    //   DPIA_EXPIRED       — APPROVED → EXPIRED (next-review past)

    private Map<String, Object> payloadFor(Dpia d, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("dpiaId",                 nullSafeStr(d.getId()));
        m.put("processingActivityId",   nullSafeStr(d.getProcessingActivityId()));
        m.put("status",                 nullSafeStr(d.getStatus()));
        m.put("previousStatus",         nullSafeStr(previousStatus));
        m.put("overallResidualRisk",    nullSafeStr(d.getOverallResidualRisk()));
        m.put("triggerReason",          nullSafeStr(d.getTriggerReason()));
        m.put("nextReviewDate",         nullSafeStr(d.getNextReviewDate()));
        m.put("dpoId",                  nullSafeStr(d.getDpoId()));
        m.put("assessorId",             nullSafeStr(d.getAssessorId()));
        return m;
    }

    private static String nullSafeStr(Object o) { return o == null ? "" : o.toString(); }
    private static String nullSafe(Object o)    { return o == null ? "<not provided>" : o.toString(); }

    private void emit(String trigger, Dpia d, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            TenantContext.getCurrentTenant(), trigger, "Dpia", d.getId(),
            "dpia:" + d.getId(), payloadFor(d, previousStatus)));
    }

    // ── DPIA CRUD ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Dpia> list(Pageable pageable) {
        return dpiaRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWithChildren(Long id) {
        Dpia d = mustGet(id);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("dpia",        d);
        out.put("questions",   questionRepo.findByDpiaIdOrderBySortOrder(id));
        out.put("mitigations", mitigationRepo.findByDpiaIdOrderBySortOrder(id));
        out.put("reviews",     reviewRepo.findByDpiaIdOrderByDecidedAtDesc(id));
        return out;
    }

    @Transactional
    public Dpia create(Dpia req) {
        // Mass-assignment defense — controller accepts the entity via
        // @RequestBody. Null id forces save() to INSERT instead of
        // merge() into a potentially cross-tenant row. DPIAs are PDPL
        // Article 25 records, so cross-tenant data corruption here
        // is also a regulatory disclosure risk.
        req.setId(null);
        req.setOrganisationId(TenantContext.getCurrentTenant());
        Dpia saved = dpiaRepo.save(req);
        audit.log("CREATE", "Dpia", saved.getId(),
            "DPIA drafted: " + nullSafe(saved.getTitleEn()) + " (PDPL Article 25)");
        emit("DPIA_DRAFTED", saved, null);
        return saved;
    }

    @Transactional
    public Dpia update(Long id, Dpia req) {
        Dpia d = mustGet(id);
        d.setProcessingActivityId(req.getProcessingActivityId());
        d.setTitleEn(req.getTitleEn());
        d.setTitleAr(req.getTitleAr());
        d.setTriggerReason(req.getTriggerReason());
        d.setOverallResidualRisk(req.getOverallResidualRisk());
        d.setDpoId(req.getDpoId());
        d.setAssessorId(req.getAssessorId());
        d.setAssessmentDate(req.getAssessmentDate());
        d.setNextReviewDate(req.getNextReviewDate());
        d.setConclusion(req.getConclusion());
        d.setUpdatedAt(LocalDateTime.now());
        return dpiaRepo.save(d);
    }

    @Transactional
    public void delete(Long id) {
        Dpia d = mustGet(id);
        d.setIsDeleted(true);
        dpiaRepo.save(d);
    }

    // ── Workflow transitions ────────────────────────────────────────────────

    @Transactional
    public Dpia submitForReview(Long id) {
        Dpia d = mustGet(id);
        require(d, "DRAFT");
        d.setStatus("IN_REVIEW");
        Dpia saved = dpiaRepo.save(d);
        audit.log("TRANSITION", "Dpia", saved.getId(), "DPIA submitted for review (DRAFT → IN_REVIEW)");
        emit("DPIA_SUBMITTED", saved, "DRAFT");
        return saved;
    }

    @Transactional
    public Dpia approve(Long id, Map<String, Object> body) {
        Dpia d = mustGet(id);
        require(d, "IN_REVIEW");
        d.setStatus("APPROVED");
        d.setApprovedAt(LocalDateTime.now());
        if (body.get("conclusion") instanceof String s) d.setConclusion(s);
        if (d.getNextReviewDate() == null) d.setNextReviewDate(java.time.LocalDate.now().plusYears(1));
        Dpia saved = dpiaRepo.save(d);
        audit.log("TRANSITION", "Dpia", saved.getId(),
            "DPIA approved (residual risk: " + nullSafe(saved.getOverallResidualRisk())
                + ", next review: " + saved.getNextReviewDate() + ")");
        emit("DPIA_APPROVED", saved, "IN_REVIEW");
        return saved;
    }

    @Transactional
    public Dpia reject(Long id, Map<String, Object> body) {
        Dpia d = mustGet(id);
        require(d, "IN_REVIEW", "DRAFT");
        String previousStatus = d.getStatus();
        d.setStatus("REJECTED");
        d.setRejectedAt(LocalDateTime.now());
        if (body.get("reason") instanceof String s) d.setRejectionReason(s);
        Dpia saved = dpiaRepo.save(d);
        audit.log("TRANSITION", "Dpia", saved.getId(),
            "DPIA rejected: " + nullSafe(saved.getRejectionReason()));
        emit("DPIA_REJECTED", saved, previousStatus);
        return saved;
    }

    @Transactional
    public Dpia expire(Long id) {
        Dpia d = mustGet(id);
        require(d, "APPROVED");
        d.setStatus("EXPIRED");
        Dpia saved = dpiaRepo.save(d);
        audit.log("TRANSITION", "Dpia", saved.getId(), "DPIA expired (next review date passed)");
        emit("DPIA_EXPIRED", saved, "APPROVED");
        return saved;
    }

    // ── Children: questions + mitigations + reviews ─────────────────────────

    @Transactional
    public DpiaQuestion addQuestion(Long dpiaId, DpiaQuestion req) {
        mustGet(dpiaId);
        req.setDpiaId(dpiaId);
        return questionRepo.save(req);
    }

    @Transactional
    public DpiaMitigation addMitigation(Long dpiaId, DpiaMitigation req) {
        mustGet(dpiaId);
        req.setDpiaId(dpiaId);
        return mitigationRepo.save(req);
    }

    @Transactional
    public DpiaReview addReview(Long dpiaId, DpiaReview req) {
        mustGet(dpiaId);
        req.setDpiaId(dpiaId);
        return reviewRepo.save(req);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Long org = TenantContext.getCurrentTenant();
        return Map.of(
            "draftCount",     dpiaRepo.countByOrganisationIdAndStatus(org, "DRAFT"),
            "inReviewCount",  dpiaRepo.countByOrganisationIdAndStatus(org, "IN_REVIEW"),
            "approvedCount",  dpiaRepo.countByOrganisationIdAndStatus(org, "APPROVED"),
            "rejectedCount",  dpiaRepo.countByOrganisationIdAndStatus(org, "REJECTED"),
            "expiredCount",   dpiaRepo.countByOrganisationIdAndStatus(org, "EXPIRED")
        );
    }

    private Dpia mustGet(Long id) {
        return dpiaRepo.findById(id)
            .filter(x -> x.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("DPIA not found: " + id));
    }

    private void require(Dpia d, String... allowed) {
        for (String s : allowed) if (s.equals(d.getStatus())) return;
        throw new IllegalArgumentException("DPIA must be in " + java.util.Arrays.toString(allowed) + " (was " + d.getStatus() + ")");
    }

    @SuppressWarnings("unused")
    private List<DpiaQuestion> _q;  // keep imports tidy
}
