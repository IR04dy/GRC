package com.tss.grc.module.scm.resilience.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.scm.service.VendorReferenceResolver;
import com.tss.grc.module.scm.resilience.dto.SupplierResilienceDtos.PlanCreateRequest;
import com.tss.grc.module.scm.resilience.dto.SupplierResilienceDtos.PlanTestRequest;
import com.tss.grc.module.scm.resilience.dto.SupplierResilienceDtos.PlanUpdateRequest;
import com.tss.grc.module.scm.resilience.entity.ExitPlan;
import com.tss.grc.module.scm.resilience.repository.ExitPlanRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lifecycle service for {@link ExitPlan}.
 *
 * <p>State machine:
 * <pre>
 *   DRAFT ──approve()──▶ APPROVED ──activate()──▶ ACTIVATED
 *         ──reject()──▶ REJECTED
 *         ──archive()─▶ ARCHIVED
 *
 *   APPROVED ──archive()─▶ ARCHIVED
 *   ACTIVATED ──archive()─▶ ARCHIVED   (after disengagement complete)
 * </pre>
 *
 * <p>recordTest() is callable at any non-terminal state and updates the
 * last-tested outcome + next-test-due date.
 */
@Service
@RequiredArgsConstructor
public class ExitPlanService {

    private static final Set<String> TEST_OUTCOMES = Set.of("PASSED", "GAP_IDENTIFIED", "FAILED");

    private final ExitPlanRepository repo;

    /** F2-246 - the single writer of this register's (vendor id, vendor name) pair. */
    private final VendorReferenceResolver vendorRefs;

    @Transactional(readOnly = true)
    public Page<ExitPlan> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByCreatedAtDesc(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<ExitPlan> listByStatus(String status, Pageable pageable) {
        return repo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(
                TenantContext.getCurrentTenant(), status, pageable);
    }

    @Transactional(readOnly = true)
    public ExitPlan get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("ExitPlan " + id));
    }

    @Transactional(readOnly = true)
    public List<ExitPlan> byVendor(Long vendorId) {
        return repo.findByOrganisationIdAndVendorIdOrderByCreatedAtDesc(
                TenantContext.getCurrentTenant(), vendorId);
    }

    @Transactional
    public ExitPlan create(PlanCreateRequest req) {
        ExitPlan p = new ExitPlan();
        p.setOrganisationId(TenantContext.getCurrentTenant());
        p.setExternalId("EXP_" + UUID.randomUUID().toString().substring(0, 12));
        p.setPlanCode(generatePlanCode());
        // F2-246 - one resolver owns both halves of the vendor reference: an id is
        // resolved inside this tenant and the label DERIVED from the row it resolved;
        // with no id the caller's label is all that identifies the supplier, and the
        // row is disclosed as unlinked rather than reading like a resolved one.
        // The tenant is read directly rather than from the half-built row: a resolve
        // placed above the organisation stamp would pass null, the filter could never
        // match, and EVERY linked create would answer 404. Found by the live probe -
        // and the probe legs that expect a refusal cannot see it, because a refusal
        // caused by a null tenant looks exactly like a correct one.
        var vendor = vendorRefs.resolve(req.vendorId(), req.vendorName(),
                TenantContext.getCurrentTenant());
        p.setVendorId(vendor.id());
        p.setVendorName(vendor.name());
        p.setContractCode(req.contractCode());
        p.setTitleEn(req.titleEn());
        p.setTitleAr(req.titleAr());
        p.setTriggerConditions(req.triggerConditions());
        p.setSeverityThreshold(req.severityThreshold());
        p.setTargetDisengagementDays(req.targetDisengagementDays() != null ? req.targetDisengagementDays() : 90);
        p.setStepsJson(req.stepsJson());
        p.setDataMigrationPlanEn(req.dataMigrationPlanEn());
        p.setDataMigrationPlanAr(req.dataMigrationPlanAr());
        p.setKnowledgeTransferPlanEn(req.knowledgeTransferPlanEn());
        p.setKnowledgeTransferPlanAr(req.knowledgeTransferPlanAr());
        p.setStatus("DRAFT");
        return repo.save(p);
    }

    @Transactional
    public ExitPlan update(Long id, PlanUpdateRequest req) {
        ExitPlan p = get(id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), "DRAFT", Set.of("DRAFT"));
        }
        if (req.titleEn() != null) p.setTitleEn(req.titleEn());
        if (req.titleAr() != null) p.setTitleAr(req.titleAr());
        if (req.triggerConditions() != null) p.setTriggerConditions(req.triggerConditions());
        if (req.severityThreshold() != null) p.setSeverityThreshold(req.severityThreshold());
        if (req.targetDisengagementDays() != null) p.setTargetDisengagementDays(req.targetDisengagementDays());
        if (req.stepsJson() != null) p.setStepsJson(req.stepsJson());
        if (req.dataMigrationPlanEn() != null) p.setDataMigrationPlanEn(req.dataMigrationPlanEn());
        if (req.dataMigrationPlanAr() != null) p.setDataMigrationPlanAr(req.dataMigrationPlanAr());
        if (req.knowledgeTransferPlanEn() != null) p.setKnowledgeTransferPlanEn(req.knowledgeTransferPlanEn());
        if (req.knowledgeTransferPlanAr() != null) p.setKnowledgeTransferPlanAr(req.knowledgeTransferPlanAr());
        return p;
    }

    @Transactional
    public ExitPlan approve(Long id, String approverName, Long approverId) {
        ExitPlan p = get(id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), "APPROVED", Set.of("DRAFT"));
        }
        p.setStatus("APPROVED");
        p.setApprovedAt(LocalDateTime.now());
        p.setApprovedBy(approverId);
        p.setApproverName(approverName);
        return p;
    }

    @Transactional
    public ExitPlan reject(Long id, String reason) {
        ExitPlan p = get(id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), "REJECTED", Set.of("DRAFT"));
        }
        p.setStatus("REJECTED");
        p.setRejectionReason(reason);
        return p;
    }

    @Transactional
    public ExitPlan activate(Long id, String reason, Long actorId) {
        ExitPlan p = get(id);
        if (!"APPROVED".equals(p.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), "ACTIVATED", Set.of("APPROVED"));
        }
        p.setStatus("ACTIVATED");
        p.setActivatedAt(LocalDateTime.now());
        p.setActivatedBy(actorId);
        p.setActivationReason(reason);
        return p;
    }

    @Transactional
    public ExitPlan archive(Long id, String reason) {
        ExitPlan p = get(id);
        if ("ARCHIVED".equals(p.getStatus()) || "REJECTED".equals(p.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), "ARCHIVED",
                    Set.of("DRAFT", "APPROVED", "ACTIVATED"));
        }
        p.setStatus("ARCHIVED");
        p.setArchivedAt(LocalDateTime.now());
        p.setArchiveReason(reason);
        return p;
    }

    @Transactional
    public ExitPlan recordTest(Long id, PlanTestRequest req) {
        if (!TEST_OUTCOMES.contains(req.outcome())) {
            throw new IllegalArgumentException(
                    "Unknown test outcome '" + req.outcome() + "'; expected " + TEST_OUTCOMES);
        }
        ExitPlan p = get(id);
        if ("ARCHIVED".equals(p.getStatus()) || "REJECTED".equals(p.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), "TEST_RECORD",
                    Set.of("DRAFT", "APPROVED", "ACTIVATED"));
        }
        p.setLastTestedAt(LocalDateTime.now());
        p.setLastTestedOutcome(req.outcome());
        p.setNextTestDue(req.nextTestDue() != null ? req.nextTestDue()
                : LocalDate.now().plusMonths(6));
        return p;
    }

    private String generatePlanCode() {
        Long orgId = TenantContext.getCurrentTenant();
        String code = "EXP-" + Year.now().getValue() + "-" + shortHex();
        if (repo.findByOrganisationIdAndPlanCode(orgId, code).isPresent()) {
            code = "EXP-" + Year.now().getValue() + "-" + shortHex();
        }
        return code;
    }

    private String shortHex() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
