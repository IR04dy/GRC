package com.tss.grc.module.cyber.asm.repository;

import com.tss.grc.module.cyber.asm.entity.AttackSurfaceShadowIt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttackSurfaceShadowItRepository extends JpaRepository<AttackSurfaceShadowIt, Long> {

    List<AttackSurfaceShadowIt> findByOrganisationIdAndIsDeletedFalseOrderByFirstObservedAtDesc(Long organisationId);

    Optional<AttackSurfaceShadowIt> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);

    long countByOrganisationIdAndStatusAndIsDeletedFalse(Long organisationId, String status);
}
