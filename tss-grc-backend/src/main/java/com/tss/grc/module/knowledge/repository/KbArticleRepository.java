package com.tss.grc.module.knowledge.repository;

import com.tss.grc.module.knowledge.entity.KbArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface KbArticleRepository extends JpaRepository<KbArticle, Long> {
    List<KbArticle> findByOrganisationIdAndStatusOrderByPublishedAtDesc(Long organisationId, String status);
    Optional<KbArticle> findByOrganisationIdAndSlug(Long organisationId, String slug);
    List<KbArticle> findByOrganisationIdAndCategoryCodeAndStatus(Long organisationId, String categoryCode, String status);

    /**
     * F2-374 — count rows with this slug INCLUDING soft-deleted ones.
     *
     * <p>The derived {@link #findByOrganisationIdAndSlug} respects the entity's
     * {@code @Where(is_deleted = 0)}, so it cannot see a soft-deleted article — but
     * {@code uk_kb_slug} (V21) is a PLAIN unique constraint that counts every physical row.
     * So a create whose slug was freed only in the app's view (deleted, not gone) passes the
     * finder guard and then violates the constraint. This native count sees what the constraint
     * sees, so {@code create} can report a clean 400 rather than an opaque unique-index 500.
     * Native SQL by design: Hibernate's {@code @Where} is applied to entity queries, not native.
     */
    @Query(value = "SELECT COUNT(*) FROM kb.articles WHERE organisation_id = :org AND slug = :slug",
           nativeQuery = true)
    long countBySlugIncludingDeleted(@Param("org") Long org, @Param("slug") String slug);
}
