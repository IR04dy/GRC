package com.tss.grc.module.privacy.processor.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.processor.entity.ProcessorAssessment;
import com.tss.grc.module.privacy.processor.repository.ProcessorAssessmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle service for {@link ProcessorAssessment}.
 *
 * <p>The eight Art. 28 controls are each {@code PASS | PASS_WITH_CONDITIONS |
 * FAIL | NOT_APPLICABLE}. The overall outcome and risk score are derived from
 * the worst-case tier across the eight controls.
 */
@Service
@RequiredArgsConstructor
public class ProcessorAssessmentService {

    private static final List<String> STATUSES = List.of("IN_PROGRESS", "COMPLETED", "APPROVED", "REJECTED");
    private static final Set<String>  CONTROL_OUTCOMES = Set.of("PASS", "PASS_WITH_CONDITIONS", "FAIL", "NOT_APPLICABLE");

    private final ProcessorAssessmentRepository repo;

    @Transactional(readOnly = true)
    public Page<ProcessorAssessment> list(Pageable p) {
        return repo.findByOrganisationIdOrderByCreatedAtDesc(TenantContext.getCurrentTenant(), p);
    }
    @Transactional(readOnly = true)
    public Page<ProcessorAssessment> listByStatus(String status, Pageable p) {
        return repo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(TenantContext.getCurrentTenant(), status, p);
    }
    @Transactional(readOnly = true)
    public ProcessorAssessment get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("ProcessorAssessment " + id));
    }
    @Transactional
    public ProcessorAssessment create(ProcessorAssessment req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        req.setId(null);
        if (req.getStatus() == null) req.setStatus("IN_PROGRESS");
        return repo.save(req);
    }

    @Transactional
    public ProcessorAssessment scoreAndComplete(Long id, Map<String, String> controls) {
        ProcessorAssessment a = get(id);
        controls.forEach((k, v) -> {
            if (v != null && !CONTROL_OUTCOMES.contains(v))
                throw new IllegalArgumentException("Control outcome must be one of " + CONTROL_OUTCOMES);
            switch (k) {
                case "c1WrittenInstructions" -> a.setC1WrittenInstructions(v);
                case "c2Confidentiality"     -> a.setC2Confidentiality(v);
                case "c3SecurityMeasures"    -> a.setC3SecurityMeasures(v);
                case "c4SubProcessorAuth"    -> a.setC4SubProcessorAuth(v);
                case "c5BreachNotification"  -> a.setC5BreachNotification(v);
                case "c6DsrAssistance"       -> a.setC6DsrAssistance(v);
                case "c7DeletionOrReturn"    -> a.setC7DeletionOrReturn(v);
                case "c8AuditRights"         -> a.setC8AuditRights(v);
                default -> { /* ignore unknown keys */ }
            }
        });
        // Derive overall outcome + risk score
        String[] outcomes = {
                a.getC1WrittenInstructions(), a.getC2Confidentiality(), a.getC3SecurityMeasures(),
                a.getC4SubProcessorAuth(), a.getC5BreachNotification(), a.getC6DsrAssistance(),
                a.getC7DeletionOrReturn(), a.getC8AuditRights()
        };
        boolean anyFail = false; int conditional = 0; int evaluated = 0;
        for (String o : outcomes) {
            if (o == null || "NOT_APPLICABLE".equals(o)) continue;
            evaluated++;
            if ("FAIL".equals(o)) anyFail = true;
            if ("PASS_WITH_CONDITIONS".equals(o)) conditional++;
        }
        String overall = anyFail ? "FAIL" : conditional > 0 ? "PASS_WITH_CONDITIONS" : "PASS";
        a.setOverallOutcome(overall);
        a.setRiskScore(BigDecimal.valueOf(anyFail ? 8.0 : 1.0 + conditional * 1.5)
                .setScale(1, java.math.RoundingMode.HALF_UP));
        a.setStatus("COMPLETED");
        a.setAssessedAt(LocalDateTime.now());
        return repo.save(a);
    }

    @Transactional
    public ProcessorAssessment approve(Long id, String approverName) {
        ProcessorAssessment a = get(id);
        a.setStatus("APPROVED");
        a.setApprovedAt(LocalDateTime.now());
        a.setApproverName(approverName);
        return repo.save(a);
    }

    @Transactional
    public ProcessorAssessment reject(Long id) {
        ProcessorAssessment a = get(id);
        a.setStatus("REJECTED");
        return repo.save(a);
    }
}
