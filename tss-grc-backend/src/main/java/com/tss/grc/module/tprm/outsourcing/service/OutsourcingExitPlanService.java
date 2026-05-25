package com.tss.grc.module.tprm.outsourcing.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ExitPlanCreateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ExitPlanResponse;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ExitPlanUpdateRequest;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingArrangement;
import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingExitPlan;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingArrangementRepository;
import com.tss.grc.module.tprm.outsourcing.repository.OutsourcingExitPlanRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 13 — Outsourcing exit-plan service.
 *
 * <p>One exit plan per arrangement. Test cadence options:
 * <ul>
 *   <li>{@code NEVER_TESTED} — bootstrap state, has not yet been tested</li>
 *   <li>{@code ANNUAL} — material critical-function arrangements; the
 *       default for SAMA-monitored functions (payments, core banking)</li>
 *   <li>{@code BIANNUAL} — material but lower-criticality arrangements
 *       (call centres, BPO)</li>
 *   <li>{@code NEVER} — board-accepted "don't bother" decision for
 *       non-material commodity arrangements</li>
 * </ul>
 *
 * <p>The cadence decision is captured here rather than at the
 * arrangement level because two arrangements with the same materiality
 * can have different cadences (e.g. ATM cash logistics is BIANNUAL, but
 * cloud-infrastructure exit is ANNUAL because the AWS / Azure
 * dependency change rate is higher).
 */
@Service
@RequiredArgsConstructor
public class OutsourcingExitPlanService {

    private final OutsourcingExitPlanRepository repo;
    private final OutsourcingArrangementRepository arrangementRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    @Transactional(readOnly = true)
    public ExitPlanResponse getForArrangement(Long arrangementId) {
        OutsourcingExitPlan p = repo.findByOrganisationIdAndArrangementIdAndIsDeletedFalse(org(), arrangementId)
            .orElseThrow(() -> new EntityNotFoundException("Exit plan not found for arrangement " + arrangementId));
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public List<ExitPlanResponse> findOverdueTest() {
        LocalDate today = LocalDate.now();
        return repo.findOverdueTest(org(), today.minusDays(365), today.minusDays(730))
            .stream().map(OutsourcingExitPlanService::toResponse).toList();
    }

    @Transactional
    public ExitPlanResponse create(ExitPlanCreateRequest req) {
        Long orgId = org();
        OutsourcingArrangement arr = arrangementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(orgId, req.arrangementId())
            .orElseThrow(() -> new EntityNotFoundException("Arrangement not found: " + req.arrangementId()));

        repo.findByOrganisationIdAndArrangementIdAndIsDeletedFalse(orgId, arr.getId())
            .ifPresent(p -> { throw new IllegalArgumentException(
                "Exit plan already exists for arrangement " + arr.getArrangementCode() + " (id=" + p.getId() + ")"); });

        OutsourcingExitPlan p = new OutsourcingExitPlan();
        p.setOrganisationId(orgId);
        p.setArrangementId(arr.getId());
        p.setTriggerConditionsEn(req.triggerConditionsEn());
        p.setTriggerConditionsAr(req.triggerConditionsAr());
        p.setExitStepsJson(req.exitStepsJson());
        p.setDataRepatriationPlanEn(req.dataRepatriationPlanEn());
        p.setDataRepatriationPlanAr(req.dataRepatriationPlanAr());
        p.setTestFrequency(req.testFrequency());
        p.setLastTestedDate(req.lastTestedDate());

        OutsourcingExitPlan saved = repo.save(p);
        audit.log("CREATE", "OutsourcingExitPlan", saved.getId(),
            "Exit plan created for arrangement " + arr.getArrangementCode()
                + " (cadence " + saved.getTestFrequency() + ")");
        return toResponse(saved);
    }

    @Transactional
    public ExitPlanResponse update(Long id, ExitPlanUpdateRequest req) {
        OutsourcingExitPlan p = loadOrThrow(id);
        if (req.triggerConditionsEn() != null)   p.setTriggerConditionsEn(req.triggerConditionsEn());
        if (req.triggerConditionsAr() != null)   p.setTriggerConditionsAr(req.triggerConditionsAr());
        if (req.exitStepsJson() != null)         p.setExitStepsJson(req.exitStepsJson());
        if (req.dataRepatriationPlanEn() != null) p.setDataRepatriationPlanEn(req.dataRepatriationPlanEn());
        if (req.dataRepatriationPlanAr() != null) p.setDataRepatriationPlanAr(req.dataRepatriationPlanAr());
        if (req.testFrequency() != null)         p.setTestFrequency(req.testFrequency());
        if (req.lastTestedDate() != null)        p.setLastTestedDate(req.lastTestedDate());
        p.setUpdatedAt(LocalDateTime.now());
        OutsourcingExitPlan saved = repo.save(p);
        audit.log("UPDATE", "OutsourcingExitPlan", saved.getId(),
            "Exit plan updated (arrangement " + saved.getArrangementId() + ")");
        return toResponse(saved);
    }

    @Transactional
    public ExitPlanResponse recordTest(Long id, LocalDate testedOn) {
        OutsourcingExitPlan p = loadOrThrow(id);
        LocalDate when = testedOn != null ? testedOn : LocalDate.now();
        p.setLastTestedDate(when);
        p.setUpdatedAt(LocalDateTime.now());
        OutsourcingExitPlan saved = repo.save(p);
        audit.log("TEST", "OutsourcingExitPlan", saved.getId(),
            "Exit plan tested on " + when + " (arrangement " + saved.getArrangementId() + ")");

        Map<String, Object> payload = new HashMap<>();
        payload.put("planId", String.valueOf(saved.getId()));
        payload.put("arrangementId", String.valueOf(saved.getArrangementId()));
        payload.put("testedOn", String.valueOf(when));
        payload.put("testFrequency", saved.getTestFrequency());
        publisher.publishEvent(new DomainEvent(
            saved.getOrganisationId(), "OUTSOURCING_EXIT_TESTED", "OutsourcingExitPlan", saved.getId(),
            "outsourcing-exit-tested:" + saved.getId(), payload));

        return toResponse(saved);
    }

    private OutsourcingExitPlan loadOrThrow(Long id) {
        return repo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Exit plan not found: " + id));
    }

    static ExitPlanResponse toResponse(OutsourcingExitPlan p) {
        return new ExitPlanResponse(
            p.getId(), p.getExternalId(), p.getOrganisationId(),
            p.getArrangementId(),
            p.getTriggerConditionsEn(), p.getTriggerConditionsAr(),
            p.getExitStepsJson(),
            p.getDataRepatriationPlanEn(), p.getDataRepatriationPlanAr(),
            p.getTestFrequency(), p.getLastTestedDate(),
            p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
