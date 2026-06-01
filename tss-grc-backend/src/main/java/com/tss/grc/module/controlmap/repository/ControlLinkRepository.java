package com.tss.grc.module.controlmap.repository;

import com.tss.grc.module.controlmap.entity.ControlLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ControlLinkRepository extends JpaRepository<ControlLink, Long> {

    List<ControlLink> findByOrganisationIdAndEntityTypeAndEntityId(
        Long organisationId, String entityType, Long entityId);

    /** Bulk delete (executes immediately) so the replace-set re-insert can't
     *  collide with the unique index on (org, type, id, sub_control). */
    @Modifying
    @Query("DELETE FROM ControlLink l WHERE l.organisationId = :orgId "
         + "AND l.entityType = :entityType AND l.entityId = :entityId")
    void deleteForEntity(@Param("orgId") Long organisationId,
                         @Param("entityType") String entityType,
                         @Param("entityId") Long entityId);
}
