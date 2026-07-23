package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeGateMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommitteeGateMapRepository extends JpaRepository<CommitteeGateMap, Long> {
    Optional<CommitteeGateMap> findByOrganisationIdAndGate(Long organisationId, String gate);
    List<CommitteeGateMap> findByOrganisationId(Long organisationId);
}
