package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.Breach;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository("doBreachRepository")
public interface BreachRepository extends JpaRepository<Breach, Long> {
    Optional<Breach> findByOrganisationIdAndReference(Long organisationId, String reference);
    Optional<Breach> findByOrganisationIdAndId(Long organisationId, Long id);
    List<Breach> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    @Query("SELECT b FROM DoBreach b WHERE b.organisationId = :o AND b.sdaiaNotifiedAt IS NULL AND b.sdaiaDeadline < :now AND b.status NOT IN ('CLOSED','FALSE_POSITIVE')")
    List<Breach> findOverdueSdaia(Long o, LocalDateTime now);
}
