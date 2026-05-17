package com.tss.grc.module.ndi.submission.repository;

import com.tss.grc.module.ndi.submission.entity.NdiSubmissionPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NdiSubmissionPackageRepository extends JpaRepository<NdiSubmissionPackage, Long> {

    List<NdiSubmissionPackage> findByOrganisationIdAndCycleIdOrderByVersionDesc(Long org, Long cycleId);

    Optional<NdiSubmissionPackage> findByIdAndOrganisationId(Long id, Long org);

    Optional<NdiSubmissionPackage> findFirstByOrganisationIdAndCycleIdOrderByVersionDesc(Long org, Long cycleId);

    List<NdiSubmissionPackage> findByOrganisationIdOrderByCreatedAtDesc(Long org);
}
