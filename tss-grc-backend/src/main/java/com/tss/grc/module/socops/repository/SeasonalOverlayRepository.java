package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.SeasonalOverlay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeasonalOverlayRepository extends JpaRepository<SeasonalOverlay, Long> {

    Optional<SeasonalOverlay> findByIdAndOrganisationId(Long id, Long organisationId);

    List<SeasonalOverlay> findByOrganisationIdOrderByStartsAtAsc(Long organisationId);

    /** Returns every overlay whose window covers {@code instant}. The F8 engine
     *  uses this to decide whether to auto-tighten KRI thresholds + SLAs. */
    @Query("""
        SELECT s FROM SeasonalOverlay s
        WHERE s.organisationId = :organisationId
          AND s.isActive = true
          AND s.startsAt <= :instant
          AND s.endsAt   >= :instant
        ORDER BY s.startsAt ASC
    """)
    List<SeasonalOverlay> findActiveAt(Long organisationId, LocalDateTime instant);
}
