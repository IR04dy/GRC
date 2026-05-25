package com.tss.grc.module.ethics.coi.repository;

import com.tss.grc.module.ethics.coi.entity.CoiRecusalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoiRecusalEventRepository extends JpaRepository<CoiRecusalEvent, Long> {

    List<CoiRecusalEvent> findByOrganisationIdAndIsDeletedFalseOrderByRecusalDateDesc(Long organisationId);

    List<CoiRecusalEvent> findByOrganisationIdAndUserIdAndIsDeletedFalseOrderByRecusalDateDesc(
            Long organisationId, Long userId);

    /**
     * Convenience alias matching the spec wording verbatim — the index
     * back-end is the {@code IsDeletedFalse} variant above. Spring Data
     * accepts both forms; we keep both so service-layer call sites can
     * use whichever reads more naturally.
     */
    default List<CoiRecusalEvent> findByOrganisationIdAndUserIdOrderByRecusalDateDesc(
            Long organisationId, Long userId) {
        return findByOrganisationIdAndUserIdAndIsDeletedFalseOrderByRecusalDateDesc(organisationId, userId);
    }
}
