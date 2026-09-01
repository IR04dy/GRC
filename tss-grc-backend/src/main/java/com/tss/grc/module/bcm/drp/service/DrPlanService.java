package com.tss.grc.module.bcm.drp.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.drp.dto.DrPlanDtos.*;
import com.tss.grc.module.bcm.drp.entity.DisasterRecoveryPlan;
import com.tss.grc.module.bcm.drp.repository.DisasterRecoveryPlanRepository;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DrPlanService {

    private static final Set<String> TEST_OUTCOMES = Set.of("PASSED", "GAP", "FAILED");

    private final DisasterRecoveryPlanRepository repo;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<DisasterRecoveryPlan> list(String status, Pageable pageable) {
        Long org = TenantContext.getCurrentTenant();
        return status == null
                ? repo.findByOrganisationIdOrderByCreatedAtDesc(org, pageable)
                : repo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(org, status, pageable);
    }

    @Transactional(readOnly = true)
    public DisasterRecoveryPlan get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("DisasterRecoveryPlan " + id));
    }

    @Transactional
    public DisasterRecoveryPlan create(CreateRequest req) {
        DisasterRecoveryPlan p = new DisasterRecoveryPlan();
        p.setOrganisationId(TenantContext.getCurrentTenant());
        p.setExternalId("DRP_" + UUID.randomUUID().toString().substring(0, 12));
        p.setPlanCode(generateCode());
        p.setTitleEn(req.titleEn());
        p.setTitleAr(req.titleAr());
        p.setDescriptionEn(req.descriptionEn());
        p.setDescriptionAr(req.descriptionAr());
        p.setLinkedBcpCode(req.linkedBcpCode());
        p.setCriticalSystemName(req.criticalSystemName());
        p.setOwner(req.owner());
        p.setRtoHours(req.rtoHours());
        p.setRpoHours(req.rpoHours());
        p.setMtpdHours(req.mtpdHours());
        p.setDrSiteType(req.drSiteType());
        p.setDrSiteLocation(req.drSiteLocation());
        p.setDrSiteProvider(req.drSiteProvider());
        p.setRecoveryStrategyEn(req.recoveryStrategyEn());
        p.setRecoveryStrategyAr(req.recoveryStrategyAr());
        p.setDependenciesJson(req.dependenciesJson());
        p.setFailoverStepsJson(req.failoverStepsJson());
        p.setFailbackStepsJson(req.failbackStepsJson());
        p.setValidationStepsJson(req.validationStepsJson());
        p.setDataBackupStrategyEn(req.dataBackupStrategyEn());
        p.setDataBackupStrategyAr(req.dataBackupStrategyAr());
        p.setStatus("DRAFT");
        return repo.save(p);
    }

    @Transactional
    public DisasterRecoveryPlan update(Long id, UpdateRequest req) {
        DisasterRecoveryPlan p = get(id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), "EDIT", Set.of("DRAFT"));
        }
        if (req.titleEn() != null) p.setTitleEn(req.titleEn());
        if (req.titleAr() != null) p.setTitleAr(req.titleAr());
        if (req.descriptionEn() != null) p.setDescriptionEn(req.descriptionEn());
        if (req.descriptionAr() != null) p.setDescriptionAr(req.descriptionAr());
        if (req.linkedBcpCode() != null) p.setLinkedBcpCode(req.linkedBcpCode());
        if (req.criticalSystemName() != null) p.setCriticalSystemName(req.criticalSystemName());
        if (req.owner() != null) p.setOwner(req.owner());
        if (req.rtoHours() != null) p.setRtoHours(req.rtoHours());
        if (req.rpoHours() != null) p.setRpoHours(req.rpoHours());
        if (req.mtpdHours() != null) p.setMtpdHours(req.mtpdHours());
        if (req.drSiteType() != null) p.setDrSiteType(req.drSiteType());
        if (req.drSiteLocation() != null) p.setDrSiteLocation(req.drSiteLocation());
        if (req.drSiteProvider() != null) p.setDrSiteProvider(req.drSiteProvider());
        if (req.recoveryStrategyEn() != null) p.setRecoveryStrategyEn(req.recoveryStrategyEn());
        if (req.recoveryStrategyAr() != null) p.setRecoveryStrategyAr(req.recoveryStrategyAr());
        if (req.dependenciesJson() != null) p.setDependenciesJson(req.dependenciesJson());
        if (req.failoverStepsJson() != null) p.setFailoverStepsJson(req.failoverStepsJson());
        if (req.failbackStepsJson() != null) p.setFailbackStepsJson(req.failbackStepsJson());
        if (req.validationStepsJson() != null) p.setValidationStepsJson(req.validationStepsJson());
        if (req.dataBackupStrategyEn() != null) p.setDataBackupStrategyEn(req.dataBackupStrategyEn());
        if (req.dataBackupStrategyAr() != null) p.setDataBackupStrategyAr(req.dataBackupStrategyAr());
        if (req.nextTestDue() != null) p.setNextTestDue(req.nextTestDue());
        return p;
    }

    @Transactional
    public DisasterRecoveryPlan approve(Long id, String approverName, Long approverId) {
        DisasterRecoveryPlan p = get(id);
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
    public DisasterRecoveryPlan reject(Long id, RejectRequest req) {
        DisasterRecoveryPlan p = get(id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), "REJECTED", Set.of("DRAFT"));
        }
        p.setStatus("REJECTED");
        p.setRejectionReason(req.reason());
        return p;
    }

    @Transactional
    public DisasterRecoveryPlan activate(Long id, ActivateRequest req, Long actorId) {
        DisasterRecoveryPlan p = get(id);
        if (!"APPROVED".equals(p.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), "ACTIVATED", Set.of("APPROVED"));
        }
        p.setStatus("ACTIVATED");
        p.setActivatedAt(LocalDateTime.now());
        p.setActivatedBy(actorId);
        p.setActivationReason(req.reason());
        return p;
    }

    @Transactional
    public DisasterRecoveryPlan archive(Long id, ArchiveRequest req) {
        DisasterRecoveryPlan p = get(id);
        if (Set.of("ARCHIVED", "REJECTED").contains(p.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), "ARCHIVED",
                    Set.of("DRAFT", "APPROVED", "ACTIVATED"));
        }
        p.setStatus("ARCHIVED");
        p.setArchivedAt(LocalDateTime.now());
        p.setArchiveReason(req.reason());
        return p;
    }

    @Transactional
    public DisasterRecoveryPlan recordTest(Long id, TestRequest req, Long actorId) {
        if (!TEST_OUTCOMES.contains(req.outcome())) {
            throw new IllegalArgumentException("Unknown test outcome '" + req.outcome() + "'");
        }
        DisasterRecoveryPlan p = get(id);
        if (Set.of("ARCHIVED", "REJECTED").contains(p.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), "RECORD_TEST",
                    Set.of("DRAFT", "APPROVED", "ACTIVATED"));
        }
        p.setLastTestedAt(LocalDateTime.now());
        p.setLastTestedBy(actorId);
        p.setLastTestedOutcome(req.outcome());
        if (req.notesEn() != null) p.setLastTestNotesEn(req.notesEn());
        p.setNextTestDue(req.nextTestDue() != null ? req.nextTestDue() : LocalDate.now().plusMonths(6));

        // Spec §7 rule 9: DR test fails → create corrective action. Emit an F8
        // event so the automation engine can fan out to the seeded BCM_DR_TEST_FAILED
        // rule (which now actually creates a CAPA, not just an audit row).
        // GAP outcome also escalates because the audit treats "GAP" as a non-passing
        // result — a Tier-3 / Tier-4 system needs at least a follow-up tracked.
        if ("FAILED".equals(req.outcome()) || "GAP".equals(req.outcome())) {
            eventPublisher.publishEvent(DomainEvent.of(
                p.getOrganisationId(),
                "BCM_DR_TEST_FAILED",
                "DisasterRecoveryPlan",
                p.getId(),
                Map.of(
                    "plan_code",  p.getPlanCode(),
                    "title_en",   p.getTitleEn() == null ? "" : p.getTitleEn(),
                    "outcome",    req.outcome(),
                    "notes",      req.notesEn() == null ? "" : req.notesEn(),
                    "actor_id",   actorId == null ? -1 : actorId
                )));
        }
        return p;
    }

    private String generateCode() {
        String code = "DRP-" + Year.now().getValue() + "-" + shortHex();
        Long org = TenantContext.getCurrentTenant();
        if (repo.findByOrganisationIdAndPlanCode(org, code).isPresent()) {
            code = "DRP-" + Year.now().getValue() + "-" + shortHex();
        }
        return code;
    }

    private String shortHex() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }
}
