package com.tss.grc.module.privacy_lc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.privacy_lc.DsrCaseLifecycle;
import com.tss.grc.module.privacy_lc.dto.DsrCaseDto;
import com.tss.grc.module.privacy_lc.entity.DsrCase;
import com.tss.grc.module.privacy_lc.repository.DsrCaseRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import com.tss.grc.common.util.ExternalIds;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *
 * <p>Wired into the F7 (audit) + F8 (automation) loops. DSR is the
 * most regulator-watched workflow in the platform — Saudi Data Office
 * spot-checks the 30-day SLA, and a missed deadline is itself a
 * reportable incident. F8 events are deliberately verbose so
 * downstream rules can target specific lifecycle moments:
 *
 *   DSR_RECEIVED            — new case opened
 *   DSR_IDENTITY_VERIFIED   — identity check passed
 *   DSR_COLLECTING          — data extraction started
 *   DSR_REDACTING           — redaction in progress
 *   DSR_DELIVERED           — terminal success
 *   DSR_EXTENDED            — 30-day extension invoked (Article 4)
 *   DSR_REJECTED            — terminal denial
 *   DSR_TRANSITIONED        — catch-all
 */
@Service
@RequiredArgsConstructor
public class DsrCaseService {

    /**
     * F2-196: the machine now lives in {@link DsrCaseLifecycle} because three other classes
     * needed to know which of its states are terminal and each had invented its own answer —
     * one of them wrongly, excluding EXTENDED from the statutory breach sweep. Read it there;
     * do not restate it here or in a repository javadoc.
     */
    private static final Map<String, Set<String>> ALLOWED = DsrCaseLifecycle.ALLOWED;

    private final DsrCaseRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    /** Sprint 4 §2.7 — optional realtime broadcaster (null in unit tests). */
    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    // ── F8 payload + emit ─────────────────────────────────────────────────────

    private Map<String, Object> payloadFor(DsrCase d, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("dsrId",          nullSafeStr(d.getId()));
        m.put("externalId",     nullSafeStr(d.getExternalId()));
        m.put("type",           nullSafeStr(d.getType()));
        m.put("status",         nullSafeStr(d.getStatus()));
        m.put("previousStatus", nullSafeStr(previousStatus));
        m.put("slaDeadline",    nullSafeStr(d.getSlaDeadline()));
        m.put("extended",       nullSafeStr(d.getExtended()));
        m.put("filedAt",        nullSafeStr(d.getFiledAt()));
        return m;
    }

    /** "" for null so the rule engine's equality matcher cannot NPE. */
    private static String nullSafeStr(Object o) { return o == null ? "" : o.toString(); }

    /** "<not provided>" for null in human-readable audit messages. */
    private static String nullSafe(Object o) { return o == null ? "<not provided>" : o.toString(); }

    private void emit(String trigger, DsrCase d, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            TenantContext.requireOrganisationId(), trigger, "DsrCase", d.getId(),
            "dsr:" + d.getExternalId(), payloadFor(d, previousStatus)));
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

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

    // ── Writes ────────────────────────────────────────────────────────────────

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
        d = repo.save(d);
        audit.log("CREATE", "DsrCase", d.getId(),
            "DSR case received: " + nullSafe(d.getExternalId())
                + " type=" + nullSafe(d.getType())
                + " (PDPL Article 4 — 30-day SLA, deadline " + d.getSlaDeadline() + ")");
        emit("DSR_RECEIVED", d, null);
        return DsrCaseDto.from(d);
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
        String previousStatus = d.getStatus();
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
        d = repo.save(d);
        audit.log("TRANSITION", "DsrCase", d.getId(),
            "DSR " + nullSafe(d.getExternalId())
                + " status " + previousStatus + " → " + req.getStatus()
                + (("EXTENDED".equals(req.getStatus())) ? " (Article 4 extension; new deadline " + d.getSlaDeadline() + ")" : ""));
        String trigger = switch (req.getStatus()) {
            case "VERIFYING_IDENTITY" -> "DSR_IDENTITY_VERIFIED";
            case "COLLECTING"         -> "DSR_COLLECTING";
            case "REDACTING"          -> "DSR_REDACTING";
            case "DELIVERED"          -> "DSR_DELIVERED";
            case "EXTENDED"           -> "DSR_EXTENDED";
            case "REJECTED"           -> "DSR_REJECTED";
            default                   -> "DSR_TRANSITIONED";
        };
        emit(trigger, d, previousStatus);
        DsrCaseDto out = DsrCaseDto.from(d);
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
        audit.log("DELETE", "DsrCase", d.getId(),
            "DSR soft-deleted: " + nullSafe(d.getExternalId()));
    }

    public List<DsrCaseDto> overdue() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findOverdueOpen(orgId, LocalDate.now()).stream()
            .map(DsrCaseDto::from).collect(Collectors.toList());
    }

    private String generateExternalId() {
        // F2-48: substring(0, 6) kept the HIGH-ORDER hex digits, so this id changed once
        // per 2^20 ms (~17.5 min) and every DSR raised in one window collided on the unique
        // index. Shared generator now, so the tenth copy cannot reintroduce it.
        return ExternalIds.generate("DSR");
    }
}
