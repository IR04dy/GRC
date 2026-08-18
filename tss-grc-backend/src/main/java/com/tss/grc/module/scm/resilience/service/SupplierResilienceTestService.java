package com.tss.grc.module.scm.resilience.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.scm.resilience.dto.SupplierResilienceDtos.TestCreateRequest;
import com.tss.grc.module.scm.resilience.entity.SupplierResilienceTest;
import com.tss.grc.module.scm.resilience.repository.SupplierResilienceTestRepository;
import com.tss.grc.module.tprm.automation.TprmAutomationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SupplierResilienceTestService {

    private static final Set<String> TYPES = Set.of(
            "TABLETOP", "FAILOVER_DRILL", "EXIT_REHEARSAL", "SLA_VALIDATION", "JOINT_CRISIS_SIM");
    private static final Set<String> SCOPES = Set.of("DESKTOP", "PARTIAL_INVOCATION", "FULL_INVOCATION");
    private static final Set<String> OUTCOMES = Set.of("PASS", "PASS_WITH_OBSERVATIONS", "FAIL", "NOT_RUN");

    private final SupplierResilienceTestRepository repo;
    @Autowired(required = false) private TprmAutomationService automation;

    @Transactional(readOnly = true)
    public Page<SupplierResilienceTest> list(Pageable p) {
        return repo.findByOrganisationIdOrderByScheduledForDescIdDesc(TenantContext.getCurrentTenant(), p);
    }
    @Transactional(readOnly = true)
    public Page<SupplierResilienceTest> listByStatus(String status, Pageable p) {
        return repo.findByOrganisationIdAndStatusOrderByScheduledForDescIdDesc(TenantContext.getCurrentTenant(), status, p);
    }
    @Transactional(readOnly = true)
    public SupplierResilienceTest get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("SupplierResilienceTest " + id));
    }

    @Transactional
    public SupplierResilienceTest create(TestCreateRequest req) {
        if (!TYPES.contains(req.testType())) throw new IllegalArgumentException("testType must be one of " + TYPES);
        if (req.scenarioScope() != null && !SCOPES.contains(req.scenarioScope()))
            throw new IllegalArgumentException("scenarioScope must be one of " + SCOPES);
        // Build a fresh entity from the DTO. status/outcome and all attribution,
        // audit, soft-delete and rowVersion columns are server-controlled
        // (SEC-FIN-004) — never taken from the request body.
        SupplierResilienceTest e = new SupplierResilienceTest();
        e.setVendorId(req.vendorId());
        e.setVendorName(req.vendorName());
        e.setTestCode(req.testCode());
        e.setTitleEn(req.titleEn());
        e.setTitleAr(req.titleAr());
        e.setTestType(req.testType());
        if (req.scenarioScope() != null) e.setScenarioScope(req.scenarioScope());
        e.setScenarioDescriptionEn(req.scenarioDescriptionEn());
        e.setScenarioDescriptionAr(req.scenarioDescriptionAr());
        e.setSuccessCriteriaEn(req.successCriteriaEn());
        e.setParticipants(req.participants());
        e.setLinkedAlternateId(req.linkedAlternateId());
        e.setLinkedExitPlanId(req.linkedExitPlanId());
        e.setLinkedBcmPlanId(req.linkedBcmPlanId());
        e.setScheduledFor(req.scheduledFor());
        e.setDuration(req.duration());
        e.setRtoTargetMinutes(req.rtoTargetMinutes());
        e.setFacilitator(req.facilitator());
        e.setNextTestDue(req.nextTestDue());
        e.setOrganisationId(TenantContext.getCurrentTenant());
        e.setId(null);
        e.setStatus("SCHEDULED");      // force initial workflow state
        e.setIsDeleted((short) 0);
        e.setIsActive((short) 1);
        return repo.save(e);
    }

    @Transactional
    public SupplierResilienceTest start(Long id) {
        SupplierResilienceTest a = get(id);
        a.setStatus("IN_PROGRESS");
        a.setExecutedAt(LocalDateTime.now());
        return repo.save(a);
    }

    @Transactional
    public SupplierResilienceTest complete(Long id, Map<String, Object> body) {
        SupplierResilienceTest a = get(id);
        if (body.get("outcome") instanceof String s && !OUTCOMES.contains(s))
            throw new IllegalArgumentException("outcome must be one of " + OUTCOMES);
        if (body.get("outcome") instanceof String s) a.setOutcome(s);
        if (body.get("rtoActualMinutes") instanceof Number n) a.setRtoActualMinutes(n.intValue());
        if (body.get("observationsEn") instanceof String s) a.setObservationsEn(s);
        if (body.get("actionItemsEn") instanceof String s) a.setActionItemsEn(s);
        if (body.get("evidenceRefs") instanceof String s) a.setEvidenceRefs(s);
        a.setStatus("COMPLETED");
        SupplierResilienceTest saved = repo.save(a);
        // Enhancement E: auto-CAPA on FAIL outcome.
        if ("FAIL".equals(saved.getOutcome()) && automation != null) {
            automation.onResilienceTestFailed(saved);
        }
        return saved;
    }

    /**
     * F2-236 — the approver is the authenticated principal, never the request.
     *
     * <p>This method stamps {@code approvedAt} from the server's own clock, so it has already
     * declared that it is the recorder of the approval; taking {@code approverName} from the body
     * split one attribution across two owners. The controller's default was
     * {@code body.getOrDefault("approverName", "admin")} — so an omitted approver was recorded as
     * the literal {@code "admin"}, which is a <b>real PLATFORM_ADMIN username in this platform</b>,
     * not a placeholder. Probed live: a forged name and the {@code "admin"} default were both
     * stored. The sibling routes in {@code SupplierResilienceController} have always passed
     * {@code usernameOf(auth)} — the correct artefact was in the adjacent controller of the same
     * package.
     */
    @Transactional
    public SupplierResilienceTest approve(Long id) {
        SupplierResilienceTest a = get(id);
        a.setStatus("APPROVED");
        a.setApprovedAt(LocalDateTime.now());
        a.setApproverName(CurrentActor.requireUsername("a resilience-test approval"));
        return repo.save(a);
    }

    @Transactional
    public SupplierResilienceTest cancel(Long id, String reason) {
        SupplierResilienceTest a = get(id);
        a.setStatus("CANCELLED");
        a.setCancellationReason(reason);
        return repo.save(a);
    }
}
