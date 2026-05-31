package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.Dpia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("doDpiaRepository")
public interface DpiaRepository extends JpaRepository<Dpia, Long> {
    Optional<Dpia> findByOrganisationIdAndReference(Long organisationId, String reference);
    Optional<Dpia> findByOrganisationIdAndId(Long organisationId, Long id);
    List<Dpia> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
