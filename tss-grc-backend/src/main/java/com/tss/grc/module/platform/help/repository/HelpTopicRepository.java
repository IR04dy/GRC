package com.tss.grc.module.platform.help.repository;

import com.tss.grc.module.platform.help.entity.HelpTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link HelpTopic}.
 *
 * <p>Two read paths matter:
 * <ul>
 *   <li><b>Route lookup</b> — the drawer fires this on every open.
 *       Returns the org-specific row if one exists for the tenant,
 *       otherwise the platform-global row.</li>
 *   <li><b>Search</b> — the "search all help" box in the drawer
 *       header fires this against title + summary + body.</li>
 * </ul>
 *
 * <p>All queries respect the org-override pattern: rows scoped to
 * the current tenant are preferred over platform-global rows.
 */
public interface HelpTopicRepository extends JpaRepository<HelpTopic, Long> {

    Optional<HelpTopic> findByExternalIdAndIsDeletedFalse(UUID externalId);

    Optional<HelpTopic> findByTopicCodeAndIsDeletedFalse(String topicCode);

    /**
     * Find all live topics that match a given route pattern, scoped
     * either to the tenant or to platform-global. The service layer
     * sorts these by precedence (org-specific > platform-global,
     * then exact > wildcard).
     */
    @Query("""
        SELECT h FROM HelpTopic h
         WHERE h.isDeleted = false
           AND (h.organisationId = :orgId OR h.organisationId IS NULL)
        """)
    List<HelpTopic> findAllVisible(@Param("orgId") Long orgId);

    /**
     * List all topics for a module, scoped to the tenant or
     * platform-global. Used by "Related help" widgets.
     */
    @Query("""
        SELECT h FROM HelpTopic h
         WHERE h.isDeleted = false
           AND h.module = :module
           AND (h.organisationId = :orgId OR h.organisationId IS NULL)
         ORDER BY h.organisationId DESC NULLS LAST, h.displayOrder ASC, h.titleEn ASC
        """)
    List<HelpTopic> findByModule(@Param("orgId") Long orgId, @Param("module") String module);

    /**
     * Full-text-ish search across title + summary + body. Uses LIKE
     * which is good enough for the drawer's search box; if the
     * platform later moves to SQL Server FTS we'll swap this query
     * for a contains().
     */
    @Query("""
        SELECT h FROM HelpTopic h
         WHERE h.isDeleted = false
           AND (h.organisationId = :orgId OR h.organisationId IS NULL)
           AND (
                LOWER(h.titleEn)    LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
             OR LOWER(h.titleAr)    LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
             OR LOWER(h.summaryEn)  LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
             OR LOWER(h.summaryAr)  LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
             OR LOWER(h.bodyEn)     LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
             OR LOWER(h.bodyAr)     LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
           )
         ORDER BY h.organisationId DESC NULLS LAST, h.displayOrder ASC
        """)
    List<HelpTopic> search(@Param("orgId") Long orgId, @Param("q") String q);

    long countByTopicCodeAndIsDeletedFalse(String topicCode);
}
