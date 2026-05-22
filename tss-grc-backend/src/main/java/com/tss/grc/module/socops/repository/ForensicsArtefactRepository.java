package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.ForensicsArtefact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ForensicsArtefactRepository extends JpaRepository<ForensicsArtefact, Long> {
    Optional<ForensicsArtefact> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<ForensicsArtefact> findByOrganisationIdAndArtefactCode(Long organisationId, String artefactCode);
    Page<ForensicsArtefact> findByOrganisationIdOrderByCapturedAtDesc(Long organisationId, Pageable pageable);
    List<ForensicsArtefact> findByOrganisationIdAndCaseCode(Long organisationId, String caseCode);
    long countByOrganisationIdAndLegalHold(Long organisationId, Boolean legalHold);
    long countByOrganisationIdAndIntegrityStatus(Long organisationId, String integrityStatus);
}
