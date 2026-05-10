package com.tss.grc.module.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CAPA closure verification workflow — assessment §5.3 recommendation #6.
 *
 * Stage flow on AuditFinding:
 *   IDENTIFIED → IMPLEMENTING → OWNER_TESTED → INDEPENDENT_TESTED
 *              → AUDIT_SIGNED_OFF → CLOSED
 */
@Service
@RequiredArgsConstructor
public class CapaWorkflowService {

    private static final List<String> STAGE_ORDER = List.of(
        "IDENTIFIED", "IMPLEMENTING", "OWNER_TESTED",
        "INDEPENDENT_TESTED", "AUDIT_SIGNED_OFF", "CLOSED"
    );

    private static final Set<String> RESULT_VALUES = Set.of("PASS", "FAIL", "PARTIAL");

    private final AuditFindingRepository findingRepo;

    @Transactional
    public AuditFinding markImplementing(Long findingId) {
        AuditFinding f = require(findingId);
        requireStage(f, "IDENTIFIED");
        f.setCapaStage("IMPLEMENTING");
        return findingRepo.save(f);
    }

    @Transactional
    public AuditFinding ownerTest(Long findingId, Map<String, Object> body) {
        AuditFinding f = require(findingId);
        requireStage(f, "IMPLEMENTING");
        String result = str(body.get("result"));
        if (result == null || !RESULT_VALUES.contains(result))
            throw new IllegalArgumentException("result must be one of: PASS, FAIL, PARTIAL");
        f.setOwnerTestAt(LocalDateTime.now());
        f.setOwnerTestResult(result);
        if (body.get("evidenceId") instanceof Number n) f.setOwnerTestEvidenceId(n.longValue());
        f.setOwnerTestNotes(str(body.get("notes")));
        // FAIL keeps stage at IMPLEMENTING for rework; PASS/PARTIAL advances
        if ("FAIL".equals(result)) {
            f.setCapaStage("IMPLEMENTING");
        } else {
            f.setCapaStage("OWNER_TESTED");
        }
        return findingRepo.save(f);
    }

    @Transactional
    public AuditFinding independentTest(Long findingId, Map<String, Object> body) {
        AuditFinding f = require(findingId);
        requireStage(f, "OWNER_TESTED");
        String result = str(body.get("result"));
        if (result == null || !RESULT_VALUES.contains(result))
            throw new IllegalArgumentException("result must be one of: PASS, FAIL, PARTIAL");
        f.setIndependentTestAt(LocalDateTime.now());
        f.setIndependentTestResult(result);
        if (body.get("byUserId") instanceof Number n) f.setIndependentTestById(n.longValue());
        f.setIndependentTestNotes(str(body.get("notes")));
        if ("FAIL".equals(result)) {
            // Send back to owner re-testing
            f.setCapaStage("IMPLEMENTING");
        } else {
            f.setCapaStage("INDEPENDENT_TESTED");
        }
        return findingRepo.save(f);
    }

    @Transactional
    public AuditFinding auditSignoff(Long findingId, Map<String, Object> body) {
        AuditFinding f = require(findingId);
        requireStage(f, "INDEPENDENT_TESTED");
        f.setAuditSignoffAt(LocalDateTime.now());
        if (body.get("byUserId") instanceof Number n) f.setAuditSignoffById(n.longValue());
        f.setAuditSignoffNotes(str(body.get("notes")));
        f.setCapaStage("AUDIT_SIGNED_OFF");
        return findingRepo.save(f);
    }

    @Transactional
    public AuditFinding close(Long findingId) {
        AuditFinding f = require(findingId);
        requireStage(f, "AUDIT_SIGNED_OFF");
        f.setCapaStage("CLOSED");
        f.setStatus("CLOSED");
        return findingRepo.save(f);
    }

    private AuditFinding require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return findingRepo.findById(id)
            .filter(f -> f.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Finding not found: " + id));
    }

    private void requireStage(AuditFinding f, String expected) {
        String actual = f.getCapaStage() != null ? f.getCapaStage() : "IDENTIFIED";
        if (!STAGE_ORDER.contains(actual))
            throw new IllegalArgumentException("Unknown CAPA stage on finding: " + actual);
        if (!actual.equals(expected))
            throw new IllegalArgumentException(
                "Cannot perform action — finding is in stage " + actual + ", expected " + expected);
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }
}
