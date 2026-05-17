package com.tss.grc.module.ndi.framework.repository;

import com.tss.grc.module.ndi.framework.entity.NdiFrameworkItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NdiFrameworkItemRepository extends JpaRepository<NdiFrameworkItem, Long> {

    Page<NdiFrameworkItem> findByOrganisationIdOrderByComponentAscDomainAscRequirementCodeAsc(Long org, Pageable p);

    Page<NdiFrameworkItem> findByOrganisationIdAndComponentOrderByDomainAscRequirementCodeAsc(
        Long org, String component, Pageable p);

    Page<NdiFrameworkItem> findByOrganisationIdAndDomainOrderByComponentAscRequirementCodeAsc(
        Long org, String domain, Pageable p);

    Optional<NdiFrameworkItem> findByIdAndOrganisationId(Long id, Long org);

    Optional<NdiFrameworkItem> findByOrganisationIdAndFrameworkVersionAndRequirementCode(
        Long org, String frameworkVersion, String requirementCode);

    List<NdiFrameworkItem> findByOrganisationId(Long org);
}
