package com.tss.grc.module.scm.resilience.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.scm.resilience.entity.SupplierResilienceTest;
import com.tss.grc.module.scm.resilience.repository.SupplierResilienceTestRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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
    public SupplierResilienceTest create(SupplierResilienceTest req) {
        if (!TYPES.contains(req.getTestType())) throw new IllegalArgumentException("testType must be one of " + TYPES);
        if (req.getScenarioScope() != null && !SCOPES.contains(req.getScenarioScope()))
            throw new IllegalArgumentException("scenarioScope must be one of " + SCOPES);
        req.setOrganisationId(TenantContext.getCurrentTenant());
        req.setId(null);
        if (req.getStatus() == null) req.setStatus("SCHEDULED");
        return repo.save(req);
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
        return repo.save(a);
    }

    @Transactional
    public SupplierResilienceTest approve(Long id, String approverName) {
        SupplierResilienceTest a = get(id);
        a.setStatus("APPROVED");
        a.setApprovedAt(LocalDateTime.now());
        a.setApproverName(approverName);
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
