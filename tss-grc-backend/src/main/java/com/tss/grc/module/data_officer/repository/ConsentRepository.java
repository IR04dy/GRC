package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("doConsentRepository")
public interface ConsentRepository extends JpaRepository<ConsentRecord, Long> {
    Optional<ConsentRecord> findByOrganisationIdAndReference(Long organisationId, String reference);
    Optional<ConsentRecord> findByOrganisationIdAndId(Long organisationId, Long id);
    List<ConsentRecord> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    List<ConsentRecord> findByOrganisationIdAndDataSubjectIdOrderByGrantedAtDesc(Long organisationId, String dataSubjectId);
}
