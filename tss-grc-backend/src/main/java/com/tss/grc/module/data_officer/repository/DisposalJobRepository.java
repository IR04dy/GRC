package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.DisposalJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("doDisposalJobRepository")
public interface DisposalJobRepository extends JpaRepository<DisposalJob, Long> {
    Optional<DisposalJob> findByOrganisationIdAndReference(Long organisationId, String reference);
    Optional<DisposalJob> findByOrganisationIdAndId(Long organisationId, Long id);
    List<DisposalJob> findByOrganisationIdOrderByScheduledForDesc(Long organisationId);
    List<DisposalJob> findByOrganisationIdAndStatusOrderByScheduledForAsc(Long organisationId, String status);
}
