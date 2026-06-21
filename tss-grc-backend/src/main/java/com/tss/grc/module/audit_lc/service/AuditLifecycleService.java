package com.tss.grc.module.audit_lc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.entity.AnnualPlan;
import com.tss.grc.module.audit_lc.entity.CapaTask;
import com.tss.grc.module.audit_lc.entity.WorkingPaper;
import com.tss.grc.module.audit_lc.repository.AnnualPlanRepository;
import com.tss.grc.module.audit_lc.repository.CapaTaskRepository;
import com.tss.grc.module.audit_lc.repository.WorkingPaperRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Audit-lifecycle support service — annual plans, working papers, CAPA
 * tasks. The Finding state machine lives in {@link FindingService}; this
 * one handles the surrounding artefacts that the frontend
 * {@code lib/auditLifecycle.ts} was hitting in mock-mode.
 *
 * <p>State machines enforced here:
 * <ul>
 *   <li>{@code AnnualPlan}: DRAFT → AC_REVIEW → AC_APPROVED → EXECUTING → CLOSED.</li>
 *   <li>{@code WorkingPaper}: DRAFT → PEER_REVIEW → APPROVED|REJECTED. REJECTED loops back to DRAFT.</li>
 *   <li>{@code CapaTask}: OPEN → IN_PROGRESS → AWAITING_VERIFICATION → CLOSED. Service flips to OVERDUE
 *       when target_date is past and status is non-terminal.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AuditLifecycleService {

    private static final Set<String> AP_STATUSES = Set.of(
        "DRAFT", "AC_REVIEW", "AC_APPROVED", "EXECUTING", "CLOSED");
    private static final Set<String> WP_STATUSES = Set.of(
        "DRAFT", "PEER_REVIEW", "APPROVED", "REJECTED");
    private static final Set<String> CAPA_STATUSES = Set.of(
        "OPEN", "IN_PROGRESS", "AWAITING_VERIFICATION", "CLOSED", "OVERDUE");

    private static final Map<String, Set<String>> AP_TRANSITIONS = Map.of(
        "DRAFT",       Set.of("AC_REVIEW"),
        "AC_REVIEW",   Set.of("AC_APPROVED", "DRAFT"),
        "AC_APPROVED", Set.of("EXECUTING"),
        "EXECUTING",   Set.of("CLOSED"));

    private static final Map<String, Set<String>> WP_TRANSITIONS = Map.of(
        "DRAFT",       Set.of("PEER_REVIEW"),
        "PEER_REVIEW", Set.of("APPROVED", "REJECTED"),
        "REJECTED",    Set.of("DRAFT"));  // re-work loop

    private static final Map<String, Set<String>> CAPA_TRANSITIONS = Map.of(
        "OPEN",                  Set.of("IN_PROGRESS", "AWAITING_VERIFICATION"),
        "IN_PROGRESS",           Set.of("AWAITING_VERIFICATION", "OPEN"),
        "AWAITING_VERIFICATION", Set.of("CLOSED", "IN_PROGRESS"),
        "OVERDUE",               Set.of("IN_PROGRESS", "AWAITING_VERIFICATION"));

    private final AnnualPlanRepository annualPlans;
    private final WorkingPaperRepository workingPapers;
    private final CapaTaskRepository capas;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ─── Annual plans ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AnnualPlan> listAnnualPlans() {
        return annualPlans.findByOrganisationIdOrderByYearDesc(org());
    }

    @Transactional(readOnly = true)
    public AnnualPlan getAnnualPlan(String externalId) {
        return annualPlans.findByOrganisationIdAndExternalId(org(), externalId)
            .orElseThrow(() -> new EntityNotFoundException("Annual plan " + externalId));
    }

    @Transactional
    public AnnualPlan createAnnualPlan(AnnualPlan body) {
        if (body.getExternalId() == null) throw new IllegalArgumentException("externalId is required");
        if (body.getYear() == null)       throw new IllegalArgumentException("year is required");
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getStatus() == null) body.setStatus("DRAFT");
        if (!AP_STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid annual-plan status: " + body.getStatus());
        body.setLastUpdated(LocalDateTime.now());
        return annualPlans.save(body);
    }

    @Transactional
    public AnnualPlan updateAnnualPlan(String externalId, AnnualPlan body) {
        AnnualPlan p = getAnnualPlan(externalId);
        if (body.getYear() != null)         p.setYear(body.getYear());
        if (body.getQ1Codes() != null)      p.setQ1Codes(body.getQ1Codes());
        if (body.getQ2Codes() != null)      p.setQ2Codes(body.getQ2Codes());
        if (body.getQ3Codes() != null)      p.setQ3Codes(body.getQ3Codes());
        if (body.getQ4Codes() != null)      p.setQ4Codes(body.getQ4Codes());
        if (body.getBudgetHours() != null)  p.setBudgetHours(body.getBudgetHours());
        if (body.getAcNotesEn() != null)    p.setAcNotesEn(body.getAcNotesEn());
        if (body.getAcNotesAr() != null)    p.setAcNotesAr(body.getAcNotesAr());
        p.setLastUpdated(LocalDateTime.now());
        return annualPlans.save(p);
    }

    @Transactional
    public AnnualPlan transitionAnnualPlan(String externalId, String to, String actor) {
        AnnualPlan p = getAnnualPlan(externalId);
        String from = p.getStatus();
        Set<String> allowed = AP_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) throw new IllegalTransitionException(from, to, allowed);
        p.setStatus(to);
        p.setLastUpdated(LocalDateTime.now());
        if ("AC_APPROVED".equals(to)) {
            p.setAcApprovedBy(actor);
            p.setAcApprovedAt(LocalDateTime.now());
        }
        return annualPlans.save(p);
    }

    @Transactional
    public void deleteAnnualPlan(String externalId) {
        AnnualPlan p = getAnnualPlan(externalId);
        p.setIsDeleted((short) 1);
        annualPlans.save(p);
    }

    // ─── Working papers ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WorkingPaper> listWorkingPapers(String engagementCode, String status) {
        Long o = org();
        if (engagementCode != null)
            return workingPapers.findByOrganisationIdAndEngagementCodeOrderByRefNumberAsc(o, engagementCode);
        if (status != null)
            return workingPapers.findByOrganisationIdAndStatusOrderByCreatedAtDesc(o, status);
        return workingPapers.findByOrganisationIdOrderByCreatedAtDesc(o);
    }

    @Transactional(readOnly = true)
    public WorkingPaper getWorkingPaper(String externalId) {
        return workingPapers.findByOrganisationIdAndExternalId(org(), externalId)
            .orElseThrow(() -> new EntityNotFoundException("Working paper " + externalId));
    }

    @Transactional
    public WorkingPaper createWorkingPaper(WorkingPaper body) {
        if (body.getExternalId() == null)     throw new IllegalArgumentException("externalId is required");
        if (body.getEngagementCode() == null) throw new IllegalArgumentException("engagementCode is required");
        if (body.getTitleEn() == null)        throw new IllegalArgumentException("titleEn is required");
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getStatus() == null) body.setStatus("DRAFT");
        if (!WP_STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid working-paper status: " + body.getStatus());
        if (body.getCreatedAt() == null) body.setCreatedAt(LocalDateTime.now());
        return workingPapers.save(body);
    }

    @Transactional
    public WorkingPaper updateWorkingPaper(String externalId, WorkingPaper body) {
        WorkingPaper wp = getWorkingPaper(externalId);
        if (body.getTitleEn() != null)        wp.setTitleEn(body.getTitleEn());
        if (body.getTitleAr() != null)        wp.setTitleAr(body.getTitleAr());
        if (body.getType() != null)           wp.setType(body.getType());
        if (body.getProcedureEn() != null)    wp.setProcedureEn(body.getProcedureEn());
        if (body.getProcedureAr() != null)    wp.setProcedureAr(body.getProcedureAr());
        if (body.getConclusionEn() != null)   wp.setConclusionEn(body.getConclusionEn());
        if (body.getConclusionAr() != null)   wp.setConclusionAr(body.getConclusionAr());
        if (body.getControlsCovered() != null) wp.setControlsCovered(body.getControlsCovered());
        if (body.getHours() != null)          wp.setHours(body.getHours());
        return workingPapers.save(wp);
    }

    @Transactional
    public WorkingPaper transitionWorkingPaper(String externalId, String to, String actor) {
        WorkingPaper wp = getWorkingPaper(externalId);
        String from = wp.getStatus();
        Set<String> allowed = WP_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) throw new IllegalTransitionException(from, to, allowed);
        wp.setStatus(to);
        if ("APPROVED".equals(to) || "REJECTED".equals(to)) {
            wp.setReviewer(actor);
            wp.setReviewedAt(LocalDateTime.now());
        }
        return workingPapers.save(wp);
    }

    @Transactional
    public void deleteWorkingPaper(String externalId) {
        WorkingPaper wp = getWorkingPaper(externalId);
        wp.setIsDeleted((short) 1);
        workingPapers.save(wp);
    }

    // ─── CAPA tasks ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CapaTask> listCapas(String findingId, String status) {
        Long o = org();
        if (findingId != null) return capas.findByOrganisationIdAndFindingIdOrderByTargetDateAsc(o, findingId);
        if (status != null)    return capas.findByOrganisationIdAndStatusOrderByTargetDateAsc(o, status);
        return capas.findByOrganisationIdOrderByTargetDateAsc(o);
    }

    @Transactional(readOnly = true)
    public CapaTask getCapa(String externalId) {
        return capas.findByOrganisationIdAndExternalId(org(), externalId)
            .orElseThrow(() -> new EntityNotFoundException("CAPA " + externalId));
    }

    @Transactional
    public CapaTask createCapa(CapaTask body) {
        if (body.getExternalId() == null) throw new IllegalArgumentException("externalId is required");
        if (body.getFindingId() == null)  throw new IllegalArgumentException("findingId is required");
        if (body.getTargetDate() == null) throw new IllegalArgumentException("targetDate is required");
        if (body.getKind() == null)       body.setKind("CORRECTIVE");
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getStatus() == null) body.setStatus("OPEN");
        if (!CAPA_STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid CAPA status: " + body.getStatus());
        applyOverdue(body);
        return capas.save(body);
    }

    @Transactional
    public CapaTask updateCapa(String externalId, CapaTask body) {
        CapaTask c = getCapa(externalId);
        if (body.getTitleEn() != null)              c.setTitleEn(body.getTitleEn());
        if (body.getTitleAr() != null)              c.setTitleAr(body.getTitleAr());
        if (body.getOwnerRole() != null)            c.setOwnerRole(body.getOwnerRole());
        if (body.getTargetDate() != null)           c.setTargetDate(body.getTargetDate());
        if (body.getEvidenceRef() != null)          c.setEvidenceRef(body.getEvidenceRef());
        if (body.getVerificationNotesEn() != null)  c.setVerificationNotesEn(body.getVerificationNotesEn());
        if (body.getVerificationNotesAr() != null)  c.setVerificationNotesAr(body.getVerificationNotesAr());
        applyOverdue(c);
        return capas.save(c);
    }

    @Transactional
    public CapaTask transitionCapa(String externalId, String to) {
        CapaTask c = getCapa(externalId);
        String from = c.getStatus();
        Set<String> allowed = CAPA_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) throw new IllegalTransitionException(from, to, allowed);
        c.setStatus(to);
        if ("CLOSED".equals(to)) c.setClosedAt(LocalDateTime.now());
        return capas.save(c);
    }

    @Transactional
    public void deleteCapa(String externalId) {
        CapaTask c = getCapa(externalId);
        c.setIsDeleted((short) 1);
        capas.save(c);
    }

    /** Mass-scan non-terminal CAPAs and flip past-target_date rows to OVERDUE. */
    @Transactional
    public List<String> runOverdueSweep() {
        LocalDate today = LocalDate.now();
        List<CapaTask> hits = capas.findOverdue(org(), today);
        List<String> flipped = new java.util.ArrayList<>();
        for (CapaTask c : hits) {
            c.setStatus("OVERDUE");
            capas.save(c);
            flipped.add(c.getExternalId());
        }
        return flipped;
    }

    private void applyOverdue(CapaTask c) {
        if (c.getStatus() == null) return;
        if ("CLOSED".equals(c.getStatus())) return;
        if (c.getTargetDate() != null && c.getTargetDate().isBefore(LocalDate.now())) {
            c.setStatus("OVERDUE");
        }
    }
}
