package com.tss.grc.module.bcm.cycle.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmBiaAssessment;
import com.tss.grc.module.bcm.cycle.repository.BcmBiaAssessmentRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

/**
 * BIA Assessment service — independent audit Tier-1 fix.
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   DRAFT → UNDER_REVIEW ─┬─> APPROVED → ACTIVE → ARCHIVED
 *                         └─> CHANGES_REQUIRED → DRAFT (loop)
 * </pre>
 *
 * <h3>Rules locked here (spec §E)</h3>
 * <ul>
 *   <li>{@code service_id} OR {@code process_id} required — a BIA must have a scope.</li>
 *   <li>{@code mtpd_hours} > 0 when APPROVING.</li>
 *   <li>{@code rto_hours ≤ mtpd_hours} — blocks APPROVED transition if violated.</li>
 *   <li>APPROVED BIAs are locked — edits must reopen via CHANGES_REQUIRED.</li>
 *   <li>{@link #calculateCriticality} derives {@code overallCriticality} from the
 *       impact-dimensions JSON: max across dimensions maps to LOW (1-2), MEDIUM (3),
 *       HIGH (4), CRITICAL (5). If {@code national_sovereign ≥ 4}, criticality
 *       is forced to {@code SOVEREIGN} regardless of other dimensions.</li>
 * </ul>
 *
 * <h3>Generation</h3>
 * <p>{@link #generateBcpStub} and {@link #generateDrpStub} stamp the BIA with
 * {@code generatedBcpId / generatedDrpId} placeholders and audit the request.
 * Actual BCP/DRP entity creation is a small adapter that the consuming module
 * wires in — the BIA service stays orthogonal to BCP/DRP packaging.
 *
 * <h3>F8 events emitted</h3>
 * <ul>
 *   <li>{@code BIA_APPROVED} — when transitioning to APPROVED</li>
 *   <li>{@code BIA_CRITICALITY_SOVEREIGN} — when criticality becomes SOVEREIGN
 *       (so an automation rule can require executive sponsor or board sign-off)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BcmBiaAssessmentService {

    private static final Set<String> STATUSES = Set.of(
        "DRAFT", "UNDER_REVIEW", "CHANGES_REQUIRED", "APPROVED", "ACTIVE", "ARCHIVED");
    private static final Set<String> CRITICALITIES = Set.of(
        "LOW", "MEDIUM", "HIGH", "CRITICAL", "SOVEREIGN");

    /** Canonical time-band labels (spec §E impact-time-bands). */
    public static final List<String> TIME_BANDS = List.of(
        "0-2h", "2-4h", "4-8h", "8-24h", "1-3d", "3-7d", ">7d");

    /** Canonical impact dimensions (spec §E impact dimensions). */
    public static final List<String> IMPACT_DIMENSIONS = List.of(
        "regulatory", "financial", "operational", "reputation",
        "legal_contractual", "public_service", "national_sovereign",
        "data", "cyber", "supplier");

    private static final ObjectMapper M = new ObjectMapper();

    private final BcmBiaAssessmentRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    // ─── CRUD ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<BcmBiaAssessment> list(int page, int size) {
        return repo.findByOrganisationIdOrderByIdDesc(
            TenantContext.getCurrentTenant(), PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public BcmBiaAssessment get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("BIA not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<BcmBiaAssessment> byCycle(Long cycleId) {
        return repo.findByOrganisationIdAndCycleIdOrderByIdDesc(TenantContext.getCurrentTenant(), cycleId);
    }

    @Transactional
    public BcmBiaAssessment create(BcmBiaAssessment body) {
        validateScope(body);
        validateRtoMtpd(body);
        body.setId(null);
        Long org = TenantContext.getCurrentTenant();
        body.setOrganisationId(org);
        // F2-22b(ii) — a BIA is created at DRAFT and reaches APPROVED only through transition().
        //
        // Accepting the body's approvalStatus skipped that method's approval gate outright:
        // "APPROVED" requires mtpdHours to be set, RTO ≤ MTPD, and criticality recomputed. A create
        // supplying approvalStatus=APPROVED produced an approved BIA with no MTPD and an unchecked
        // recovery objective — the number the whole BCM programme is built on. Rejected, not
        // silently downgraded (F2-44).
        if (body.getApprovalStatus() != null && !"DRAFT".equals(body.getApprovalStatus()))
            throw new IllegalArgumentException(
                "A new BIA is created at DRAFT; approvalStatus=" + body.getApprovalStatus()
                + " was rejected because approval requires mtpdHours and RTO ≤ MTPD, which this "
                + "endpoint does not check. Create it, then POST /bia-assessments/{id}/transition.");
        body.setApprovalStatus("DRAFT");
        // F2-238 — three field initialisers moved here. They made update()'s null-guards
        // decorative, so any PATCH re-declared the BIA as having no manual workaround, not being
        // an OPRES critical operation, and NORMAL national-criticality tier. All three are NOT
        // NULL, so the create path has to own the default.
        if (body.getManualWorkaroundAvailable() == null)  body.setManualWorkaroundAvailable(false);
        if (body.getOpresCriticalOperationFlag() == null) body.setOpresCriticalOperationFlag(false);
        if (body.getNationalCriticalTier() == null)       body.setNationalCriticalTier("NORMAL");
        if (body.getBiaCode() == null || body.getBiaCode().isBlank()) {
            body.setBiaCode(nextCode());
        }
        // Compute criticality up-front if matrix is already populated
        recomputeCriticality(body);

        BcmBiaAssessment saved = repo.save(body);
        audit.log("CREATE", "BcmBiaAssessment", saved.getId(),
            "BIA created: " + saved.getBiaCode() + " scope=service:" + saved.getServiceId()
                + "/process:" + saved.getProcessId());
        return saved;
    }

    @Transactional
    public BcmBiaAssessment update(Long id, BcmBiaAssessment patch) {
        BcmBiaAssessment existing = get(id);
        if ("APPROVED".equals(existing.getApprovalStatus()) || "ACTIVE".equals(existing.getApprovalStatus()) ||
            "ARCHIVED".equals(existing.getApprovalStatus()))
            throw new IllegalArgumentException(
                "Cannot edit a " + existing.getApprovalStatus() + " BIA — reopen via transition first");
        if (patch.getApprovalStatus() != null && !patch.getApprovalStatus().equals(existing.getApprovalStatus()))
            throw new IllegalArgumentException("Use transition() to change status");

        // Patch what was supplied
        if (patch.getCycleId()                != null) existing.setCycleId(patch.getCycleId());
        if (patch.getServiceId()              != null) existing.setServiceId(patch.getServiceId());
        if (patch.getProcessId()              != null) existing.setProcessId(patch.getProcessId());
        if (patch.getDepartment()             != null) existing.setDepartment(patch.getDepartment());
        if (patch.getBiaOwnerId()             != null) existing.setBiaOwnerId(patch.getBiaOwnerId());
        if (patch.getServiceOwnerId()         != null) existing.setServiceOwnerId(patch.getServiceOwnerId());
        if (patch.getProcessOwnerId()         != null) existing.setProcessOwnerId(patch.getProcessOwnerId());
        if (patch.getDescription()            != null) existing.setDescription(patch.getDescription());
        if (patch.getMtpdHours()              != null) existing.setMtpdHours(patch.getMtpdHours());
        if (patch.getRtoHours()               != null) existing.setRtoHours(patch.getRtoHours());
        if (patch.getRpoHours()               != null) existing.setRpoHours(patch.getRpoHours());
        if (patch.getMbcoDescription()        != null) existing.setMbcoDescription(patch.getMbcoDescription());
        if (patch.getMinStaff()               != null) existing.setMinStaff(patch.getMinStaff());
        if (patch.getMinTechText()            != null) existing.setMinTechText(patch.getMinTechText());
        if (patch.getMinFacilityText()        != null) existing.setMinFacilityText(patch.getMinFacilityText());
        if (patch.getMinSupplierText()        != null) existing.setMinSupplierText(patch.getMinSupplierText());
        if (patch.getManualWorkaroundAvailable()    != null) existing.setManualWorkaroundAvailable(patch.getManualWorkaroundAvailable());
        if (patch.getManualWorkaroundDescription()  != null) existing.setManualWorkaroundDescription(patch.getManualWorkaroundDescription());
        if (patch.getPeakPeriodsText()        != null) existing.setPeakPeriodsText(patch.getPeakPeriodsText());
        if (patch.getImpactTimeBandsJson()    != null) existing.setImpactTimeBandsJson(patch.getImpactTimeBandsJson());
        if (patch.getEvidenceId()             != null) existing.setEvidenceId(patch.getEvidenceId());
        if (patch.getReviewerId()             != null) existing.setReviewerId(patch.getReviewerId());

        validateRtoMtpd(existing);
        recomputeCriticality(existing);
        existing.setUpdatedAt(LocalDateTime.now());
        BcmBiaAssessment saved = repo.save(existing);
        audit.log("UPDATE", "BcmBiaAssessment", saved.getId(),
            "BIA updated: " + saved.getBiaCode() + " criticality=" + saved.getOverallCriticality());
        return saved;
    }

    @Transactional
    public BcmBiaAssessment transition(Long id, String toStatus) {
        BcmBiaAssessment e = get(id);
        if (toStatus == null || !STATUSES.contains(toStatus))
            throw new IllegalArgumentException("status must be one of " + STATUSES);

        String from = e.getApprovalStatus();
        boolean legal = switch (from) {
            case "DRAFT"            -> Set.of("UNDER_REVIEW", "ARCHIVED").contains(toStatus);
            case "UNDER_REVIEW"     -> Set.of("APPROVED", "CHANGES_REQUIRED", "DRAFT").contains(toStatus);
            case "CHANGES_REQUIRED" -> Set.of("DRAFT", "UNDER_REVIEW").contains(toStatus);
            case "APPROVED"         -> Set.of("ACTIVE", "CHANGES_REQUIRED").contains(toStatus);
            case "ACTIVE"           -> Set.of("ARCHIVED", "CHANGES_REQUIRED").contains(toStatus);
            case "ARCHIVED"         -> false;
            default                 -> false;
        };
        if (!legal)
            throw new IllegalArgumentException("Illegal BIA transition: " + from + " → " + toStatus);

        // Approval gate: RTO ≤ MTPD; MTPD must be set.
        if ("APPROVED".equals(toStatus)) {
            if (e.getMtpdHours() == null || e.getMtpdHours() <= 0)
                throw new IllegalArgumentException("mtpdHours must be set before approval");
            validateRtoMtpd(e);
            recomputeCriticality(e);  // ensure criticality is fresh on approval
        }

        e.setApprovalStatus(toStatus);
        LocalDateTime now = LocalDateTime.now();
        if ("APPROVED".equals(toStatus) && e.getApprovedAt() == null) {
            e.setApprovedAt(now);
        }
        e.setUpdatedAt(now);
        BcmBiaAssessment saved = repo.save(e);
        audit.log("TRANSITION", "BcmBiaAssessment", saved.getId(),
            "BIA " + saved.getBiaCode() + " " + from + " → " + toStatus);

        if ("APPROVED".equals(toStatus)) {
            eventPublisher.publishEvent(DomainEvent.of(
                saved.getOrganisationId(), "BIA_APPROVED", "BcmBiaAssessment", saved.getId(),
                Map.of("bia_code", saved.getBiaCode(),
                       "criticality", String.valueOf(saved.getOverallCriticality()))));
            if ("SOVEREIGN".equals(saved.getOverallCriticality())) {
                eventPublisher.publishEvent(DomainEvent.of(
                    saved.getOrganisationId(), "BIA_CRITICALITY_SOVEREIGN", "BcmBiaAssessment", saved.getId(),
                    Map.of("bia_code", saved.getBiaCode())));
            }
        }
        return saved;
    }

    // ─── Criticality calculator ──────────────────────────────────

    /**
     * Recompute criticality from the impact-time-bands matrix. Walks every
     * (time-band, dimension) cell, takes the MAX per dimension, and stores
     * that map as {@code impactDimensionsJson}. Then maps the overall MAX:
     * <pre>
     *   max=1..2 → LOW
     *   max=3    → MEDIUM
     *   max=4    → HIGH
     *   max=5    → CRITICAL
     *   national_sovereign ≥ 4 → SOVEREIGN  (overrides)
     * </pre>
     */
    @Transactional
    public BcmBiaAssessment calculateCriticality(Long id) {
        BcmBiaAssessment e = get(id);
        recomputeCriticality(e);
        BcmBiaAssessment saved = repo.save(e);
        audit.log("CUSTOM", "BcmBiaAssessment", saved.getId(),
            "BIA criticality recalculated: " + saved.getBiaCode() + " → " + saved.getOverallCriticality());
        return saved;
    }

    private void recomputeCriticality(BcmBiaAssessment e) {
        if (e.getImpactTimeBandsJson() == null || e.getImpactTimeBandsJson().isBlank()) {
            // No matrix yet — leave criticality null
            e.setImpactDimensionsJson(null);
            e.setOverallCriticality(null);
            return;
        }
        try {
            JsonNode matrix = M.readTree(e.getImpactTimeBandsJson());
            Map<String, Integer> dimensionMax = new LinkedHashMap<>();
            for (String dim : IMPACT_DIMENSIONS) dimensionMax.put(dim, 0);

            Iterator<Map.Entry<String, JsonNode>> bandIter = matrix.fields();
            while (bandIter.hasNext()) {
                JsonNode bandRow = bandIter.next().getValue();
                if (bandRow == null || !bandRow.isObject()) continue;
                for (String dim : IMPACT_DIMENSIONS) {
                    int v = bandRow.path(dim).asInt(0);
                    if (v > dimensionMax.get(dim)) dimensionMax.put(dim, v);
                }
            }
            ObjectNode dimensionsNode = M.createObjectNode();
            int overall = 0;
            for (Map.Entry<String, Integer> en : dimensionMax.entrySet()) {
                dimensionsNode.put(en.getKey(), en.getValue());
                if (en.getValue() > overall) overall = en.getValue();
            }
            e.setImpactDimensionsJson(dimensionsNode.toString());

            String label = switch (overall) {
                case 1, 2 -> "LOW";
                case 3    -> "MEDIUM";
                case 4    -> "HIGH";
                case 5    -> "CRITICAL";
                default   -> null;
            };
            // National-sovereign override
            if (dimensionMax.getOrDefault("national_sovereign", 0) >= 4) {
                label = "SOVEREIGN";
            }
            e.setOverallCriticality(label);
        } catch (Exception ex) {
            throw new IllegalArgumentException("impactTimeBandsJson is malformed: " + ex.getMessage());
        }
    }

    // ─── Plan generation ─────────────────────────────────────────

    /**
     * Stamp the BIA with the supplied generated-BCP id. Returns the updated BIA.
     * This is the audit-visible half of "Generate BCP from BIA" — the actual
     * BcmPlan row creation is done by a thin adapter in the calling controller
     * to keep this service from depending on the legacy BCM package.
     */
    @Transactional
    public BcmBiaAssessment markBcpGenerated(Long biaId, Long bcpId) {
        BcmBiaAssessment e = get(biaId);
        e.setGeneratedBcpId(bcpId);
        e.setUpdatedAt(LocalDateTime.now());
        BcmBiaAssessment saved = repo.save(e);
        audit.log("CUSTOM", "BcmBiaAssessment", saved.getId(),
            "BCP generated from BIA " + saved.getBiaCode() + " → bcpId=" + bcpId);
        return saved;
    }

    @Transactional
    public BcmBiaAssessment markDrpGenerated(Long biaId, Long drpId) {
        BcmBiaAssessment e = get(biaId);
        e.setGeneratedDrpId(drpId);
        e.setUpdatedAt(LocalDateTime.now());
        BcmBiaAssessment saved = repo.save(e);
        audit.log("CUSTOM", "BcmBiaAssessment", saved.getId(),
            "DRP generated from BIA " + saved.getBiaCode() + " → drpId=" + drpId);
        return saved;
    }

    // ─── Stats ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Long> out = new HashMap<>();
        for (String s : STATUSES)     out.put("status_" + s.toLowerCase(),       repo.countByOrganisationIdAndApprovalStatus(org, s));
        for (String c : CRITICALITIES) out.put("crit_" + c.toLowerCase(),        repo.countByOrganisationIdAndOverallCriticality(org, c));
        return out;
    }

    // ─── Validation helpers ──────────────────────────────────────

    private void validateScope(BcmBiaAssessment b) {
        if (b.getServiceId() == null && b.getProcessId() == null)
            throw new IllegalArgumentException("Either serviceId or processId is required");
    }

    private void validateRtoMtpd(BcmBiaAssessment b) {
        if (b.getRtoHours() != null && b.getMtpdHours() != null
                && b.getRtoHours() > b.getMtpdHours())
            throw new IllegalArgumentException(
                "rtoHours (" + b.getRtoHours() + ") must be ≤ mtpdHours (" + b.getMtpdHours() + ")");
        if (b.getRtoHours() != null && b.getRtoHours() < 0)
            throw new IllegalArgumentException("rtoHours cannot be negative");
        if (b.getMtpdHours() != null && b.getMtpdHours() < 0)
            throw new IllegalArgumentException("mtpdHours cannot be negative");
        if (b.getRpoHours() != null && b.getRpoHours() < 0)
            throw new IllegalArgumentException("rpoHours cannot be negative");
    }

    private String nextCode() {
        Long org = TenantContext.getCurrentTenant();
        int year = Year.now().getValue();
        long count = repo.findByOrganisationIdOrderByIdDesc(org, PageRequest.of(0, 1)).getTotalElements();
        return String.format("BIA-%d-%06d", year, count + 1);
    }
}
