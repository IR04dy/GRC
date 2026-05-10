package com.tss.grc.module.capa.repository;

import com.tss.grc.module.capa.entity.CapaTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CapaTaskRepository extends JpaRepository<CapaTask, Long> {
    List<CapaTask> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
    Optional<CapaTask> findByOrganisationIdAndExternalId(Long organisationId, String externalId);
    List<CapaTask> findByOrganisationIdAndOriginTypeAndOriginId(Long organisationId, String originType, String originId);
    List<CapaTask> findByOrganisationIdAndOwnerAndStatusNotIn(Long organisationId, String owner, List<String> excluded);
    List<CapaTask> findByOrganisationIdAndStatusNotInAndTargetDateBefore(Long organisationId, List<String> excluded, LocalDate before);
}
