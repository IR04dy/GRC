package com.tss.grc.module.privacy.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.entity.DataBreach;
import com.tss.grc.module.privacy.entity.DataSubjectRequest;
import com.tss.grc.module.privacy.entity.ProcessingActivity;
import com.tss.grc.module.privacy.repository.ConsentRecordRepository;
import com.tss.grc.module.privacy.repository.DataBreachRepository;
import com.tss.grc.module.privacy.repository.DataSubjectRequestRepository;
import com.tss.grc.module.privacy.repository.DpiaRepository;
import com.tss.grc.module.privacy.repository.ProcessingActivityRepository;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class PrivacyService {

    private final DataBreachRepository breachRepo;
    private final DataSubjectRequestRepository dsrRepo;
    private final ProcessingActivityRepository ropaRepo;
    private final DpiaRepository dpiaRepo;
    private final ConsentRecordRepository consentRepo;

    @Autowired(required = false)
    private BroadcastHelper bcast;

    /** Open Issue #006 — optional F7 audit sink for the SDAIA submission flow. */
    @Autowired(required = false)
    private com.tss.grc.module.platform.auditlog.service.AuditLogService auditLog;

    /** Open Issue #006 — optional F8 publisher for the SDAIA submission flow. */
    @Autowired(required = false)
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    private void emitBreach(DataBreach b) {
        if (bcast != null) bcast.entity(b.getOrganisationId(), "data_breach", b.getId(), b);
    }
    private void emitDsr(DataSubjectRequest r) {
        if (bcast != null) bcast.entity(r.getOrganisationId(), "dsr_case", r.getId(), r);
    }
    private void emitRopa(ProcessingActivity a) {
        if (bcast != null) bcast.entity(a.getOrganisationId(), "processing_activity", a.getId(), a);
    }

    // ── Data Breaches ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DataBreach> listBreaches(Pageable pageable) {
        return breachRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public DataBreach getBreach(Long id) {
        return breachRepo.findById(id)
            .filter(b -> b.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Breach not found: " + id));
    }

    @Transactional
    public DataBreach createBreach(DataBreach req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        return breachRepo.save(req);
    }

    @Transactional
    public DataBreach updateBreach(Long id, DataBreach req) {
        DataBreach existing = getBreach(id);
        existing.setTitleEn(req.getTitleEn());
        existing.setDescriptionEn(req.getDescriptionEn());
        existing.setBreachType(req.getBreachType());
        existing.setDiscoveredAt(req.getDiscoveredAt());
        existing.setDataSubjectsAffectedCount(req.getDataSubjectsAffectedCount());
        existing.setPdpcNotificationRequired(req.getPdpcNotificationRequired());
        existing.setPdpcNotifiedAt(req.getPdpcNotifiedAt());
        existing.setStatus(req.getStatus());
        return breachRepo.save(existing);
    }

    @Transactional
    public void deleteBreach(Long id) {
        DataBreach b = getBreach(id);
        b.setIsDeleted(true);
        breachRepo.save(b);
    }

    // ── SDAIA Notification Workflow (Open Issue #006) ─────────────────────────
    //
    // PDPL Article 31 (Implementing Regulations Art. 40) — the controller
    // must notify SDAIA within 72 hours of awareness for material personal-data
    // breaches. SDAIA has no machine-readable API; submission is via the SDAIA
    // portal (https://www.sdaia.gov.sa/) and the DPO records the outcome here.
    //
    // The workflow is a simple state machine:
    //   NOT_REPORTED  → IN_PREPARATION | SUBMITTED
    //   IN_PREPARATION → SUBMITTED | NOT_REPORTED
    //   SUBMITTED     → ACKNOWLEDGED
    //   ACKNOWLEDGED  (terminal)

    /** Valid SDAIA notification states. */
    private static final java.util.Set<String> SDAIA_STATES = java.util.Set.of(
        "NOT_REPORTED", "IN_PREPARATION", "SUBMITTED", "ACKNOWLEDGED");

    /**
     * Record a manual SDAIA submission outcome (Open Issue #006).
     *
     * <p>Called by the DPO (or CISO/PLATFORM_ADMIN) after they have posted the
     * breach report via the SDAIA portal. Transitions the breach to status
     * {@code SUBMITTED} (or {@code ACKNOWLEDGED} when SDAIA returns a reference),
     * stamps the submission reference and timestamp, and emits an F7 audit row
     * + F8 trigger {@code BREACH_REPORTED_TO_SDAIA}. The PDPL Article 31 72-hour
     * clock is discharged when {@code sdaia_submitted_at} is set.
     */
    @Transactional
    public DataBreach recordSdaiaSubmission(Long id,
                                            String submissionRef,
                                            java.time.LocalDateTime submittedAt,
                                            String targetStatus) {
        DataBreach breach = getBreach(id);
        String resolvedStatus = targetStatus == null ? "SUBMITTED" : targetStatus.toUpperCase();
        if (!SDAIA_STATES.contains(resolvedStatus)) {
            throw new IllegalArgumentException(
                "Invalid SDAIA status: " + targetStatus +
                " (allowed: NOT_REPORTED, IN_PREPARATION, SUBMITTED, ACKNOWLEDGED)");
        }
        String prior = breach.getSdaiaNotificationStatus() == null
            ? "NOT_REPORTED" : breach.getSdaiaNotificationStatus();
        // Guard: ACKNOWLEDGED is terminal — re-opening would lose the regulator
        // closure record. Only an explicit re-classification (handled outside
        // this endpoint, via the privacy admin path) can roll it back.
        if ("ACKNOWLEDGED".equals(prior) && !"ACKNOWLEDGED".equals(resolvedStatus)) {
            throw new IllegalStateException(
                "Breach " + id + " already ACKNOWLEDGED by SDAIA — cannot move to " + resolvedStatus);
        }
        breach.setSdaiaNotificationStatus(resolvedStatus);
        if (submissionRef != null && !submissionRef.isBlank()) {
            breach.setSdaiaSubmissionRef(submissionRef);
        }
        if (submittedAt != null) {
            breach.setSdaiaSubmittedAt(submittedAt);
        } else if ("SUBMITTED".equals(resolvedStatus) && breach.getSdaiaSubmittedAt() == null) {
            // No explicit timestamp provided — stamp now (DPO submitted "just now").
            breach.setSdaiaSubmittedAt(java.time.LocalDateTime.now());
        }
        DataBreach saved = breachRepo.save(breach);
        emitBreach(saved);

        // F7 audit + F8 event
        try {
            if (auditLog != null) {
                auditLog.log("BREACH_REPORTED_TO_SDAIA", "DataBreach", saved.getId(),
                    String.format("SDAIA status: %s -> %s (ref=%s)", prior, resolvedStatus,
                        saved.getSdaiaSubmissionRef() == null ? "-" : saved.getSdaiaSubmissionRef()));
            }
        } catch (Exception ex) {
            // Audit failures must not block the regulator-discharge path.
            log.warn("F7 audit log failed for SDAIA submission on breach {}", id, ex);
        }
        try {
            if (eventPublisher != null) {
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("breachId",       saved.getId());
                payload.put("priorStatus",    prior);
                payload.put("newStatus",      resolvedStatus);
                payload.put("submissionRef",  saved.getSdaiaSubmissionRef());
                payload.put("submittedAt",
                    saved.getSdaiaSubmittedAt() == null ? null : saved.getSdaiaSubmittedAt().toString());
                eventPublisher.publishEvent(new com.tss.grc.module.platform.automation.event.DomainEvent(
                    saved.getOrganisationId(),
                    "BREACH_REPORTED_TO_SDAIA",
                    "DataBreach",
                    saved.getId(),
                    null,
                    payload));
            }
        } catch (Exception ex) {
            // Event-bus failure must not block the regulator-discharge path.
            log.warn("F8 event publish failed for SDAIA submission on breach {}", id, ex);
        }

        return saved;
    }

    /** Returns the submitted state of the breach for the SDAIA status DTO. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getSdaiaStatus(Long id) {
        DataBreach b = getBreach(id);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("breachId",                 b.getId());
        out.put("discoveredAt",             b.getDiscoveredAt());
        out.put("sdaiaNotificationStatus",  b.getSdaiaNotificationStatus());
        out.put("sdaiaSubmissionRef",       b.getSdaiaSubmissionRef());
        out.put("sdaiaSubmittedAt",         b.getSdaiaSubmittedAt());
        out.put("pdplArticle31DeadlineAt",
            b.getDiscoveredAt() == null ? null : b.getDiscoveredAt().plusHours(72));
        return out;
    }

    // ── Breach workflow (PDPL Article 20 — 72-hour notification) ──────────────

    @Transactional
    public DataBreach containBreach(Long id) {
        DataBreach b = getBreach(id);
        if (!"OPEN".equals(b.getStatus())) {
            throw new IllegalArgumentException("Breach must be OPEN to contain (was " + b.getStatus() + ")");
        }
        b.setStatus("INVESTIGATING");
        DataBreach saved = breachRepo.save(b);
        emitBreach(saved);
        return saved;
    }

    /**
     * Mark the Saudi Data Office as notified for this breach. Records the
     * notification timestamp; the consumer should subsequently attach the
     * submission package as evidence.
     */
    @Transactional
    public DataBreach notifyDataOffice(Long id) {
        DataBreach b = getBreach(id);
        b.setPdpcNotificationRequired(true);
        b.setPdpcNotifiedAt(java.time.LocalDateTime.now());
        DataBreach saved = breachRepo.save(b);
        emitBreach(saved);
        return saved;
    }

    @Transactional
    public DataBreach closeBreach(Long id) {
        DataBreach b = getBreach(id);
        if (!"INVESTIGATING".equals(b.getStatus()) && !"OPEN".equals(b.getStatus())) {
            throw new IllegalArgumentException("Breach must be OPEN or INVESTIGATING to close (was " + b.getStatus() + ")");
        }
        b.setStatus("CLOSED");
        DataBreach saved = breachRepo.save(b);
        emitBreach(saved);
        return saved;
    }

    // ── Data Subject Requests ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DataSubjectRequest> listDsrs(Pageable pageable) {
        return dsrRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public DataSubjectRequest getDsr(Long id) {
        return dsrRepo.findById(id)
            .filter(r -> r.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("DSR not found: " + id));
    }

    @Transactional
    public DataSubjectRequest createDsr(DataSubjectRequest req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        return dsrRepo.save(req);
    }

    @Transactional
    public DataSubjectRequest updateDsr(Long id, DataSubjectRequest req) {
        DataSubjectRequest existing = getDsr(id);
        existing.setRequestType(req.getRequestType());
        existing.setRequesterName(req.getRequesterName());
        existing.setRequesterEmail(req.getRequesterEmail());
        existing.setDueDate(req.getDueDate());
        existing.setStatus(req.getStatus());
        existing.setAssignedTo(req.getAssignedTo());
        existing.setCompletedAt(req.getCompletedAt());
        return dsrRepo.save(existing);
    }

    @Transactional
    public void deleteDsr(Long id) {
        DataSubjectRequest r = getDsr(id);
        r.setIsDeleted(true);
        dsrRepo.save(r);
    }

    // ── DSR workflow (PDPL — 30-day fulfilment SLA) ───────────────────────────

    /**
     * Start fulfilling a DSR. Transitions OPEN → IN_PROGRESS and, if no
     * due_date was set, defaults it to received_at + 30 days per PDPL.
     */
    @Transactional
    public DataSubjectRequest startDsr(Long id) {
        DataSubjectRequest r = getDsr(id);
        if ("FULFILLED".equals(r.getStatus()) || "REJECTED".equals(r.getStatus())) {
            throw new IllegalArgumentException("DSR is already terminal (" + r.getStatus() + ")");
        }
        r.setStatus("IN_PROGRESS");
        if (r.getDueDate() == null) {
            java.time.LocalDateTime base = r.getReceivedAt() != null ? r.getReceivedAt() : java.time.LocalDateTime.now();
            r.setDueDate(base.toLocalDate().plusDays(30));
        }
        DataSubjectRequest saved = dsrRepo.save(r);
        emitDsr(saved);
        return saved;
    }

    @Transactional
    public DataSubjectRequest fulfilDsr(Long id) {
        DataSubjectRequest r = getDsr(id);
        if ("FULFILLED".equals(r.getStatus()) || "REJECTED".equals(r.getStatus())) {
            throw new IllegalArgumentException("DSR is already terminal (" + r.getStatus() + ")");
        }
        r.setStatus("FULFILLED");
        r.setCompletedAt(java.time.LocalDateTime.now());
        DataSubjectRequest saved = dsrRepo.save(r);
        emitDsr(saved);
        return saved;
    }

    /**
     * Extend the DSR due date by N days. PDPL Implementing Regulations allow
     * the controller to extend the response window once, with notification to
     * the data subject. Default extension is 30 days.
     */
    @Transactional
    public DataSubjectRequest extendDsr(Long id, Integer days) {
        DataSubjectRequest r = getDsr(id);
        int n = (days == null || days <= 0) ? 30 : days;
        java.time.LocalDate base = r.getDueDate() != null ? r.getDueDate() : java.time.LocalDate.now();
        r.setDueDate(base.plusDays(n));
        DataSubjectRequest saved = dsrRepo.save(r);
        emitDsr(saved);
        return saved;
    }

    @Transactional
    public DataSubjectRequest rejectDsr(Long id) {
        DataSubjectRequest r = getDsr(id);
        if ("FULFILLED".equals(r.getStatus()) || "REJECTED".equals(r.getStatus())) {
            throw new IllegalArgumentException("DSR is already terminal (" + r.getStatus() + ")");
        }
        r.setStatus("REJECTED");
        r.setCompletedAt(java.time.LocalDateTime.now());
        DataSubjectRequest saved = dsrRepo.save(r);
        emitDsr(saved);
        return saved;
    }

    // ── Processing Activities (ROPA) ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ProcessingActivity> listRopa(Pageable pageable) {
        return ropaRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public ProcessingActivity getRopa(Long id) {
        return ropaRepo.findById(id)
            .filter(a -> a.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Processing activity not found: " + id));
    }

    @Transactional
    public ProcessingActivity createRopa(ProcessingActivity req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        return ropaRepo.save(req);
    }

    @Transactional
    public ProcessingActivity updateRopa(Long id, ProcessingActivity req) {
        ProcessingActivity existing = getRopa(id);
        existing.setActivityNameEn(req.getActivityNameEn());
        existing.setActivityNameAr(req.getActivityNameAr());
        existing.setPurposeEn(req.getPurposeEn());
        existing.setLegalBasis(req.getLegalBasis());
        existing.setRetentionPeriodMonths(req.getRetentionPeriodMonths());
        existing.setCrossBorderTransfer(req.getCrossBorderTransfer());
        existing.setStatus(req.getStatus());
        return ropaRepo.save(existing);
    }

    @Transactional
    public void deleteRopa(Long id) {
        ProcessingActivity a = getRopa(id);
        a.setIsDeleted(true);
        ropaRepo.save(a);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    /**
     * Privacy / PDPL dashboard stats. Returns the full shape the dashboard
     * reads (ropaCount, highRiskRopa, pia*, dsr*, consents*, cross-border,
     * breach*) — earlier this returned only three keys with different names,
     * so every dashboard tile read {@code undefined} and rendered 0. The
     * legacy keys (totalBreaches/openDsrs/totalRopa) are kept for back-compat.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        Long orgId = TenantContext.getCurrentTenant();

        long totalRopa     = ropaRepo.findByOrganisationId(orgId, Pageable.unpaged()).getTotalElements();
        long crossBorder   = ropaRepo.findByOrganisationIdAndCrossBorderTransferTrue(orgId).size();
        long highRiskRopa  = ropaRepo.findByOrganisationIdAndSpecialCategoryFlagTrue(orgId).size();

        long dsrOpen = dsrRepo.countByOrganisationIdAndStatus(orgId, "PENDING")
                     + dsrRepo.countByOrganisationIdAndStatus(orgId, "IN_PROGRESS")
                     + dsrRepo.countByOrganisationIdAndStatus(orgId, "OPEN")
                     + dsrRepo.countByOrganisationIdAndStatus(orgId, "NEW");
        long dsrSla  = dsrRepo.countByOrganisationIdAndStatus(orgId, "ESCALATED");

        long openBreaches = breachRepo.countByOrganisationIdAndStatus(orgId, "OPEN")
                          + breachRepo.countByOrganisationIdAndStatus(orgId, "INVESTIGATING");
        long notified     = breachRepo.countByOrganisationIdAndStatus(orgId, "NOTIFIED");
        long incidents12m = openBreaches + notified
                          + breachRepo.countByOrganisationIdAndStatus(orgId, "INVESTIGATED")
                          + breachRepo.countByOrganisationIdAndStatus(orgId, "ESCALATED")
                          + breachRepo.countByOrganisationIdAndStatus(orgId, "REMEDIATED")
                          + breachRepo.countByOrganisationIdAndStatus(orgId, "CLOSED");

        long piaCompleted = dpiaRepo.countByOrganisationIdAndStatus(orgId, "APPROVED");
        long piaOverdue   = dpiaRepo.countByOrganisationIdAndStatus(orgId, "EXPIRED");

        long activeConsents = consentRepo.countByOrganisationIdAndStatus(orgId, "ACTIVE")
                            + consentRepo.countByOrganisationIdAndStatus(orgId, "GRANTED");
        long withdrawn      = consentRepo.countByOrganisationIdAndStatus(orgId, "WITHDRAWN");

        Map<String, Long> m = new LinkedHashMap<>();
        // Legacy keys (back-compat).
        m.put("totalRopa", totalRopa);
        m.put("openDsrs", dsrOpen);
        m.put("totalBreaches", openBreaches);
        // Dashboard shape.
        m.put("ropaCount", totalRopa);
        m.put("highRiskRopa", highRiskRopa);
        m.put("crossBorderTransfers", crossBorder);
        m.put("piaCompleted", piaCompleted);
        m.put("piaOverdue", piaOverdue);
        m.put("dsrOpen", dsrOpen);
        m.put("dsrSlaBreaches", dsrSla);
        m.put("activeConsents", activeConsents);
        m.put("withdrawnConsents30d", withdrawn);
        m.put("privacyIncidents12m", incidents12m);
        m.put("breachNotifications", notified);
        // Not tracked in this module yet — surfaced as 0 rather than absent.
        m.put("processorsWithoutDpa", 0L);
        m.put("retentionViolations", 0L);
        return m;
    }
}
