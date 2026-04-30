package com.tss.grc.module.bcm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.entity.BcmPlan;
import com.tss.grc.module.bcm.entity.BiaRecord;
import com.tss.grc.module.bcm.entity.CriticalProcess;
import com.tss.grc.module.bcm.repository.BcmPlanRepository;
import com.tss.grc.module.bcm.repository.BiaRecordRepository;
import com.tss.grc.module.bcm.repository.CriticalProcessRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BcmService {

    private final BcmPlanRepository planRepo;
    private final CriticalProcessRepository processRepo;
    private final BiaRecordRepository biaRepo;

    // ── BCM Plans ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<BcmPlan> listPlans(Pageable pageable) {
        return planRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public BcmPlan getPlan(Long id) {
        return planRepo.findById(id)
            .filter(p -> p.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("BCM Plan not found: " + id));
    }

    @Transactional
    public BcmPlan createPlan(BcmPlan req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        return planRepo.save(req);
    }

    @Transactional
    public BcmPlan updatePlan(Long id, BcmPlan req) {
        BcmPlan existing = getPlan(id);
        existing.setPlanNameEn(req.getPlanNameEn());
        existing.setPlanNameAr(req.getPlanNameAr());
        existing.setPlanType(req.getPlanType());
        existing.setVersion(req.getVersion());
        existing.setStatus(req.getStatus());
        existing.setNextReviewDate(req.getNextReviewDate());
        return planRepo.save(existing);
    }

    @Transactional
    public void deletePlan(Long id) {
        BcmPlan p = getPlan(id);
        p.setIsDeleted(true);
        planRepo.save(p);
    }

    // ── Critical Processes ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CriticalProcess> listProcesses(Pageable pageable) {
        return processRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public CriticalProcess getProcess(Long id) {
        return processRepo.findById(id)
            .filter(p -> p.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Critical process not found: " + id));
    }

    @Transactional
    public CriticalProcess createProcess(CriticalProcess req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        return processRepo.save(req);
    }

    @Transactional
    public CriticalProcess updateProcess(Long id, CriticalProcess req) {
        CriticalProcess existing = getProcess(id);
        existing.setProcessNameEn(req.getProcessNameEn());
        existing.setProcessNameAr(req.getProcessNameAr());
        existing.setOwnerId(req.getOwnerId());
        existing.setCriticality(req.getCriticality());
        return processRepo.save(existing);
    }

    @Transactional
    public void deleteProcess(Long id) {
        CriticalProcess p = getProcess(id);
        p.setIsDeleted(true);
        processRepo.save(p);
    }

    // ── BIA Records ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BiaRecord> listBia(Long processId) {
        if (processId != null) return biaRepo.findByProcessId(processId, Pageable.unpaged()).getContent();
        // Return all BIA records for this org's processes
        List<Long> processIds = processRepo.findByOrganisationId(TenantContext.getCurrentTenant(), Pageable.unpaged())
            .map(CriticalProcess::getId).getContent();
        return biaRepo.findByProcessIdIn(processIds);
    }

    @Transactional
    public BiaRecord createBia(BiaRecord req) {
        return biaRepo.save(req);
    }

    @Transactional
    public BiaRecord updateBia(Long id, BiaRecord req) {
        BiaRecord existing = biaRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("BIA record not found: " + id));
        existing.setProcessId(req.getProcessId());
        existing.setAssessmentDate(req.getAssessmentDate());
        existing.setRtoHours(req.getRtoHours());
        existing.setRpoHours(req.getRpoHours());
        existing.setMtpdHours(req.getMtpdHours());
        existing.setOverallImpact(req.getOverallImpact());
        existing.setStatus(req.getStatus());
        return biaRepo.save(existing);
    }

    @Transactional
    public void deleteBia(Long id) {
        biaRepo.deleteById(id);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        Long orgId = TenantContext.getCurrentTenant();
        long totalPlans = planRepo.findByOrganisationId(orgId, Pageable.unpaged()).getTotalElements();
        long totalProcesses = processRepo.findByOrganisationId(orgId, Pageable.unpaged()).getTotalElements();
        List<Long> processIds = processRepo.findByOrganisationId(orgId, Pageable.unpaged())
            .map(CriticalProcess::getId).getContent();
        long totalBia = biaRepo.findByProcessIdIn(processIds).size();
        return Map.of("totalPlans", totalPlans, "totalProcesses", totalProcesses, "totalBia", totalBia);
    }
}
