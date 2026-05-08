package com.tss.grc.module.privacy.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.entity.DataBreach;
import com.tss.grc.module.privacy.entity.DataSubjectRequest;
import com.tss.grc.module.privacy.entity.ProcessingActivity;
import com.tss.grc.module.privacy.repository.DataBreachRepository;
import com.tss.grc.module.privacy.repository.DataSubjectRequestRepository;
import com.tss.grc.module.privacy.repository.ProcessingActivityRepository;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrivacyService {

    private final DataBreachRepository breachRepo;
    private final DataSubjectRequestRepository dsrRepo;
    private final ProcessingActivityRepository ropaRepo;

    @Autowired(required = false)
    private BroadcastHelper bcast;
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

    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        Long orgId = TenantContext.getCurrentTenant();
        return Map.of(
            "totalBreaches",    breachRepo.countByOrganisationIdAndStatus(orgId, "OPEN") + breachRepo.countByOrganisationIdAndStatus(orgId, "INVESTIGATING"),
            "openDsrs",         dsrRepo.countByOrganisationIdAndStatus(orgId, "PENDING")
                                  + dsrRepo.countByOrganisationIdAndStatus(orgId, "IN_PROGRESS")
                                  + dsrRepo.countByOrganisationIdAndStatus(orgId, "OPEN"),
            "totalRopa",        ropaRepo.findByOrganisationId(orgId, Pageable.unpaged()).getTotalElements()
        );
    }
}
