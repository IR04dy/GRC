package com.tss.grc.module.ndi.gap.repository;

import com.tss.grc.module.ndi.gap.entity.NdiCorrectiveAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NdiCorrectiveActionRepository extends JpaRepository<NdiCorrectiveAction, Long> {

    List<NdiCorrectiveAction> findByOrganisationIdAndGapIdOrderByIdAsc(Long org, Long gapId);

    Optional<NdiCorrectiveAction> findByIdAndOrganisationId(Long id, Long org);

    List<NdiCorrectiveAction> findByOrganisationIdAndStatusOrderByTargetDateAsc(Long org, String status);

    List<NdiCorrectiveAction> findByOrganisationId(Long org);
}
