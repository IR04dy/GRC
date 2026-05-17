package com.tss.grc.module.platform.controls.repository;

import com.tss.grc.module.platform.controls.entity.ControlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ControlMappingRepository extends JpaRepository<ControlMapping, Long> {

    List<ControlMapping> findByOrganisationIdAndControlIdOrderByIdAsc(Long organisationId, Long controlId);

    List<ControlMapping> findByOrganisationIdAndCitationIdOrderByIdAsc(Long organisationId, Long citationId);

    Optional<ControlMapping> findByControlIdAndCitationId(Long controlId, Long citationId);

    Optional<ControlMapping> findByIdAndOrganisationId(Long id, Long organisationId);

    /**
     * For the Compliance Posture Hub (F6): list distinct authority
     * document ids that any of the org's controls are mapped to.
     */
    @Query("""
        SELECT DISTINCT c.authorityDocumentId
        FROM ControlMapping m
        JOIN com.tss.grc.module.platform.regulatory.entity.AuthorityDocumentCitation c
          ON c.id = m.citationId
        WHERE m.organisationId = :orgId
        """)
    List<Long> distinctMappedAuthorityDocumentIds(Long orgId);
}
