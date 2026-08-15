package com.tss.grc.module.audit_lc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ServerOwnedState;
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
 *
 * <h2>⚠⚠ F2-217 — a create that names the state does not need the machine</h2>
 *
 * <p>All three {@code create*} methods below persist the request entity, and each validated the
 * supplied {@code status} only for <em>membership of the vocabulary</em> — never for being the
 * state a record is allowed to <em>start</em> in. Probed live 2026-08-15 as {@code compliance}:
 * {@code POST /api/audit-lc/annual-plans} carrying
 * {@code {"status":"AC_APPROVED","acApprovedBy":"Nobody Who Approved This",
 * "acApprovedAt":"2001-01-01T00:00:00"}} answered <b>200</b> and stored all three, while the
 * guarded {@code /transition} route answered <b>409</b> for {@code DRAFT → AC_APPROVED} on an
 * identical plan. The same shape produced an IIA Standard 2330 working paper created
 * {@code APPROVED} with a named {@code reviewer} and a backdated {@code reviewedAt}, and a CAPA
 * created {@code CLOSED} with a backdated {@code closedAt} — a corrective action closed without
 * ever being verified.
 *
 * <p>🔑 <b>This is DF-3/DF-4's defect exactly — forged authority — and it was invisible to the
 * ratchet built to find it.</b> {@code MassAssignmentRatchetTest} ranks a body type by matching
 * field names against a literal list, and that list holds {@code approvedBy}, {@code reviewedBy}
 * and {@code closedBy}. This module's columns are {@code acApprovedBy}, {@code reviewer},
 * {@code reviewedAt} and {@code closedAt} — the same concepts, spelled differently — so all six
 * endpoints ranked TENANT rather than CRIT and were never adjudicated.
 *
 * <p>⚠ <b>And the fix that preceded it names the field it left open.</b> F2-203 closed
 * {@code author} on {@code createWorkingPaper} because an invented auditor was being persisted,
 * and its comment reads <em>"the same rule {@code transitionWorkingPaper} below already applies
 * to {@code reviewer}"</em>. True of the transition; false of the create it is written on. 🔑
 * <b>Fixing the field you noticed leaves the remainder looking covered, because the mechanism
 * that covers them is visibly present</b> — the live probe returned {@code author=compliance},
 * correctly stamped, beside a {@code reviewer} the caller had chosen.
 *
 * <p>Two rules now, applied by {@link #initialStatusOnly} and the {@code clear*} calls in each
 * create: a record is created in its register's <b>initial state</b>, and the columns a
 * transition stamps are <b>never</b> a caller's to set. The initial state is declared once per
 * register and read by both the default and the guard, so the two cannot disagree.
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

    /**
     * F2-217 — the one state each register may be created in.
     *
     * <p>Declared rather than derived: "initial" is not "has no incoming edge", because all three
     * machines loop back to their first state ({@code AC_REVIEW → DRAFT},
     * {@code REJECTED → DRAFT}, {@code IN_PROGRESS → OPEN}). What makes these safe is that each
     * constant has exactly <b>two</b> readers inside its own create — the default applied when the
     * caller says nothing, and {@link #initialStatusOnly}'s guard — so the value the server picks
     * and the value it accepts cannot drift apart. Before this they were separate literals and only
     * the default existed.
     */
    private static final String AP_INITIAL   = "DRAFT";
    private static final String WP_INITIAL   = "DRAFT";
    private static final String CAPA_INITIAL = "OPEN";

    /**
     * Returns the initial state, or refuses a caller trying to start somewhere else.
     *
     * <p>⚠ This cannot break a working request. The create already refused anything outside the
     * register's vocabulary, so every value that succeeds today is a declared state — and the only
     * ones this newly refuses are the non-initial ones, i.e. requests performing a transition that
     * the {@code /transition} route would have adjudicated and recorded. <b>A request that performs
     * an unguarded transition is not a working request.</b> (F2-182's rule for choosing between
     * refusing and coercing: ask what is already refusing.)
     *
     * <p>Refusing rather than silently coercing to the initial state is deliberate. A caller that
     * believes it is filing an approved plan must be told it is not, and be sent to the route that
     * can record who approved it; a quiet downgrade would leave the caller's own copy disagreeing
     * with the register (F2-197's rule that a silent all-clear is worse than a visible gap).
     */
    private static String initialStatusOnly(String supplied, String initial, String register) {
        // `register` carries its own article ("an annual plan") — "a " + register produced
        // "a annual plan" in the 400 body, which a caller reads before anything else here.
        //
        // F2-218 moved the body to common/validation/ServerOwnedState the first time a second
        // register (NDI evidence, whose create bypassed a maker/checker SoD check) needed the same
        // rule. This stays as the module's own name for it — the delegation is what stops a third
        // register hand-rolling a third copy that then drifts.
        return ServerOwnedState.requireInitial(
            supplied, initial, register, "POST /{externalId}/transition");
    }

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
        body.setStatus(initialStatusOnly(body.getStatus(), AP_INITIAL, "an annual plan"));
        if (!AP_STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid annual-plan status: " + body.getStatus());
        // F2-217 — transitionAnnualPlan stamps these when it reaches AC_APPROVED. A plan being
        // created has been approved by nobody, so there is nothing here to preserve; accepting
        // them let a caller assert an Audit Committee approval that never happened.
        body.setAcApprovedBy(null);
        body.setAcApprovedAt(null);
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
        body.setStatus(initialStatusOnly(body.getStatus(), WP_INITIAL, "a working paper"));
        if (!WP_STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid working-paper status: " + body.getStatus());
        // F2-217 — transitionWorkingPaper stamps these on APPROVED/REJECTED. The controller has
        // already stamped `author` from the authenticated principal (F2-203); these two are the
        // half that fix left open, and they carry the IIA Standard 2330 review attribution.
        body.setReviewer(null);
        body.setReviewedAt(null);
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
        body.setStatus(initialStatusOnly(body.getStatus(), CAPA_INITIAL, "a CAPA task"));
        if (!CAPA_STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid CAPA status: " + body.getStatus());
        // F2-217 — transitionCapa stamps closedAt when it reaches CLOSED. A CAPA created CLOSED
        // with a backdated closure is a corrective action that was never verified.
        body.setClosedAt(null);
        // applyOverdue may still move OPEN -> OVERDUE from target_date; that is server-derived,
        // which is the distinction this whole fix draws.
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
