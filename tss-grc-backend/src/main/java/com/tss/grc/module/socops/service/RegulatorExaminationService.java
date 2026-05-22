package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.socops.entity.RegulatorExamination;
import com.tss.grc.module.socops.repository.RegulatorExaminationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegulatorExaminationService {

    private final RegulatorExaminationRepository repo;
    private final AuditLogService audit;

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
        in.setOrganisationId(TenantContext.getCurrentTenant());
        boolean isNew = in.getId() == null;
        RegulatorExamination saved = repo.save(in);
        audit.log(isNew ? "REGULATOR_EXAM_CREATE" : "REGULATOR_EXAM_UPDATE",
            "RegulatorExamination", saved.getId(),
            saved.getRegulator() + " · " + saved.getExaminationCode());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        RegulatorExamination e = get(id);
        e.softDelete(null);
        repo.save(e);
        audit.log("REGULATOR_EXAM_DELETE", "RegulatorExamination", id, "Soft-deleted");
    }
}
