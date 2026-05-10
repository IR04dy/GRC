package com.tss.grc.module.capa.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.capa.entity.CapaTask;
import com.tss.grc.module.capa.repository.CapaTaskRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CAPA service. State machine:
 *   OPEN                  → IN_PROGRESS, CANCELLED
 *   IN_PROGRESS           → AWAITING_VERIFICATION, CANCELLED
 *   AWAITING_VERIFICATION → CLOSED, IN_REMEDIATION (failed verify reopens), CANCELLED
 *   CLOSED, CANCELLED → terminal
 *
 * Note: failed-verify path uses IN_PROGRESS (not a separate IN_REMEDIATION state)
 * to keep the state set tight. Verifier records the rejection in progress_notes.
 */
@Service
@RequiredArgsConstructor
public class CapaTaskService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "OPEN",                  Set.of("IN_PROGRESS", "CANCELLED"),
        "IN_PROGRESS",           Set.of("AWAITING_VERIFICATION", "CANCELLED"),
        "AWAITING_VERIFICATION", Set.of("CLOSED", "IN_PROGRESS", "CANCELLED")
    );

    private static final List<String> TERMINAL = List.of("CLOSED", "CANCELLED");

    private final CapaTaskRepository repo;

    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public List<CapaTaskDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByCreatedAtDesc(orgId).stream()
            .map(CapaTaskDto::from).collect(Collectors.toList());
    }

    public List<CapaTaskDto> listForOrigin(String originType, String originId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndOriginTypeAndOriginId(orgId, originType, originId).stream()
            .map(CapaTaskDto::from).collect(Collectors.toList());
    }

    public List<CapaTaskDto> overdue() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndStatusNotInAndTargetDateBefore(orgId, TERMINAL, LocalDate.now()).stream()
            .map(CapaTaskDto::from).collect(Collectors.toList());
    }

    public CapaTaskDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(CapaTaskDto::from)
            .orElseThrow(() -> new EntityNotFoundException("CAPA task not found: " + externalId));
    }

    @Transactional
    public CapaTaskDto create(CapaTaskDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        CapaTask t = new CapaTask();
        t.setExternalId(req.getId() != null ? req.getId() : generateExternalId());
        t.setOrganisationId(orgId);
        t.setOriginType(req.getOriginType());
        t.setOriginId(req.getOriginId());
        t.setTitleEn(req.getTitleEn());
        t.setTitleAr(req.getTitleAr());
        t.setDescriptionEn(req.getDescriptionEn());
        t.setDescriptionAr(req.getDescriptionAr());
        t.setCapaType(req.getCapaType());
        t.setSeverity(req.getSeverity());
        t.setOwner(req.getOwner());
        t.setTargetDate(req.getTargetDate());
        t.setStatus("OPEN");
        CapaTaskDto out = CapaTaskDto.from(repo.save(t));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "capa", t.getExternalId(), out);
        return out;
    }

    @Transactional
    public CapaTaskDto transition(String externalId, CapaTaskDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        CapaTask t = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("CAPA task not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(t.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(t.getStatus(), req.getStatus(), allowed);
        }
        LocalDateTime now = LocalDateTime.now();
        t.setStatus(req.getStatus());

        if ("IN_PROGRESS".equals(req.getStatus()) && t.getStartedAt() == null) {
            t.setStartedAt(now);
        }
        if ("AWAITING_VERIFICATION".equals(req.getStatus())) {
            t.setSubmittedForVerificationAt(now);
        }
        if ("CLOSED".equals(req.getStatus())) {
            t.setVerifiedAt(now);
            if (req.getVerifiedBy() != null) t.setVerifiedBy(req.getVerifiedBy());
            t.setClosedAt(now);
        }
        if ("CANCELLED".equals(req.getStatus())) {
            t.setCancelledAt(now);
            if (req.getCancellationReasonEn() != null) t.setCancellationReasonEn(req.getCancellationReasonEn());
        }
        if (req.getProgressNotesEn() != null) t.setProgressNotesEn(req.getProgressNotesEn());
        if (req.getProgressNotesAr() != null) t.setProgressNotesAr(req.getProgressNotesAr());
        if (req.getEvidenceRefs() != null)    t.setEvidenceRefs(req.getEvidenceRefs());

        CapaTaskDto out = CapaTaskDto.from(repo.save(t));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "capa", externalId, out);
        return out;
    }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        CapaTask t = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("CAPA task not found: " + externalId));
        t.setIsDeleted((short) 1);
        repo.save(t);
    }

    private String generateExternalId() {
        return String.format("CAPA-%d-%s",
            LocalDateTime.now().getYear(),
            Long.toHexString(System.currentTimeMillis()).toUpperCase().substring(0, 6));
    }
}
