package com.tss.grc.module.openbanking.repository;

import com.tss.grc.module.openbanking.entity.ObApiCall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ObApiCallRepository extends JpaRepository<ObApiCall, Long> {

    Page<ObApiCall> findByOrganisationIdOrderByOccurredAtDesc(
            Long organisationId, Pageable pageable);

    Optional<ObApiCall> findByIdAndOrganisationId(Long id, Long organisationId);

    List<ObApiCall> findByTppIdAndOrganisationIdOrderByOccurredAtDesc(
            Long tppId, Long organisationId);

    long countByOrganisationIdAndOccurredAtAfter(Long organisationId, LocalDateTime since);

    long countByOrganisationIdAndStatusCodeGreaterThanEqualAndOccurredAtAfter(
            Long organisationId, Integer minStatus, LocalDateTime since);
}
