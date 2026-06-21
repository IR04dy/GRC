package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.RegulatorExamination;
import com.tss.grc.module.socops.repository.RegulatorExaminationRepository;
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
public class RegulatorExaminationService {

    private final RegulatorExaminationRepository repo;
    private final AuditLogService                audit;
    private final ApplicationEventPublisher      publisher;

    @Transactional(readOnly = true)
    public Page<RegulatorExamination> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByNotifiedAtDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public RegulatorExamination get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("RegulatorExamination " + id));
    }

    @Transactional
    public RegulatorExamination save(RegulatorExamination in) {
        // SEC-CYB-002 — on create force id=null so a client-supplied id can't
        // overwrite an existing row via merge; on update load the row scoped
        // to the caller's tenant and copy only mutable fields.
        Long org = TenantContext.getCurrentTenant();
        boolean isNew = in.getId() == null;
        RegulatorExamination target;
        if (isNew) {
            in.setId(null);
            in.setOrganisationId(org);
            target = in;
        } else {
            RegulatorExamination existing = repo.findByIdAndOrganisationId(in.getId(), org)
                .orElseThrow(() -> new EntityNotFoundException("RegulatorExamination " + in.getId()));
            copyMutable(in, existing);
            target = existing;
        }
        RegulatorExamination saved = repo.save(target);
        audit.log(isNew ? "REGULATOR_EXAM_CREATE" : "REGULATOR_EXAM_UPDATE",
            "RegulatorExamination", saved.getId(),
            saved.getRegulator() + " · " + saved.getExaminationCode());
        // F8 — AR-128 (response coordinator notify) consumes this on creation.
        // SAMA / NCA / SDAIA / CITC / CMA / ZATCA all map to the same trigger
        // and the rule's condition_json can scope further if a regulator wants
        // its own escalation chain.
        if (isNew) {
            publisher.publishEvent(DomainEvent.of(
                saved.getOrganisationId(), "REGULATOR_EXAM_NOTIFIED",
                "RegulatorExamination", saved.getId(),
                Map.of("regulator",       saved.getRegulator()       == null ? "" : saved.getRegulator(),
                       "examinationCode", saved.getExaminationCode() == null ? "" : saved.getExaminationCode(),
                       "examinationType", saved.getExaminationType() == null ? "" : saved.getExaminationType())));
        }
        return saved;
    }

    /** Copy client-mutable fields onto the tenant-owned managed entity. id,
     *  organisationId and audit columns are deliberately never copied. */
    private void copyMutable(RegulatorExamination in, RegulatorExamination target) {
        target.setExaminationCode(in.getExaminationCode());
        target.setRegulator(in.getRegulator());
        target.setExaminationType(in.getExaminationType());
        target.setTitleEn(in.getTitleEn());
        target.setTitleAr(in.getTitleAr());
        target.setScopeAreas(in.getScopeAreas());
        target.setLeadExaminerName(in.getLeadExaminerName());
        target.setNotifiedAt(in.getNotifiedAt());
        target.setFieldworkStartAt(in.getFieldworkStartAt());
        target.setFieldworkEndAt(in.getFieldworkEndAt());
        target.setDraftReportAt(in.getDraftReportAt());
        target.setFinalReportAt(in.getFinalReportAt());
        target.setResponseDueAt(in.getResponseDueAt());
        target.setResponseSubmittedAt(in.getResponseSubmittedAt());
        target.setClosedAt(in.getClosedAt());
        target.setStatus(in.getStatus());
        target.setFindingsTotal(in.getFindingsTotal());
        target.setFindingsCritical(in.getFindingsCritical());
        target.setFindingsHigh(in.getFindingsHigh());
        target.setFindingsMedLow(in.getFindingsMedLow());
        target.setOverallRating(in.getOverallRating());
        target.setRelatedFramework(in.getRelatedFramework());
        target.setNotes(in.getNotes());
    }

    @Transactional
    public void delete(Long id) {
        RegulatorExamination e = get(id);
        e.softDelete(null);
        repo.save(e);
        audit.log("REGULATOR_EXAM_DELETE", "RegulatorExamination", id, "Soft-deleted");
    }
}
