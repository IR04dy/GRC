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

    /** Overlays whose start_at falls in {@code [from, to]} — used by the
     *  sweep to fire {@code SEASONAL_OVERLAY_ACTIVATED} on the day an
     *  overlay opens (Hajj / Ramadan / National Day etc.). The sweep window
     *  is one day so a late tick still catches the activation; audit-log
     *  dedup prevents a re-fire on the next tick. */
    @Query("""
        SELECT s FROM SeasonalOverlay s
        WHERE s.organisationId = :organisationId
          AND s.isActive = true
          AND s.startsAt BETWEEN :from AND :to
        ORDER BY s.startsAt ASC
    """)
    List<SeasonalOverlay> findStartingBetween(Long organisationId, LocalDateTime from, LocalDateTime to);
}
