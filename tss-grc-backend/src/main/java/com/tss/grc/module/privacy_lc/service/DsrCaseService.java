package com.tss.grc.module.privacy_lc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import com.tss.grc.module.privacy_lc.dto.DsrCaseDto;
import com.tss.grc.module.privacy_lc.entity.DsrCase;
import com.tss.grc.module.privacy_lc.repository.DsrCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DSR service — PDPL Article 4 30-day SLA + state machine.
 *
 *   INTAKE              → VERIFYING_IDENTITY
 *   VERIFYING_IDENTITY  → COLLECTING
 *   COLLECTING          → REDACTING
 *   REDACTING           → DELIVERED
 *   (any non-DELIVERED) → EXTENDED (+30 days, max 60 day total per Article 4)
 *   (any non-DELIVERED) → REJECTED
 */
@Service
@RequiredArgsConstructor
public class DsrCaseService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "INTAKE",             Set.of("VERIFYING_IDENTITY", "REJECTED", "EXTENDED"),
        "VERIFYING_IDENTITY", Set.of("COLLECTING", "REJECTED", "EXTENDED"),
        "COLLECTING",         Set.of("REDACTING", "REJECTED", "EXTENDED"),
        "REDACTING",          Set.of("DELIVERED", "EXTENDED"),
        "EXTENDED",           Set.of("VERIFYING_IDENTITY", "COLLECTING", "REDACTING", "DELIVERED", "REJECTED")
    );

    private final DsrCaseRepository repo;

    /** Sprint 4 §2.7 — optional realtime broadcaster (null in unit tests). */
    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public List<DsrCaseDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByFiledAtDesc(orgId).stream()
            .map(DsrCaseDto::from).collect(Collectors.toList());
    }

    public DsrCaseDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(DsrCaseDto::from)
            .orElseThrow(() -> new EntityNotFoundException("DSR not found: " + externalId));
    }

    @Transactional
    public DsrCaseDto create(DsrCaseDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        DsrCase d = new DsrCase();
        d.setExternalId(req.getId() != null ? req.getId() : generateExternalId());
        d.setOrganisationId(orgId);
        d.setType(req.getType());
        d.setSubjectName(req.getSubjectName());
        d.setNationalIdLast4(req.getNationalIdLast4());
        d.setContactEmail(req.getContactEmail());
        d.setStatus("INTAKE");
        d.setFiledAt(LocalDateTime.now());
        d.setSlaDeadline(LocalDate.now().plusDays(30));
        d.setRopaIds(req.getRopaIds());
        return DsrCaseDto.from(repo.save(d));
    }

    @Transactional
    public DsrCaseDto transition(String externalId, DsrCaseDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        DsrCase d = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("DSR not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(d.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(d.getStatus(), req.getStatus(), allowed);
        }
        LocalDateTime now = LocalDateTime.now();
        d.setStatus(req.getStatus());
        if ("VERIFYING_IDENTITY".equals(req.getStatus())) {
            d.setIdentityVerifiedAt(now);
            if (req.getVerificationMethod() != null) d.setVerificationMethod(req.getVerificationMethod());
        }
        if ("COLLECTING".equals(req.getStatus()))    d.setDataCollectedAt(now);
        if ("REDACTING".equals(req.getStatus()))     d.setRedactionCompletedAt(now);
        if ("DELIVERED".equals(req.getStatus())) {
            d.setDeliveredAt(now);
            if (req.getDeliveryMethod() != null) d.setDeliveryMethod(req.getDeliveryMethod());
        }
        if ("EXTENDED".equals(req.getStatus())) {
            d.setExtended(true);
            d.setSlaDeadline(d.getSlaDeadline().plusDays(30));  // Article 4: extend to 60 days total
        }
        if (req.getDataSummaryEn() != null) d.setDataSummaryEn(req.getDataSummaryEn());
        if (req.getDataSummaryAr() != null) d.setDataSummaryAr(req.getDataSummaryAr());
        DsrCaseDto out = DsrCaseDto.from(repo.save(d));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "dsr_case", externalId, out);
        return out;
    }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        DsrCase d = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("DSR not found: " + externalId));
        d.setIsDeleted((short) 1);
        repo.save(d);
    }

    public List<DsrCaseDto> overdue() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndStatusNotInAndSlaDeadlineBefore(
                orgId, List.of("DELIVERED", "REJECTED"), LocalDate.now()
            ).stream()
            .map(DsrCaseDto::from).collect(Collectors.toList());
    }

    private String generateExternalId() {
        return String.format("DSR-%d-%s",
            LocalDateTime.now().getYear(),
            Long.toHexString(System.currentTimeMillis()).toUpperCase().substring(0, 6));
    }
}
