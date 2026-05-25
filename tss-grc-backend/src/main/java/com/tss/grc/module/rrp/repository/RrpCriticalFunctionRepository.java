package com.tss.grc.module.rrp.repository;

import com.tss.grc.module.rrp.entity.RrpCriticalFunction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RrpCriticalFunctionRepository extends JpaRepository<RrpCriticalFunction, Long> {

    List<RrpCriticalFunction> findByCycleIdAndOrganisationIdOrderByFunctionCodeAsc(
            Long cycleId, Long organisationId);

    Optional<RrpCriticalFunction> findByIdAndOrganisationId(Long id, Long organisationId);

    long countByCycleIdAndOrganisationId(Long cycleId, Long organisationId);
}
