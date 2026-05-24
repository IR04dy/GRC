package com.tss.grc.module.ai.governance.repository;

import com.tss.grc.module.ai.governance.entity.MlModelAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MlModelAuditRepository extends JpaRepository<MlModelAudit, Long> {

    List<MlModelAudit> findByOrganisationIdAndModelIdAndIsDeletedFalseOrderByAuditDateDesc(Long organisationId, Long modelId);

    Optional<MlModelAudit> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);

    Optional<MlModelAudit> findTopByOrganisationIdAndModelIdAndAuditTypeAndIsDeletedFalseOrderByAuditDateDesc(
            Long organisationId, Long modelId, String auditType);

    /** Used by the V183 unique-index defence (model_id, audit_type, audit_date). */
    Optional<MlModelAudit> findByOrganisationIdAndModelIdAndAuditTypeAndAuditDateAndIsDeletedFalse(
            Long organisationId, Long modelId, String auditType, java.time.LocalDate auditDate);
}
