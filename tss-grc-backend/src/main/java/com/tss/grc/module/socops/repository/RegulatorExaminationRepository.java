package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.RegulatorExamination;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegulatorExaminationRepository extends JpaRepository<RegulatorExamination, Long> {

    Optional<RegulatorExamination> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<RegulatorExamination> findByOrganisationIdAndExaminationCode(Long organisationId, String examinationCode);

    Page<RegulatorExamination> findByOrganisationIdOrderByNotifiedAtDesc(Long organisationId, Pageable pageable);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndRegulator(Long organisationId, String regulator);
}
