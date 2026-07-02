package com.tss.grc.module.evidence.repository;

import com.tss.grc.module.evidence.entity.EvidenceLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceLinkRepository extends JpaRepository<EvidenceLink, Long> {

    List<EvidenceLink> findByEvidenceIdAndOrganisationIdAndIsDeletedFalse(Long evidenceId, Long orgId);

    List<EvidenceLink> findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(
            Long orgId, String linkedModule, Long linkedEntityId);

    /** All live links for a module — drives the sustainability evidence-package bundle (S-C). */
    List<EvidenceLink> findByOrganisationIdAndLinkedModuleAndIsDeletedFalse(
            Long orgId, String linkedModule);
}
