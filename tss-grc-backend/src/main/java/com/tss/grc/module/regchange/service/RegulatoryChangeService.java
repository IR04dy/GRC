package com.tss.grc.module.regchange.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.regchange.dto.RegulatoryChangeDto;
import com.tss.grc.module.regchange.entity.RegulatoryChange;
import com.tss.grc.module.regchange.repository.RegulatoryChangeRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Regulatory change tracker.
 *
 * State machine:
 *   NEW                → ASSESSED, DEPRIORITISED
 *   ASSESSED           → IMPACT_ANALYSED, DEPRIORITISED
 *   IMPACT_ANALYSED    → IN_IMPLEMENTATION, DEPRIORITISED
 *   IN_IMPLEMENTATION  → IMPLEMENTED, DEPRIORITISED
 *   IMPLEMENTED        → CLOSED
 *   CLOSED, DEPRIORITISED → terminal
 *
 * Explicit bean name avoids collision with legacy
 * {@code com.tss.grc.module.compliance.service.RegulatoryChangeService}.
 */
@Service("regchangeRegulatoryChangeService")
@RequiredArgsConstructor
public class RegulatoryChangeService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "NEW",               Set.of("ASSESSED", "DEPRIORITISED"),
        "ASSESSED",          Set.of("IMPACT_ANALYSED", "DEPRIORITISED"),
        "IMPACT_ANALYSED",   Set.of("IN_IMPLEMENTATION", "DEPRIORITISED"),
        "IN_IMPLEMENTATION", Set.of("IMPLEMENTED", "DEPRIORITISED"),
        "IMPLEMENTED",       Set.of("CLOSED")
    );

    private final RegulatoryChangeRepository repo;

    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public List<RegulatoryChangeDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByPublishedAtDesc(orgId).stream()
            .map(RegulatoryChangeDto::from).collect(Collectors.toList());
    }

    public List<RegulatoryChangeDto> byRegulator(String regulator) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndRegulator(orgId, regulator).stream()
            .map(RegulatoryChangeDto::from).collect(Collectors.toList());
    }

    public RegulatoryChangeDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(RegulatoryChangeDto::from)
            .orElseThrow(() -> new EntityNotFoundException("Regulatory change not found: " + externalId));
    }

    @Transactional
    public RegulatoryChangeDto create(RegulatoryChangeDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        RegulatoryChange c = new RegulatoryChange();
        c.setExternalId(req.getId() != null ? req.getId() : generateExternalId());
        c.setOrganisationId(orgId);
        c.setRegulator(req.getRegulator());
        c.setCitation(req.getCitation());
        c.setTitleEn(req.getTitleEn());
        c.setTitleAr(req.getTitleAr());
        c.setSummaryEn(req.getSummaryEn());
        c.setSummaryAr(req.getSummaryAr());
        c.setSourceUrl(req.getSourceUrl());
        c.setPublishedAt(req.getPublishedAt());
        c.setEffectiveAt(req.getEffectiveAt());
        c.setStatus("NEW");
        RegulatoryChangeDto out = RegulatoryChangeDto.from(repo.save(c));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "regchange", c.getExternalId(), out);
        return out;
    }

    @Transactional
    public RegulatoryChangeDto transition(String externalId, RegulatoryChangeDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        RegulatoryChange c = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Regulatory change not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(c.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(c.getStatus(), req.getStatus(), allowed);
        }
        LocalDateTime now = LocalDateTime.now();
        c.setStatus(req.getStatus());

        if ("ASSESSED".equals(req.getStatus())) {
            c.setAssessedAt(now);
            if (req.getAssessedBy() != null) c.setAssessedBy(req.getAssessedBy());
            if (req.getApplicability() != null) c.setApplicability(req.getApplicability());
        }
        if ("IMPACT_ANALYSED".equals(req.getStatus())) {
            if (req.getImpactScore() != null) c.setImpactScore(req.getImpactScore());
            if (req.getImpactSummaryEn() != null) c.setImpactSummaryEn(req.getImpactSummaryEn());
            if (req.getImpactSummaryAr() != null) c.setImpactSummaryAr(req.getImpactSummaryAr());
        }
        if ("IN_IMPLEMENTATION".equals(req.getStatus())) {
            c.setImplementationStartedAt(now);
            if (req.getImplementationOwner() != null) c.setImplementationOwner(req.getImplementationOwner());
            if (req.getImplementationTargetDate() != null) c.setImplementationTargetDate(req.getImplementationTargetDate());
        }
        if ("IMPLEMENTED".equals(req.getStatus())) {
            c.setImplementationCompletedAt(now);
        }
        if ("CLOSED".equals(req.getStatus())) {
            c.setClosedAt(now);
        }
        if ("DEPRIORITISED".equals(req.getStatus())) {
            if (req.getDeprioritisationReasonEn() != null) c.setDeprioritisationReasonEn(req.getDeprioritisationReasonEn());
        }
        if (req.getLinkedControls() != null) c.setLinkedControls(req.getLinkedControls());
        if (req.getLinkedCapaIds() != null)  c.setLinkedCapaIds(req.getLinkedCapaIds());
        if (req.getLinkedEvidence() != null) c.setLinkedEvidence(req.getLinkedEvidence());

        RegulatoryChangeDto out = RegulatoryChangeDto.from(repo.save(c));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "regchange", externalId, out);
        return out;
    }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        RegulatoryChange c = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Regulatory change not found: " + externalId));
        c.setIsDeleted((short) 1);
        repo.save(c);
    }

    private String generateExternalId() {
        return String.format("RC-%d-%s",
            LocalDateTime.now().getYear(),
            Long.toHexString(System.currentTimeMillis()).toUpperCase().substring(0, 6));
    }
}
