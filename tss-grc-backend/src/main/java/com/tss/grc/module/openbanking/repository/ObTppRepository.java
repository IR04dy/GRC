package com.tss.grc.module.openbanking.repository;

import com.tss.grc.module.openbanking.entity.ObTpp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ObTppRepository extends JpaRepository<ObTpp, Long> {

    Page<ObTpp> findByOrganisationIdOrderByReferenceAsc(
            Long organisationId, Pageable pageable);

    Optional<ObTpp> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<ObTpp> findByReferenceAndOrganisationId(
            String reference, Long organisationId);

    List<ObTpp> findByOrganisationIdAndStatus(Long organisationId, String status);

    List<ObTpp> findByOrganisationIdAndCertExpiresAtBefore(
            Long organisationId, LocalDate cutoff);

    long countByOrganisationId(Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
