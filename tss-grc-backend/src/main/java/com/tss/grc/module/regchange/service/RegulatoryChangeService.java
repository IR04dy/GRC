package com.tss.grc.module.regchange.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.regchange.dto.RegulatoryChangeDto;
import com.tss.grc.module.regchange.entity.RegulatoryChange;
import com.tss.grc.module.regchange.repository.RegulatoryChangeRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * <p>Wired into F7 (audit) + F8 (automation). Regulatory change is the
 * compliance team's intake-to-implementation pipeline — every new NCA
 * ECC revision, SAMA circular, PDPL clarification, or DGA framework
 * update enters here. Events let downstream consumers (CAPA fan-out,
 * board pack, framework mapping refresh) react automatically:
 *
 *   REG_CHANGE_NEW                — new change logged
 *   REG_CHANGE_ASSESSED           — applicability decided
 *   REG_CHANGE_IMPACT_ANALYSED    — impact + scope quantified
 *   REG_CHANGE_IN_IMPLEMENTATION  — action plan launched
 *   REG_CHANGE_IMPLEMENTED        — controls/policies updated
 *   REG_CHANGE_CLOSED             — terminal close
 *   REG_CHANGE_DEPRIORITISED      — not applicable / deferred
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
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    // ── F8 payload + emit ─────────────────────────────────────────────────────

    private Map<String, Object> payloadFor(RegulatoryChange c, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("regChangeId",        nullSafeStr(c.getId()));
        m.put("externalId",         nullSafeStr(c.getExternalId()));
        m.put("regulator",          nullSafeStr(c.getRegulator()));
        m.put("citation",           nullSafeStr(c.getCitation()));
        m.put("status",             nullSafeStr(c.getStatus()));
        m.put("previousStatus",     nullSafeStr(previousStatus));
        m.put("applicability",      nullSafeStr(c.getApplicability()));
        m.put("impactScore",        nullSafeStr(c.getImpactScore()));
        m.put("effectiveAt",        nullSafeStr(c.getEffectiveAt()));
        m.put("implementationTargetDate", nullSafeStr(c.getImplementationTargetDate()));
        return m;
    }

    private static String nullSafeStr(Object o) { return o == null ? "" : o.toString(); }
    private static String nullSafeMsg(Object o) { return o == null ? "<not provided>" : o.toString(); }

    private void emit(String trigger, RegulatoryChange c, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            TenantContext.requireOrganisationId(), trigger, "RegulatoryChange", c.getId(),
            "reg_change:" + nullSafeStr(c.getExternalId()), payloadFor(c, previousStatus)));
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

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

    // ── Writes ────────────────────────────────────────────────────────────────

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
        c = repo.save(c);
        audit.log("CREATE", "RegulatoryChange", c.getId(),
            "Reg change logged: " + nullSafeMsg(c.getExternalId())
                + " (" + nullSafeMsg(c.getRegulator()) + " — " + nullSafeMsg(c.getCitation()) + ")");
        emit("REG_CHANGE_NEW", c, null);
        RegulatoryChangeDto out = RegulatoryChangeDto.from(c);
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
        String previousStatus = c.getStatus();
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

        c = repo.save(c);
        audit.log("TRANSITION", "RegulatoryChange", c.getId(),
            "Reg change " + nullSafeMsg(c.getExternalId())
                + " status " + previousStatus + " → " + req.getStatus());
        String trigger = switch (req.getStatus()) {
            case "ASSESSED"          -> "REG_CHANGE_ASSESSED";
            case "IMPACT_ANALYSED"   -> "REG_CHANGE_IMPACT_ANALYSED";
            case "IN_IMPLEMENTATION" -> "REG_CHANGE_IN_IMPLEMENTATION";
            case "IMPLEMENTED"       -> "REG_CHANGE_IMPLEMENTED";
            case "CLOSED"            -> "REG_CHANGE_CLOSED";
            case "DEPRIORITISED"     -> "REG_CHANGE_DEPRIORITISED";
            default                  -> "REG_CHANGE_TRANSITIONED";
        };
        emit(trigger, c, previousStatus);
        RegulatoryChangeDto out = RegulatoryChangeDto.from(c);
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
        audit.log("DELETE", "RegulatoryChange", c.getId(),
            "Reg change soft-deleted: " + nullSafeMsg(c.getExternalId()));
    }

    private String generateExternalId() {
        return String.format("RC-%d-%s",
            LocalDateTime.now().getYear(),
            Long.toHexString(System.currentTimeMillis()).toUpperCase().substring(0, 6));
    }
}
