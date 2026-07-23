package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeFreezeWindow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ChangeFreezeWindowRepository extends JpaRepository<ChangeFreezeWindow, Long> {

    List<ChangeFreezeWindow> findByOrganisationIdOrderByStartsAtAsc(Long organisationId);

    List<ChangeFreezeWindow> findByOrganisationIdAndActiveTrue(Long organisationId);

    /** Active freeze windows for an org that overlap the given interval. */
    List<ChangeFreezeWindow> findByOrganisationIdAndActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
        Long organisationId, LocalDateTime windowEnd, LocalDateTime windowStart);
}
