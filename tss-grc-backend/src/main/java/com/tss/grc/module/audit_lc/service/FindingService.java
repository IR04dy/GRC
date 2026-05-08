package com.tss.grc.module.audit_lc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import com.tss.grc.module.audit_lc.dto.FindingDto;
import com.tss.grc.module.audit_lc.entity.Finding;
import com.tss.grc.module.audit_lc.repository.FindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finding service — guards the IIA-aligned state machine.
 *
 *   OPEN                  → MGMT_RESPONSE
 *   MGMT_RESPONSE         → IN_REMEDIATION
 *   IN_REMEDIATION        → AWAITING_VERIFICATION
 *   AWAITING_VERIFICATION → CLOSED
 *   (any non-CLOSED)      → REOPENED via separate endpoint (not implemented)
 */
@Service
@RequiredArgsConstructor
public class FindingService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "OPEN",                  Set.of("MGMT_RESPONSE"),
        "MGMT_RESPONSE",         Set.of("IN_REMEDIATION"),
        "IN_REMEDIATION",        Set.of("AWAITING_VERIFICATION"),
        "AWAITING_VERIFICATION", Set.of("CLOSED")
    );

    private final FindingRepository repo;

    /** Sprint 4 §2.7 — optional realtime broadcaster (null in unit tests). */
    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public List<FindingDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByRaisedAtDesc(orgId).stream()
            .map(FindingDto::from).collect(Collectors.toList());
    }

    public List<FindingDto> listForEngagement(String engagementCode) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndEngagementCodeOrderByRaisedAtDesc(orgId, engagementCode).stream()
            .map(FindingDto::from).collect(Collectors.toList());
    }

    public FindingDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(FindingDto::from)
            .orElseThrow(() -> new EntityNotFoundException("Finding not found: " + externalId));
    }

    @Transactional
    public FindingDto create(FindingDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        long count = repo.findByOrganisationIdAndEngagementCodeOrderByRaisedAtDesc(orgId, req.getEngagementCode()).size();
        Finding f = new Finding();
        f.setExternalId(req.getId() != null ? req.getId() : generateExternalId());
        f.setOrganisationId(orgId);
        f.setEngagementCode(req.getEngagementCode());
        f.setRefNumber(req.getRefNumber() != null ? req.getRefNumber() : String.format("FIN-%03d", count + 1));
        f.setTitleEn(req.getTitleEn()); f.setTitleAr(req.getTitleAr());
        f.setSeverity(req.getSeverity()); f.setStatus("OPEN");
        f.setControlCrosswalk(req.getControlCrosswalk());
        f.setConditionEn(req.getConditionEn()); f.setConditionAr(req.getConditionAr());
        f.setCriteriaEn(req.getCriteriaEn()); f.setCriteriaAr(req.getCriteriaAr());
        f.setCauseEn(req.getCauseEn()); f.setCauseAr(req.getCauseAr());
        f.setRecommendationEn(req.getRecommendationEn()); f.setRecommendationAr(req.getRecommendationAr());
        f.setTargetDate(req.getTargetDate() != null ? req.getTargetDate() : LocalDate.now().plusDays(30));
        f.setOwnerRole(req.getOwnerRole());
        return FindingDto.from(repo.save(f));
    }

    @Transactional
    public FindingDto transition(String externalId, FindingDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        Finding f = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Finding not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(f.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(f.getStatus(), req.getStatus(), allowed);
        }
        f.setStatus(req.getStatus());
        if (req.getMgmtResponseEn() != null) f.setMgmtResponseEn(req.getMgmtResponseEn());
        if (req.getMgmtResponseAr() != null) f.setMgmtResponseAr(req.getMgmtResponseAr());
        if (req.getMgmtResponseAuthor() != null) f.setMgmtResponseAuthor(req.getMgmtResponseAuthor());
        FindingDto out = FindingDto.from(repo.save(f));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "audit_finding", externalId, out);
        return out;
    }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        Finding f = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Finding not found: " + externalId));
        f.setIsDeleted((short) 1);
        repo.save(f);
    }

    private String generateExternalId() {
        return String.format("FIN-%d-%s",
            LocalDateTime.now().getYear(),
            Long.toHexString(System.currentTimeMillis()).toUpperCase().substring(0, 6));
    }
}
