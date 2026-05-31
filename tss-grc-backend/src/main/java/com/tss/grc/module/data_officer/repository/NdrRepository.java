package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.NdrSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("doNdrRepository")
public interface NdrRepository extends JpaRepository<NdrSubmission, Long> {
    Optional<NdrSubmission> findByOrganisationIdAndReference(Long organisationId, String reference);
    Optional<NdrSubmission> findByOrganisationIdAndId(Long organisationId, Long id);
    List<NdrSubmission> findByOrganisationIdOrderByCycleYearDesc(Long organisationId);
    Optional<NdrSubmission> findByOrganisationIdAndCycleYear(Long organisationId, Integer cycleYear);
}
