package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.KriReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KriReadingRepository extends JpaRepository<KriReading, Long> {
    List<KriReading> findTop60ByKriIdOrderByReadingAtDesc(Long kriId);
    Optional<KriReading> findFirstByKriIdOrderByReadingAtDesc(Long kriId);

    /**
     * Latest reading per KRI for a supplied set of KRI ids — one query in place
     * of an N+1 {@link #findFirstByKriIdOrderByReadingAtDesc} loop (QUAL-BCMP-006).
     *
     * <p>Returns {@code [kriId, band, readingValue]} rows where {@code readingAt}
     * equals the maximum {@code readingAt} for that KRI, mirroring "first by
     * readingAt desc" exactly. The presence of a row for a KRI corresponds to
     * {@code findFirstByKriIdOrderByReadingAtDesc(kriId).isPresent()} (i.e. the
     * KRI has at least one reading); {@code band} may still be null for a row
     * whose latest reading has a null band.
     *
     * <p>Tenant scoping is inherited from the caller-supplied {@code kriIds}
     * (KriReading carries no organisation_id column; it is scoped transitively
     * through its parent KriDefinition, which the caller has already org-filtered).
     * On the rare {@code readingAt} tie the projection can yield two rows for one
     * KRI — identical non-determinism to the per-KRI finder it replaces.
     */
    @Query("""
        SELECT r.kriId, r.band, r.readingValue FROM KriReading r
        WHERE r.kriId IN :kriIds
          AND r.readingAt = (
              SELECT MAX(r2.readingAt) FROM KriReading r2 WHERE r2.kriId = r.kriId
          )
        """)
    List<Object[]> findLatestReadingByKriIdIn(@Param("kriIds") Collection<Long> kriIds);
}
