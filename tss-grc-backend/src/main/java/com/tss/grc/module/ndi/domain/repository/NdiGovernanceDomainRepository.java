package com.tss.grc.module.ndi.domain.repository;

import com.tss.grc.module.ndi.domain.entity.NdiGovernanceDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NdiGovernanceDomainRepository extends JpaRepository<NdiGovernanceDomain, Long> {

    List<NdiGovernanceDomain> findAllByOrganisationIdOrderBySortOrderAscIdAsc(Long organisationId);

    List<NdiGovernanceDomain> findAllByOrganisationIdAndIsActiveOrderBySortOrderAscIdAsc(Long organisationId, Boolean isActive);

    Optional<NdiGovernanceDomain> findByOrganisationIdAndCode(Long organisationId, String code);

    @Query("""
        select d from NdiGovernanceDomain d
        where d.organisationId = :orgId
          and d.isActive = true
        order by d.sortOrder asc, d.id asc
    """)
    List<NdiGovernanceDomain> activeForOrg(Long orgId);
}
