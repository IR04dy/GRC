package com.tss.grc.module.sharia.repository;

import com.tss.grc.module.sharia.entity.ShariaScholar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShariaScholarRepository extends JpaRepository<ShariaScholar, Long> {

    Page<ShariaScholar> findByOrganisationIdOrderByReferenceAsc(
            Long organisationId, Pageable pageable);

    Optional<ShariaScholar> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<ShariaScholar> findByReferenceAndOrganisationId(
            String reference, Long organisationId);

    List<ShariaScholar> findByOrganisationIdAndStatus(Long organisationId, String status);

    List<ShariaScholar> findByOrganisationIdAndTermEndBefore(
            Long organisationId, LocalDate cutoff);

    long countByOrganisationId(Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
