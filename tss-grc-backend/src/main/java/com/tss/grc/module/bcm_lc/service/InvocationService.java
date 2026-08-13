package com.tss.grc.module.bcm_lc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import com.tss.grc.module.bcm_lc.dto.InvocationDto;
import com.tss.grc.module.bcm_lc.entity.Invocation;
import com.tss.grc.module.bcm_lc.repository.InvocationRepository;
import com.tss.grc.common.util.ExternalIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BCM Invocation service — NCEMA-aligned state machine.
 *
 *   STANDBY              → DECLARED
 *   DECLARED             → INVOKED, STAND_DOWN
 *   INVOKED              → STABILISED, STAND_DOWN
 *   STABILISED           → STAND_DOWN
 *   STAND_DOWN           → POST_INCIDENT_REVIEW
 *   POST_INCIDENT_REVIEW → CLOSED
 *
 * STAND_DOWN can be reached from any active state when the disruption is contained
 * before formal stabilisation (e.g., short-duration outages).
 */
@Service
@RequiredArgsConstructor
public class InvocationService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "STANDBY",              Set.of("DECLARED"),
        "DECLARED",             Set.of("INVOKED", "STAND_DOWN"),
        "INVOKED",              Set.of("STABILISED", "STAND_DOWN"),
        "STABILISED",           Set.of("STAND_DOWN"),
        "STAND_DOWN",           Set.of("POST_INCIDENT_REVIEW"),
        "POST_INCIDENT_REVIEW", Set.of("CLOSED")
    );

    private static final List<String> CLOSED_STATES = List.of("CLOSED");

    private final InvocationRepository repo;

    /** Sprint 4 §2.7 — optional realtime broadcaster (null in unit tests). */
    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public List<InvocationDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByDetectedAtDesc(orgId).stream()
            .map(InvocationDto::from).collect(Collectors.toList());
    }

    public List<InvocationDto> listActive() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndStatusNotInOrderByDetectedAtDesc(orgId, CLOSED_STATES).stream()
            .map(InvocationDto::from).collect(Collectors.toList());
    }

    public InvocationDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(InvocationDto::from)
            .orElseThrow(() -> new EntityNotFoundException("Invocation not found: " + externalId));
    }

    @Transactional
    public InvocationDto create(InvocationDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        Invocation v = new Invocation();
        // F2-208 — swept. See ClientSuppliedReferenceRatchetTest for why this whole family is
        // now a hard zero rather than one fix per slice. A BCP invocation reference is raised
        // DURING a crisis and cited in the NCEMA after-action record; two invocations sharing
        // one reference is unresolvable after the fact.
        v.setExternalId(generateExternalId());
        v.setOrganisationId(orgId);
        v.setTitleEn(req.getTitleEn());
        v.setTitleAr(req.getTitleAr());
        v.setSeverity(req.getSeverity());
        v.setTriggerEventEn(req.getTriggerEventEn());
        v.setTriggerEventAr(req.getTriggerEventAr());
        v.setAffectedBiaIds(req.getAffectedBiaIds());
        v.setAffectedSites(req.getAffectedSites());
        v.setIncidentCommander(req.getIncidentCommander());
        v.setBcmCoordinator(req.getBcmCoordinator());
        v.setStatus("STANDBY");
        v.setDetectedAt(LocalDateTime.now());
        return InvocationDto.from(repo.save(v));
    }

    @Transactional
    public InvocationDto transition(String externalId, InvocationDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        Invocation v = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Invocation not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(v.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(v.getStatus(), req.getStatus(), allowed);
        }
        LocalDateTime now = LocalDateTime.now();
        v.setStatus(req.getStatus());

        if ("DECLARED".equals(req.getStatus())) {
            v.setDeclaredAt(now);
            if (req.getDeclaredBy() != null) v.setDeclaredBy(req.getDeclaredBy());
        }
        if ("INVOKED".equals(req.getStatus())) {
            v.setInvokedAt(now);
            if (req.getInvokedStrategies() != null) v.setInvokedStrategies(req.getInvokedStrategies());
        }
        if ("STABILISED".equals(req.getStatus())) {
            v.setStabilisedAt(now);
            if (req.getActualRtoHours() != null) v.setActualRtoHours(req.getActualRtoHours());
            if (req.getActualRpoHours() != null) v.setActualRpoHours(req.getActualRpoHours());
        }
        if ("STAND_DOWN".equals(req.getStatus())) {
            v.setStandDownAt(now);
            if (req.getStandDownAuthoriser() != null) v.setStandDownAuthoriser(req.getStandDownAuthoriser());
            if (req.getEstimatedLossSar() != null)    v.setEstimatedLossSar(req.getEstimatedLossSar());
        }
        if ("POST_INCIDENT_REVIEW".equals(req.getStatus()) || "CLOSED".equals(req.getStatus())) {
            if (req.getPirFindingsEn() != null) v.setPirFindingsEn(req.getPirFindingsEn());
            if (req.getPirFindingsAr() != null) v.setPirFindingsAr(req.getPirFindingsAr());
            if (req.getPirCapaCount() != null)  v.setPirCapaCount(req.getPirCapaCount());
            if ("CLOSED".equals(req.getStatus())) v.setReviewCompletedAt(now);
        }
        if (req.getRegulatorReference() != null) {
            v.setRegulatorReference(req.getRegulatorReference());
            if (v.getRegulatorNotifiedAt() == null) v.setRegulatorNotifiedAt(now);
        }
        if (Boolean.TRUE.equals(req.getSamaNotified()))  v.setSamaNotified(true);
        if (Boolean.TRUE.equals(req.getNcemaNotified())) v.setNcemaNotified(true);

        InvocationDto out = InvocationDto.from(repo.save(v));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "bcm_invocation", externalId, out);
        return out;
    }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        Invocation v = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Invocation not found: " + externalId));
        v.setIsDeleted((short) 1);
        repo.save(v);
    }

    private String generateExternalId() {
        // F2-48: substring(0, 6) kept the HIGH-ORDER hex digits, so this id changed once
        // per 2^20 ms (~17.5 min) and every INV raised in one window collided on the unique
        // index. Shared generator now, so the tenth copy cannot reintroduce it.
        return ExternalIds.generate("INV");
    }
}
