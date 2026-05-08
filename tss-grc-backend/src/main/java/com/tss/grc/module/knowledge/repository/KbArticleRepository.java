package com.tss.grc.module.knowledge.repository;

import com.tss.grc.module.knowledge.entity.KbArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface KbArticleRepository extends JpaRepository<KbArticle, Long> {
    List<KbArticle> findByOrganisationIdAndStatusOrderByPublishedAtDesc(Long organisationId, String status);
    Optional<KbArticle> findByOrganisationIdAndSlug(Long organisationId, String slug);
    List<KbArticle> findByOrganisationIdAndCategoryCodeAndStatus(Long organisationId, String categoryCode, String status);
}
