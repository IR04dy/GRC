package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CloudService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CloudServiceRepository extends JpaRepository<CloudService, Long> {

    List<CloudService> findByOrganisationIdOrderByServiceNameAsc(Long organisationId);

    Optional<CloudService> findByIdAndOrganisationId(Long id, Long organisationId);

    List<CloudService> findByOrganisationIdAndStatusOrderByServiceNameAsc(Long organisationId, String status);

    List<CloudService> findByOrganisationIdAndCriticalityOrderByServiceNameAsc(Long organisationId, String criticality);

    long countByOrganisationIdAndKingdomDataResident(Long organisationId, Boolean kingdomDataResident);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndCriticality(Long organisationId, String criticality);
}
