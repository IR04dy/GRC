package com.tss.grc.module.ai.lifecycle.repository;

import com.tss.grc.module.ai.lifecycle.entity.Recertification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecertificationRepository extends JpaRepository<Recertification, Long> {
    List<Recertification> findByOrganisationIdAndModelIdOrderByCreatedAtDesc(Long org, Long modelId);
    List<Recertification> findByOrganisationIdOrderByCreatedAtDesc(Long org);
    Optional<Recertification> findByOrganisationIdAndId(Long org, Long id);
    Optional<Recertification> findByOrganisationIdAndReference(Long org, String reference);
    long countByOrganisationIdAndStatus(Long org, String status);

    @Query("SELECT r FROM Recertification r WHERE r.organisationId = :org "
         + "AND r.status = 'DUE' AND r.dueDate <= :asOf")
    List<Recertification> findOverdue(@Param("org") Long org, @Param("asOf") LocalDate asOf);
}
