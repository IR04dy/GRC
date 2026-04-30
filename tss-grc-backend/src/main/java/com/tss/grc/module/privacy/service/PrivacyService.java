package com.tss.grc.module.privacy.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.entity.DataBreach;
import com.tss.grc.module.privacy.entity.DataSubjectRequest;
import com.tss.grc.module.privacy.entity.ProcessingActivity;
import com.tss.grc.module.privacy.repository.DataBreachRepository;
import com.tss.grc.module.privacy.repository.DataSubjectRequestRepository;
import com.tss.grc.module.privacy.repository.ProcessingActivityRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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
