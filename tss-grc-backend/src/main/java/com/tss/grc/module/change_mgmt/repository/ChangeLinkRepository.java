package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChangeLinkRepository extends JpaRepository<ChangeLink, Long> {
    List<ChangeLink> findByChangeRequestId(Long changeRequestId);
    List<ChangeLink> findByEntityTypeAndEntityId(String entityType, Long entityId);

    /**
     * CM-L3: org-scoped inverse lookup. Joins the parent change so the tenant
     * filter runs in SQL, instead of fetching every org's links for the entity
     * and post-filtering per row (safe before, but an N+1). {@code @Where} on
     * both {@link ChangeLink} and ChangeRequest excludes soft-deleted rows.
     * Returns [ChangeLink, ChangeRequest] pairs for the current org only.
     */
    @Query("""
        select l, c from ChangeLink l, ChangeRequest c
        where c.id = l.changeRequestId
          and l.entityType = :entityType
          and l.entityId = :entityId
          and c.organisationId = :org
        """)
    List<Object[]> findLinkedChangesForEntity(@Param("entityType") String entityType,
                                              @Param("entityId") Long entityId,
                                              @Param("org") Long org);
}
