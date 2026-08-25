package com.tss.grc.module.audit_eng.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_eng.dto.AuditEngagementDto;
import com.tss.grc.module.audit_eng.entity.AuditEngagement;
import com.tss.grc.module.audit_eng.repository.AuditEngagementRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import com.tss.grc.common.util.CsvTokens;
import com.tss.grc.common.util.ExternalIds;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Audit Engagement service. State machine (IIA-aligned):
 *   PLANNING   → FIELDWORK, CANCELLED
 *   FIELDWORK  → REPORTING, CANCELLED
 *   REPORTING  → FOLLOW_UP, CANCELLED
 *   FOLLOW_UP  → CLOSED
 *   CLOSED, CANCELLED → terminal
 *
 * Explicit bean name avoids collision with legacy
 * {@code com.tss.grc.module.compliance.service.AuditEngagementService}.
 */
@Service("auditEngAuditEngagementService")
@RequiredArgsConstructor
public class AuditEngagementService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "PLANNING",  Set.of("FIELDWORK", "CANCELLED"),
        "FIELDWORK", Set.of("REPORTING", "CANCELLED"),
        "REPORTING", Set.of("FOLLOW_UP", "CANCELLED"),
        "FOLLOW_UP", Set.of("CLOSED")
    );

    private final AuditEngagementRepository repo;

    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public List<AuditEngagementDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByPlanningStartedAtDesc(orgId).stream()
            .map(AuditEngagementDto::from).collect(Collectors.toList());
    }

    public List<AuditEngagementDto> listActive() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndStatusNotIn(orgId, List.of("CLOSED", "CANCELLED")).stream()
            .map(AuditEngagementDto::from).collect(Collectors.toList());
    }

    public AuditEngagementDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(AuditEngagementDto::from)
            .orElseThrow(() -> new EntityNotFoundException("Audit engagement not found: " + externalId));
    }

    @Transactional
    public AuditEngagementDto create(AuditEngagementDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        AuditEngagement e = new AuditEngagement();
        // ⚠⚠ F2-206 — F2-172(d)'s SEVENTH instance. The server mints the engagement reference.
        // An audit engagement's reference appears in the IIA working papers and findings that
        // cite it (F2-203 measured five disjoint engagement code spaces already); letting a
        // client name it adds a sixth by hand.
        e.setExternalId(generateExternalId());
        e.setOrganisationId(orgId);
        e.setEngagementCode(req.getEngagementCode());
        e.setTitleEn(req.getTitleEn()); e.setTitleAr(req.getTitleAr());
        e.setObjectiveEn(req.getObjectiveEn()); e.setObjectiveAr(req.getObjectiveAr());
        e.setScopeEn(req.getScopeEn()); e.setScopeAr(req.getScopeAr());
        e.setAuditType(req.getAuditType());
        e.setFramework(req.getFramework());
        e.setAuditorFirm(req.getAuditorFirm());
        e.setLeadAuditor(req.getLeadAuditor());
        e.setAuditTeam(req.getAuditTeam());
        e.setAuditCommitteeChair(req.getAuditCommitteeChair());
        e.setBudgetHours(req.getBudgetHours());
        e.setAnnualPlanId(req.getAnnualPlanId());
        e.setStatus("PLANNING");
        AuditEngagementDto out = AuditEngagementDto.from(repo.save(e));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "audit_engagement", e.getExternalId(), out);
        return out;
    }

    @Transactional
    public AuditEngagementDto transition(String externalId, AuditEngagementDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        AuditEngagement e = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Audit engagement not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(e.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(e.getStatus(), req.getStatus(), allowed);
        }
        LocalDateTime now = LocalDateTime.now();
        e.setStatus(req.getStatus());
        switch (req.getStatus()) {
            case "FIELDWORK"  -> e.setFieldworkStartedAt(now);
            case "REPORTING"  -> { e.setFieldworkEndedAt(now); e.setReportingStartedAt(now); }
            case "FOLLOW_UP"  -> { e.setReportIssuedAt(now); e.setFollowUpStartedAt(now); }
            case "CLOSED"     -> e.setClosedAt(now);
            case "CANCELLED"  -> {
                e.setCancelledAt(now);
                if (req.getCancellationReasonEn() != null) e.setCancellationReasonEn(req.getCancellationReasonEn());
                // F2-275 - the Arabic half of the same reason, under its OWN null guard so an
                // omitted Arabic reason leaves the stored one alone rather than clearing it.
                if (req.getCancellationReasonAr() != null) e.setCancellationReasonAr(req.getCancellationReasonAr());
            }
            default -> { /* no extra side-effects */ }
        }
        if (req.getActualHours() != null) e.setActualHours(req.getActualHours());
        if (req.getLinkedFindingIds() != null) {
            e.setLinkedFindingIds(req.getLinkedFindingIds());
            e.setFindingsCount(CsvTokens.countNonBlank(req.getLinkedFindingIds())); // F2-369: "".split(",") is 1, not 0
        }
        AuditEngagementDto out = AuditEngagementDto.from(repo.save(e));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "audit_engagement", externalId, out);
        return out;
    }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        AuditEngagement e = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Audit engagement not found: " + externalId));
        e.setIsDeleted((short) 1);
        repo.save(e);
    }

    private String generateExternalId() {
        // F2-48: substring(0, 6) kept the HIGH-ORDER hex digits, so this id changed once
        // per 2^20 ms (~17.5 min) and every ENG raised in one window collided on the unique
        // index. Shared generator now, so the tenth copy cannot reintroduce it.
        return ExternalIds.generate("ENG");
    }
}
