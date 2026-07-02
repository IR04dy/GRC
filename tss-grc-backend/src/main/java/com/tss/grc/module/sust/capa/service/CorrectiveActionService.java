package com.tss.grc.module.sust.capa.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.repository.CorrectiveActionRepository;
import com.tss.grc.module.sust.settings.service.OrganisationSettingsService;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Sustainability Corrective-Action service.
 *
 * <p>Status machine:
 * <pre>
 *   OPEN → IN_PROGRESS → COMPLETED → VERIFIED → CLOSED
 *   any → CANCELLED   (terminal except via soft-delete)
 *   CLOSED, CANCELLED → (nothing)
 * </pre>
 *
 * <p>Rules:
 * <ul>
 *   <li><b>Rule A</b> — verifier ({@code verifiedBy}) must not equal owner
 *       ({@code ownerName}) when transitioning to {@code VERIFIED}.</li>
 *   <li><b>Rule B</b> — transitioning to {@code COMPLETED} requires
 *       {@code evidenceRef} to be set.</li>
 *   <li><b>Rule C</b> — {@code CLOSED} and {@code CANCELLED} are terminal.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CorrectiveActionService {

    private static final Set<String> STATUSES =
        Set.of("OPEN", "IN_PROGRESS", "COMPLETED", "VERIFIED", "CLOSED", "CANCELLED");

    private static final Set<String> PRIORITIES =
        Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    private static final Set<String> SOURCE_TYPES =
        Set.of("CLIMATE_RISK", "GHG_RECORD", "ANNEX_GAP", "CMA_DISCLOSURE",
               "TCFD_ITEM", "SGI_COMMITMENT", "MANUAL");

    private final CorrectiveActionRepository repo;
    private final OrganisationSettingsService settingsService;
    private final AuditLogService audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Reads ─────────────────────────────────────────────────────────
    public Page<CorrectiveAction> list(Pageable p) {
        return repo.findByOrganisationIdOrderByTargetDateAscIdDesc(org(), p);
    }
    public Page<CorrectiveAction> listByStatus(String status, Pageable p) {
        return repo.findByOrganisationIdAndStatusOrderByTargetDateAscIdDesc(org(), status, p);
    }
    public Page<CorrectiveAction> listBySource(String sourceType, Pageable p) {
        return repo.findByOrganisationIdAndSourceTypeOrderByTargetDateAscIdDesc(org(), sourceType, p);
    }
    public Page<CorrectiveAction> listByPriority(String priority, Pageable p) {
        return repo.findByOrganisationIdAndPriorityOrderByTargetDateAscIdDesc(org(), priority, p);
    }
    public List<CorrectiveAction> listForSource(String sourceType, String sourceRef) {
        return repo.findByOrganisationIdAndSourceTypeAndSourceRef(org(), sourceType, sourceRef);
    }
    public CorrectiveAction get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Corrective action " + id));
    }

    // ── Writes ────────────────────────────────────────────────────────
    @Transactional
    public CorrectiveAction create(CorrectiveAction req) {
        req.setId(null);
        req.setOrganisationId(org());
        // Mass-assignment guard (SEC-ESG-003): force the initial lifecycle
        // state (transitions are the only path forward) and scrub the
        // workflow timestamps + verification attribution + audit columns so
        // a CAPA cannot be born VERIFIED/CLOSED or carry a forged verifier.
        req.setStatus("OPEN");
        req.setStartedAt(null);
        req.setCompletedAt(null);
        req.setVerifiedBy(null);
        req.setVerifiedAt(null);
        req.setClosedAt(null);
        req.setCreatedBy(null);
        req.setUpdatedBy(null);
        if (req.getPriority() == null || req.getPriority().isBlank()) req.setPriority("MEDIUM");
        validate(req);
        CorrectiveAction saved = repo.save(req);
        audit.log("CREATE", "CorrectiveAction", saved.getId(), "Corrective action created");
        return saved;
    }

    @Transactional
    public CorrectiveAction update(Long id, CorrectiveAction req) {
        CorrectiveAction cur = get(id);
        // Rule C — CLOSED / CANCELLED are read-only.
        if ("CLOSED".equals(cur.getStatus()) || "CANCELLED".equals(cur.getStatus())) {
            throw new IllegalStateException(
                "Corrective action is " + cur.getStatus() + " and cannot be modified. Re-open via the workflow.");
        }
        if (req.getSourceType() != null)        cur.setSourceType(req.getSourceType());
        if (req.getSourceRef() != null)         cur.setSourceRef(req.getSourceRef());
        if (req.getTitleEn() != null)           cur.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null)           cur.setTitleAr(req.getTitleAr());
        if (req.getDescriptionEn() != null)     cur.setDescriptionEn(req.getDescriptionEn());
        if (req.getDescriptionAr() != null)     cur.setDescriptionAr(req.getDescriptionAr());
        if (req.getPriority() != null)          cur.setPriority(req.getPriority());
        if (req.getOwnerName() != null)         cur.setOwnerName(req.getOwnerName());
        if (req.getOwnerEmail() != null)        cur.setOwnerEmail(req.getOwnerEmail());
        if (req.getAssignedTo() != null)        cur.setAssignedTo(req.getAssignedTo());
        if (req.getTargetDate() != null)        cur.setTargetDate(req.getTargetDate());
        if (req.getRootCauseEn() != null)       cur.setRootCauseEn(req.getRootCauseEn());
        if (req.getRootCauseAr() != null)       cur.setRootCauseAr(req.getRootCauseAr());
        if (req.getRemediationPlanEn() != null) cur.setRemediationPlanEn(req.getRemediationPlanEn());
        if (req.getRemediationPlanAr() != null) cur.setRemediationPlanAr(req.getRemediationPlanAr());
        if (req.getEvidenceRef() != null)       cur.setEvidenceRef(req.getEvidenceRef());
        if (req.getCostEstimateSar() != null)   cur.setCostEstimateSar(req.getCostEstimateSar());
        if (req.getEffortHours() != null)       cur.setEffortHours(req.getEffortHours());
        if (req.getLinkedCapaId() != null)      cur.setLinkedCapaId(req.getLinkedCapaId());
        if (req.getLinkedRiskId() != null)      cur.setLinkedRiskId(req.getLinkedRiskId());
        if (req.getNotes() != null)             cur.setNotes(req.getNotes());
        validate(cur);
        CorrectiveAction saved = repo.save(cur);
        audit.log("UPDATE", "CorrectiveAction", saved.getId(), "Corrective action updated");
        return saved;
    }

    @Transactional
    public CorrectiveAction transition(Long id, String to) {
        return transition(id, to, (GrcUserDetails) null);
    }

    /**
     * Status transition. When transitioning to {@code VERIFIED} the acting
     * verifier is derived from the authenticated principal — never from a
     * client-supplied string — so attribution cannot be forged and the
     * separation-of-duties control (Rule A) compares a trusted identity
     * against the stored owner (SEC-ESG-002).
     *
     * @param actor the authenticated caller; required for VERIFIED.
     */
    @Transactional
    public CorrectiveAction transition(Long id, String to, GrcUserDetails actor) {
        CorrectiveAction cur = get(id);
        if (!STATUSES.contains(to))
            throw new IllegalArgumentException("Unknown status: " + to);
        String from = cur.getStatus();
        Set<String> allowedNext = allowedNext(from);
        if (!allowedNext.contains(to))
            throw new IllegalTransitionException(from, to, allowedNext);

        LocalDateTime now = LocalDateTime.now();
        switch (to) {
            case "IN_PROGRESS" -> {
                if (cur.getStartedAt() == null) cur.setStartedAt(now);
            }
            case "COMPLETED" -> {
                // Rule B — evidence required.
                if (cur.getEvidenceRef() == null || cur.getEvidenceRef().isBlank())
                    throw new IllegalArgumentException(
                        "Completing a corrective action requires evidence_ref (Rule B). Attach evidence before completing.");
                cur.setCompletedAt(now);
            }
            case "VERIFIED" -> {
                if (actor == null || actor.getUsername() == null || actor.getUsername().isBlank())
                    throw new IllegalArgumentException("Verification requires an authenticated verifier.");
                // Verifier identity is the authenticated principal — not a
                // request-body string — so the attribution is non-forgeable.
                String verifier = actor.getUsername();
                // Rule A — separation of duties: the verifier must not be the
                // owner. Compared against both the owner display name and the
                // owner email so a free-text owner field cannot be side-stepped.
                if (sameActor(verifier, cur.getOwnerName()) || sameActor(verifier, cur.getOwnerEmail()))
                    throw new IllegalArgumentException(
                        "Verifier must differ from owner (Rule A — separation of duties).");
                cur.setVerifiedBy(verifier);
                cur.setVerifiedAt(now);
            }
            case "CLOSED" -> cur.setClosedAt(now);
            case "CANCELLED" -> cur.setClosedAt(now);
            default -> { /* OPEN — no timestamp */ }
        }
        cur.setStatus(to);
        CorrectiveAction saved = repo.save(cur);
        audit.log("TRANSITION", "CorrectiveAction", saved.getId(), "CAPA -> " + to);
        return saved;
    }

    /** Trim-aware, case-insensitive identity compare used by Rule A. */
    private static boolean sameActor(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * Allowed forward transitions from each state. CANCELLED is universally
     * available except from terminal states.
     */
    private static Set<String> allowedNext(String from) {
        return switch (from) {
            case "OPEN"        -> Set.of("IN_PROGRESS", "CANCELLED");
            case "IN_PROGRESS" -> Set.of("COMPLETED", "CANCELLED");
            case "COMPLETED"   -> Set.of("VERIFIED", "CANCELLED");
            case "VERIFIED"    -> Set.of("CLOSED");
            // Rule C — terminal states.
            default -> Set.of();
        };
    }

    @Transactional
    public void softDelete(Long id) {
        CorrectiveAction cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
        audit.log("DELETE", "CorrectiveAction", id, "Corrective action deleted");
    }

    // ── Aggregations ─────────────────────────────────────────────────

    /**
     * Cross-source rollup: counts by status + priority + source-type, plus
     * overdue + due-soon counts. Powers the corrective-actions tab header
     * KPI strip.
     */
    public Map<String, Object> aggregate() {
        // S-B — the near-term warning window and the (wider) executive-
        // escalation window are tenant-configurable via OrganisationSettings
        // (defaults 7 / 30 days live in the settings schema).
        var st = settingsService.getOrCreate();
        List<CorrectiveAction> all = repo.findByOrganisationIdAndStatusIn(
            org(), List.of("OPEN", "IN_PROGRESS", "COMPLETED", "VERIFIED"));
        LocalDate today  = LocalDate.now();
        LocalDate warnBy = today.plusDays(st.getCapaOverdueWarningDays());       // imminent (warning)
        LocalDate soon   = today.plusDays(st.getCapaExecutiveEscalationDays());  // executive-escalation window

        Map<String, Integer> byStatus   = new LinkedHashMap<>();
        Map<String, Integer> byPriority = new LinkedHashMap<>();
        Map<String, Integer> bySource   = new LinkedHashMap<>();
        for (String s : List.of("OPEN", "IN_PROGRESS", "COMPLETED", "VERIFIED")) byStatus.put(s, 0);
        for (String p : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW"))            byPriority.put(p, 0);

        int overdue = 0, dueSoon = 0, dueSoonWarning = 0;
        for (CorrectiveAction a : all) {
            byStatus.merge(a.getStatus(), 1, Integer::sum);
            byPriority.merge(a.getPriority(), 1, Integer::sum);
            bySource.merge(a.getSourceType(), 1, Integer::sum);
            LocalDate due = a.getTargetDate();
            if (due == null) continue;
            // VERIFIED items are no longer "overdue" — they're awaiting closure.
            if ("VERIFIED".equals(a.getStatus())) continue;
            if (due.isBefore(today)) { overdue++; continue; }
            if (!due.isAfter(soon))   dueSoon++;          // within executive-escalation window
            if (!due.isAfter(warnBy)) dueSoonWarning++;   // subset — within the warning window
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("open",       byStatus.getOrDefault("OPEN", 0));
        out.put("inProgress", byStatus.getOrDefault("IN_PROGRESS", 0));
        out.put("completed",  byStatus.getOrDefault("COMPLETED", 0));
        out.put("verified",   byStatus.getOrDefault("VERIFIED", 0));
        out.put("overdue",        overdue);
        out.put("dueSoon",        dueSoon);
        out.put("dueSoonWarning", dueSoonWarning);
        out.put("byPriority", byPriority);
        out.put("bySource",   bySource);
        out.put("activeTotal", all.size());
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────
    private void validate(CorrectiveAction r) {
        if (r.getTitleEn() == null || r.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn required");
        if (r.getSourceType() == null || !SOURCE_TYPES.contains(r.getSourceType()))
            throw new IllegalArgumentException("sourceType must be one of " + SOURCE_TYPES);
        if (!STATUSES.contains(r.getStatus()))
            throw new IllegalArgumentException("status must be one of " + STATUSES);
        if (!PRIORITIES.contains(r.getPriority()))
            throw new IllegalArgumentException("priority must be one of " + PRIORITIES);
        if (r.getCostEstimateSar() != null && r.getCostEstimateSar().signum() < 0)
            throw new IllegalArgumentException("costEstimateSar must be >= 0");
        if (r.getEffortHours() != null && r.getEffortHours() < 0)
            throw new IllegalArgumentException("effortHours must be >= 0");
    }
}
