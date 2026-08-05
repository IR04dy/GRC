package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeSchedulePublication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * S8g / CE-8 — published forward schedules (V356).
 *
 * <p>There is no update path by design (see the entity): superseding a publication means publishing
 * again, so every read here is "latest first". Every query filters {@code organisationId}
 * (CLAUDE.md: developer responsibility).
 */
public interface ChangeSchedulePublicationRepository extends JpaRepository<ChangeSchedulePublication, Long> {

    /** The publication history, newest first — the FSC's own audit trail. */
    List<ChangeSchedulePublication> findByOrganisationIdOrderByPublishedAtDescIdDesc(Long organisationId);

    /**
     * The current published schedule: the most recent publication.
     *
     * <p>⚠ "Most recent" and "covering today" are different questions, and this answers the first.
     * A tenant that published a January schedule and never published again has a current publication
     * whose window has expired — and the service says so rather than pretending none exists, because
     * "nobody has ever published" and "the published one is stale" are different governance failures.
     */
    Optional<ChangeSchedulePublication> findFirstByOrganisationIdOrderByPublishedAtDescIdDesc(Long organisationId);

    Optional<ChangeSchedulePublication> findByIdAndOrganisationId(Long id, Long organisationId);
}
