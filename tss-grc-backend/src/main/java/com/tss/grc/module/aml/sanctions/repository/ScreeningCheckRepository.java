package com.tss.grc.module.aml.sanctions.repository;

import com.tss.grc.module.aml.sanctions.entity.ScreeningCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Tenant-scoped repository for {@link ScreeningCheck}. */
public interface ScreeningCheckRepository extends JpaRepository<ScreeningCheck, Long> {

    List<ScreeningCheck> findByOrganisationIdAndIsDeletedFalseOrderByCheckAtDesc(Long organisationId);

    List<ScreeningCheck> findTop100ByOrganisationIdAndIsDeletedFalseOrderByCheckAtDesc(Long organisationId);

    List<ScreeningCheck> findByOrganisationIdAndResultAndIsDeletedFalseOrderByCheckAtDesc(
        Long organisationId, String result);

    Optional<ScreeningCheck> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    Optional<ScreeningCheck> findByOrganisationIdAndCheckCodeAndIsDeletedFalse(
        Long organisationId, String checkCode);

    boolean existsByOrganisationIdAndCheckCodeAndIsDeletedFalse(Long organisationId, String checkCode);

    long countByOrganisationIdAndCheckAtAfterAndIsDeletedFalse(Long organisationId, LocalDateTime since);

    long countByOrganisationIdAndIsDeletedFalse(Long organisationId);
}
