package com.tss.grc.module.bcm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.entity.BcmExercise;
import com.tss.grc.module.bcm.entity.BcmPlan;
import com.tss.grc.module.bcm.entity.BiaRecord;
import com.tss.grc.module.bcm.entity.CriticalProcess;
import com.tss.grc.module.bcm.repository.BcmExerciseRepository;
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
    private final BcmExerciseRepository exerciseRepo;

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

    // ── Plan workflow actions (ISO 22301 lifecycle) ───────────────────────────
    //
    // Status transitions:
    //   DRAFT → UNDER_REVIEW → APPROVED → INVOKED → APPROVED (after restoration)
    // Each transition is enforced; calling out-of-order transitions throws.

    @Transactional
    public BcmPlan submitForReview(Long id) {
        BcmPlan p = getPlan(id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new IllegalArgumentException("Plan must be DRAFT to submit for review (was " + p.getStatus() + ")");
        }
        p.setStatus("UNDER_REVIEW");
        return planRepo.save(p);
    }

    @Transactional
    public BcmPlan approvePlan(Long id) {
        BcmPlan p = getPlan(id);
        if (!"DRAFT".equals(p.getStatus()) && !"UNDER_REVIEW".equals(p.getStatus())) {
            throw new IllegalArgumentException("Plan must be DRAFT or UNDER_REVIEW to approve (was " + p.getStatus() + ")");
        }
        p.setStatus("APPROVED");
        return planRepo.save(p);
    }

    @Transactional
    public BcmPlan invokePlan(Long id) {
        BcmPlan p = getPlan(id);
        if (!"APPROVED".equals(p.getStatus())) {
            throw new IllegalArgumentException("Plan must be APPROVED to invoke (was " + p.getStatus() + ")");
        }
        p.setStatus("INVOKED");
        return planRepo.save(p);
    }

    @Transactional
    public BcmPlan restorePlan(Long id) {
        BcmPlan p = getPlan(id);
        if (!"INVOKED".equals(p.getStatus())) {
            throw new IllegalArgumentException("Plan must be INVOKED to restore (was " + p.getStatus() + ")");
        }
        p.setStatus("APPROVED");
        return planRepo.save(p);
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

    // ── Exercises (ISO 22301 §8.5 — exercise programme) ───────────────────────

    @Transactional(readOnly = true)
    public Page<BcmExercise> listExercises(Pageable pageable) {
        return exerciseRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public BcmExercise getExercise(Long id) {
        return exerciseRepo.findById(id)
            .filter(e -> e.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Exercise not found: " + id));
    }

    @Transactional
    public BcmExercise createExercise(BcmExercise req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        return exerciseRepo.save(req);
    }

    @Transactional
    public BcmExercise updateExercise(Long id, BcmExercise req) {
        BcmExercise e = getExercise(id);
        e.setPlanId(req.getPlanId());
        e.setExerciseNameEn(req.getExerciseNameEn());
        e.setExerciseNameAr(req.getExerciseNameAr());
        e.setExerciseType(req.getExerciseType());
        e.setScenario(req.getScenario());
        e.setScheduledDate(req.getScheduledDate());
        e.setExecutedDate(req.getExecutedDate());
        e.setScore(req.getScore());
        e.setStatus(req.getStatus());
        e.setFindings(req.getFindings());
        e.setCapaCount(req.getCapaCount() != null ? req.getCapaCount() : 0);
        e.setFacilitator(req.getFacilitator());
        e.setUpdatedAt(java.time.LocalDateTime.now());
        return exerciseRepo.save(e);
    }

    @Transactional
    public void deleteExercise(Long id) {
        BcmExercise e = getExercise(id);
        e.setIsDeleted(true);
        e.setUpdatedAt(java.time.LocalDateTime.now());
        exerciseRepo.save(e);
    }

    /** Lifecycle helper: mark an exercise complete and persist its score & findings. */
    @Transactional
    public BcmExercise completeExercise(Long id, Integer score, String findings) {
        BcmExercise e = getExercise(id);
        e.setStatus("COMPLETED");
        e.setExecutedDate(java.time.LocalDate.now());
        if (score != null) e.setScore(score);
        if (findings != null) e.setFindings(findings);
        e.setUpdatedAt(java.time.LocalDateTime.now());
        return exerciseRepo.save(e);
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
        long totalExercises = exerciseRepo.findByOrganisationId(orgId, Pageable.unpaged()).getTotalElements();
        return Map.of(
            "totalPlans",     totalPlans,
            "totalProcesses", totalProcesses,
            "totalBia",       totalBia,
            "totalExercises", totalExercises
        );
    }
}
