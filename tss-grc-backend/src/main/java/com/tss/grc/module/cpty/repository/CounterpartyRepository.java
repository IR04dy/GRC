package com.tss.grc.module.cpty.repository;

import com.tss.grc.module.cpty.entity.Counterparty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

    Page<Counterparty> findByOrganisationIdOrderByReferenceAsc(
            Long organisationId, Pageable pageable);

    Optional<Counterparty> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<Counterparty> findByReferenceAndOrganisationId(
            String reference, Long organisationId);

    List<Counterparty> findByOrganisationIdAndStatus(Long organisationId, String status);

    List<Counterparty> findByOrganisationIdAndParentGroupReference(
            Long organisationId, String parentGroupReference);

    long countByOrganisationId(Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
