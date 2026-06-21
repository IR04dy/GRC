package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.AwarenessCampaign;
import com.tss.grc.module.socops.repository.AwarenessCampaignRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AwarenessCampaignService {

    private final AwarenessCampaignRepository repo;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public Page<AwarenessCampaign> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByStartedAtDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public AwarenessCampaign get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("AwarenessCampaign " + id));
    }

    @Transactional
    public AwarenessCampaign save(AwarenessCampaign in) {
        // SEC-CYB-002 — on create force id=null so a client-supplied id can't
        // overwrite an existing row via merge; on update load the row scoped
        // to the caller's tenant and copy only mutable fields.
        Long org = TenantContext.getCurrentTenant();
        boolean isNew = in.getId() == null;
        AwarenessCampaign target;
        if (isNew) {
            in.setId(null);
            in.setOrganisationId(org);
            target = in;
        } else {
            AwarenessCampaign existing = repo.findByIdAndOrganisationId(in.getId(), org)
                .orElseThrow(() -> new EntityNotFoundException("AwarenessCampaign " + in.getId()));
            copyMutable(in, existing);
            target = existing;
        }
        AwarenessCampaign saved = repo.save(target);
        audit.log(isNew ? "AWARENESS_CREATE" : "AWARENESS_UPDATE",
            "AwarenessCampaign", saved.getId(), saved.getCampaignCode());
        return saved;
    }

    /** Copy client-mutable fields onto the tenant-owned managed entity. id,
     *  organisationId and audit columns are deliberately never copied. */
    private void copyMutable(AwarenessCampaign in, AwarenessCampaign target) {
        target.setCampaignCode(in.getCampaignCode());
        target.setTitleEn(in.getTitleEn());
        target.setTitleAr(in.getTitleAr());
        target.setCampaignType(in.getCampaignType());
        target.setTargetAudience(in.getTargetAudience());
        target.setTargetUserCount(in.getTargetUserCount());
        target.setStartedAt(in.getStartedAt());
        target.setEndedAt(in.getEndedAt());
        target.setStatus(in.getStatus());
        target.setDeliveredCount(in.getDeliveredCount());
        target.setOpenedCount(in.getOpenedCount());
        target.setClickedCount(in.getClickedCount());
        target.setReportedCount(in.getReportedCount());
        target.setCompletedCount(in.getCompletedCount());
        target.setRepeatOffenderCount(in.getRepeatOffenderCount());
        target.setPassRatePercent(in.getPassRatePercent());
        target.setAlignedControls(in.getAlignedControls());
        target.setNotes(in.getNotes());
    }

    @Transactional
    public void delete(Long id) {
        AwarenessCampaign c = get(id);
        c.softDelete(null);
        repo.save(c);
        audit.log("AWARENESS_DELETE", "AwarenessCampaign", id, "Soft-deleted");
    }
}
