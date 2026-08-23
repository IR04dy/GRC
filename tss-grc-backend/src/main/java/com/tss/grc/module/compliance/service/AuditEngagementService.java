package com.tss.grc.module.compliance.service;

import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.AuditEngagement;
import com.tss.grc.module.compliance.repository.AuditEngagementRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * External audit engagement lifecycle — assessment §5.3 recommendation #3.
 */
@Service
@RequiredArgsConstructor
public class AuditEngagementService {

    private static final List<String> STATUS_ORDER = List.of(
        "PLANNED", "ENGAGED", "OPENING_MEETING", "FIELDWORK",
        "REPORTING", "MGMT_RESPONSE", "CLOSED", "FOLLOW_UP"
    );

    /**
     * F2-294 — DERIVED from {@link #STATUS_ORDER} rather than typed, so the state a create
     * may land in and the ladder {@code advance} walks cannot drift apart (F2-217's rule:
     * declare the initial state once and let both the default and the guard read it).
     */
    static final String INITIAL_STATUS = STATUS_ORDER.get(0);

    /** F2-294 — {@code AuditEngagementRequest} does not declare this field at all. */
    static final int DEFAULT_FINDINGS_COUNT = 0;

    private final AuditEngagementRepository repo;

    @Transactional(readOnly = true)
    public Page<AuditEngagement> list(String auditType, String status, String framework, Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        String at = blankToNull(auditType);
        String st = blankToNull(status);
        String fw = blankToNull(framework);
        return repo.findWithFilters(orgId, at, st, fw, pageable);
    }

    @Transactional(readOnly = true)
    public AuditEngagement get(Long id) {
        return require(id);
    }

    @Transactional
    public AuditEngagement create(AuditEngagement req) {
        AuditEngagement e = new AuditEngagement();
        e.setOrganisationId(TenantContext.getCurrentTenant());
        apply(e, req);
        // ⚠⚠ F2-294 — this default was DEAD CODE: `apply` copies the request's status and
        // the entity initialised it to "PLANNED", so `e.getStatus()` was never null. The
        // consequence was not only that the default never ran — it is that `apply` copying
        // a status the caller CHOSE let a create land in any state at all, skipping
        // `POST /{id}/advance` which is the declared route for that move (F2-292's
        // dead-default finding, same shape, different register).
        e.setStatus(ServerOwnedState.requireInitial(
            req.getStatus(), INITIAL_STATUS, "an audit engagement",
            "POST /api/v1/compliance/audit-engagements/{id}/advance"));
        // NOT NULL with no create-path writer before this: `AuditEngagementRequest` does
        // not declare the field at all, so the form could never send it and the guard in
        // `update` fired with the initialiser's 0 on every request.
        if (e.getFindingsCount() == null) e.setFindingsCount(DEFAULT_FINDINGS_COUNT);
        return repo.save(e);
    }

    @Transactional
    public AuditEngagement update(Long id, AuditEngagement req) {
        AuditEngagement e = require(id);
        apply(e, req);
        return repo.save(e);
    }

    @Transactional
    public void delete(Long id) {
        AuditEngagement e = require(id);
        e.setIsDeleted(true);
        repo.save(e);
    }

    /** Advance to a target status. Validates against the canonical order. */
    @Transactional
    public AuditEngagement advance(Long id, String targetStatus) {
        AuditEngagement e = require(id);
        int from = STATUS_ORDER.indexOf(e.getStatus());
        int to   = STATUS_ORDER.indexOf(targetStatus);
        if (to < 0) throw new IllegalArgumentException("Unknown status: " + targetStatus);
        if (to <= from) throw new IllegalArgumentException(
            "Cannot move from " + e.getStatus() + " to " + targetStatus + " — must advance forward only");
        e.setStatus(targetStatus);
        // Auto-stamp lifecycle dates on transitions
        LocalDate today = LocalDate.now();
        switch (targetStatus) {
            case "OPENING_MEETING" -> { if (e.getOpeningMeetingDate() == null) e.setOpeningMeetingDate(today); }
            case "FIELDWORK"       -> { if (e.getFieldworkStartDate() == null) e.setFieldworkStartDate(today); }
            case "REPORTING"       -> { if (e.getFieldworkEndDate()   == null) e.setFieldworkEndDate(today); }
            case "MGMT_RESPONSE"   -> { if (e.getReportIssuedDate()   == null) e.setReportIssuedDate(today); }
            case "CLOSED"          -> { if (e.getClosureDate()        == null) e.setClosureDate(today); }
            default -> { /* no-op */ }
        }
        return repo.save(e);
    }

    @Transactional
    public AuditEngagement scheduleFollowUp(Long id, LocalDate followupDate) {
        AuditEngagement e = require(id);
        if (followupDate == null) throw new IllegalArgumentException("followupDate is required");
        e.setStatus("FOLLOW_UP");
        e.setNextFollowupDate(followupDate);
        return repo.save(e);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Object> out = new HashMap<>();
        out.put("total",         repo.countByOrganisationId(org));
        for (String s : STATUS_ORDER) {
            out.put(s.toLowerCase(), repo.countByOrganisationIdAndStatus(org, s));
        }
        return out;
    }

    private void apply(AuditEngagement target, AuditEngagement src) {
        if (src.getEngagementCode()       != null) target.setEngagementCode(src.getEngagementCode());
        if (src.getTitleEn()              != null) target.setTitleEn(src.getTitleEn());
        if (src.getTitleAr()              != null) target.setTitleAr(src.getTitleAr());
        if (src.getAuditType()            != null) target.setAuditType(src.getAuditType());
        if (src.getFramework()            != null) target.setFramework(src.getFramework());
        if (src.getAuditorFirm()          != null) target.setAuditorFirm(src.getAuditorFirm());
        if (src.getLeadAuditor()          != null) target.setLeadAuditor(src.getLeadAuditor());
        if (src.getEngagementLetterDate() != null) target.setEngagementLetterDate(src.getEngagementLetterDate());
        if (src.getOpeningMeetingDate()   != null) target.setOpeningMeetingDate(src.getOpeningMeetingDate());
        if (src.getFieldworkStartDate()   != null) target.setFieldworkStartDate(src.getFieldworkStartDate());
        if (src.getFieldworkEndDate()     != null) target.setFieldworkEndDate(src.getFieldworkEndDate());
        if (src.getReportIssuedDate()     != null) target.setReportIssuedDate(src.getReportIssuedDate());
        if (src.getClosureDate()          != null) target.setClosureDate(src.getClosureDate());
        if (src.getNextFollowupDate()     != null) target.setNextFollowupDate(src.getNextFollowupDate());
        if (src.getScopeSummary()         != null) target.setScopeSummary(src.getScopeSummary());
        if (src.getStatus()               != null) target.setStatus(src.getStatus());
        if (src.getFindingsCount()        != null) target.setFindingsCount(src.getFindingsCount());
    }

    private AuditEngagement require(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findById(id)
            .filter(e -> e.getOrganisationId().equals(orgId))
            .orElseThrow(() -> new EntityNotFoundException("Audit engagement not found: " + id));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
