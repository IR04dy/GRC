package com.tss.grc.module.privacy.repository;

import com.tss.grc.module.privacy.entity.ConsentRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    Page<ConsentRecord> findByOrganisationIdOrderByIdDesc(Long organisationId, Pageable pageable);

    Optional<ConsentRecord> findByIdAndOrganisationId(Long id, Long organisationId);

    List<ConsentRecord> findByOrganisationIdAndDataSubjectRefOrderByIdDesc(
        Long organisationId, String dataSubjectRef);

    List<ConsentRecord> findByOrganisationIdAndPurposeCodeAndStatusOrderByIdDesc(
        Long organisationId, String purposeCode, String status);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
