package com.tss.grc.module.bcm.cycle.runtime.repository;

import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Slice 10-B — tenant-scoped repository for {@link ExerciseRunResponse}.
 */
public interface ExerciseRunResponseRepository extends JpaRepository<ExerciseRunResponse, Long> {

    List<ExerciseRunResponse> findByOrganisationIdAndRunInjectIdOrderByResponseAtAsc(
        Long organisationId, Long runInjectId);

    List<ExerciseRunResponse> findByOrganisationIdAndParticipantUserIdOrderByResponseAtDesc(
        Long organisationId, Long participantUserId);

    long countByOrganisationIdAndRunInjectId(Long organisationId, Long runInjectId);
}
