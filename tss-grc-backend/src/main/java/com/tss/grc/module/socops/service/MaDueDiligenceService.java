package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.MaDueDiligence;
import com.tss.grc.module.socops.repository.MaDueDiligenceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MaDueDiligenceService {

    private final MaDueDiligenceRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    @Transactional(readOnly = true)
    public Page<MaDueDiligence> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByCreatedAtDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public MaDueDiligence get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("MaDueDiligence " + id));
    }

    @Transactional
    public MaDueDiligence save(MaDueDiligence in) {
        // SEC-CYB-002 — on create force id=null so a client-supplied id can't
        // overwrite an existing row via merge; on update load the row scoped
        // to the caller's tenant and copy only mutable fields.
        Long org = TenantContext.getCurrentTenant();
        boolean isNew = in.getId() == null;
        MaDueDiligence target;
        if (isNew) {
            in.setId(null);
            in.setOrganisationId(org);
            target = in;
        } else {
            MaDueDiligence existing = repo.findByIdAndOrganisationId(in.getId(), org)
                .orElseThrow(() -> new EntityNotFoundException("MaDueDiligence " + in.getId()));
            copyMutable(in, existing);
            target = existing;
        }
        MaDueDiligence saved = repo.save(target);
        audit.log(isNew ? "MA_DD_CREATE" : "MA_DD_UPDATE",
            "MaDueDiligence", saved.getId(),
            saved.getEngagementCode() + " · " + saved.getTargetEntityName()
                + " · " + saved.getPhase());
        return saved;
    }

    /** Copy client-mutable fields onto the tenant-owned managed entity. id,
     *  organisationId and audit columns are deliberately never copied. */
    private void copyMutable(MaDueDiligence in, MaDueDiligence target) {
        target.setEngagementCode(in.getEngagementCode());
        target.setTitleEn(in.getTitleEn());
        target.setTitleAr(in.getTitleAr());
        target.setTargetEntityName(in.getTargetEntityName());
        target.setTargetIndustry(in.getTargetIndustry());
        target.setTargetCountry(in.getTargetCountry());
        target.setDealType(in.getDealType());
        target.setPhase(in.getPhase());
        target.setDealStatus(in.getDealStatus());
        target.setRiskAreasAssessed(in.getRiskAreasAssessed());
        target.setFindingsTotal(in.getFindingsTotal());
        target.setFindingsCritical(in.getFindingsCritical());
        target.setFindingsHigh(in.getFindingsHigh());
        target.setPriceAdjustmentSar(in.getPriceAdjustmentSar());
        target.setCyberLeadName(in.getCyberLeadName());
        target.setLegalLeadName(in.getLegalLeadName());
        target.setFinanceLeadName(in.getFinanceLeadName());
        target.setLoiSignedAt(in.getLoiSignedAt());
        target.setFieldworkStartAt(in.getFieldworkStartAt());
        target.setFieldworkEndAt(in.getFieldworkEndAt());
        target.setFinalReportAt(in.getFinalReportAt());
        target.setDealCloseAt(in.getDealCloseAt());
        target.setIntegrationCompleteAt(in.getIntegrationCompleteAt());
        target.setCyberRecommendation(in.getCyberRecommendation());
        target.setRecommendationNotes(in.getRecommendationNotes());
        target.setNotes(in.getNotes());
    }

    @Transactional
    public MaDueDiligence transitionPhase(Long id, String newPhase) {
        MaDueDiligence dd = get(id);
        String old = dd.getPhase();
        dd.setPhase(newPhase);
        MaDueDiligence saved = repo.save(dd);
        audit.log("MA_DD_TRANSITION", "MaDueDiligence", id,
            "Phase " + old + " → " + newPhase);
        // F8 — AR-142. PIF / sovereign-fund deal phase transitions trigger
        // legal + integration-readiness owner notifications. The rule's
        // condition_json filters on (pif_aligned == true) so non-PIF deals
        // still emit the event but don't escalate.
        publisher.publishEvent(DomainEvent.of(
            saved.getOrganisationId(), "MA_DD_PHASE_TRANSITION",
            "MaDueDiligence", id,
            Map.of("engagementCode", saved.getEngagementCode()   == null ? "" : saved.getEngagementCode(),
                   "targetEntity",   saved.getTargetEntityName() == null ? "" : saved.getTargetEntityName(),
                   "dealType",       saved.getDealType()         == null ? "" : saved.getDealType(),
                   "oldPhase",       old                         == null ? "" : old,
                   "newPhase",       newPhase                    == null ? "" : newPhase)));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        MaDueDiligence dd = get(id);
        dd.softDelete(null);
        repo.save(dd);
        audit.log("MA_DD_DELETE", "MaDueDiligence", id, "Soft-deleted");
    }
}
