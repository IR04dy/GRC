package com.tss.grc.module.knowledge.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.knowledge.dto.KbArticleDto;
import com.tss.grc.module.knowledge.entity.KbArticle;
import com.tss.grc.module.knowledge.repository.KbArticleRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * KB article service. State machine:
 *   DRAFT     → PUBLISHED, ARCHIVED
 *   PUBLISHED → DRAFT (revisions), ARCHIVED
 *   ARCHIVED  → DRAFT (un-archive)
 *
 * Transitioning to PUBLISHED stamps {@code publishedAt} on first publish only
 * (subsequent re-publishes update {@code updatedAt} but keep the original
 * publish date).
 */
@Service
@RequiredArgsConstructor
public class KbArticleService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "DRAFT",     Set.of("PUBLISHED", "ARCHIVED"),
        "PUBLISHED", Set.of("DRAFT", "ARCHIVED"),
        "ARCHIVED",  Set.of("DRAFT")
    );

    private final KbArticleRepository repo;

    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public List<KbArticleDto> listPublished(String category) {
        Long org = TenantContext.requireOrganisationId();
        List<KbArticle> rows = (category == null || category.isBlank())
            ? repo.findByOrganisationIdAndStatusOrderByPublishedAtDesc(org, "PUBLISHED")
            : repo.findByOrganisationIdAndCategoryCodeAndStatus(org, category, "PUBLISHED");
        return rows.stream().map(KbArticleDto::from).collect(Collectors.toList());
    }

    public KbArticleDto bySlug(String slug) {
        Long org = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndSlug(org, slug)
            .map(KbArticleDto::from)
            .orElseThrow(() -> new EntityNotFoundException("KB article not found: " + slug));
    }

    @Transactional
    public KbArticleDto create(KbArticleDto req, String author) {
        Long org = TenantContext.requireOrganisationId();
        // Slug uniqueness check (per-org)
        if (repo.findByOrganisationIdAndSlug(org, req.getSlug()).isPresent()) {
            throw new IllegalArgumentException("Article with slug '" + req.getSlug() + "' already exists");
        }
        KbArticle a = new KbArticle();
        a.setExternalId(req.getId() != null ? req.getId() : generateExternalId());
        a.setOrganisationId(org);
        a.setSlug(req.getSlug());
        a.setCategoryCode(req.getCategoryCode());
        a.setTitleEn(req.getTitleEn()); a.setTitleAr(req.getTitleAr());
        a.setSummaryEn(req.getSummaryEn()); a.setSummaryAr(req.getSummaryAr());
        a.setBodyEnMd(req.getBodyEnMd()); a.setBodyArMd(req.getBodyArMd());
        a.setTags(req.getTags());
        a.setStatus("DRAFT");
        a.setVisibility(req.getVisibility() != null ? req.getVisibility() : "INTERNAL");
        a.setAuthor(author);
        KbArticleDto out = KbArticleDto.from(repo.save(a));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(org, "kb_article", a.getExternalId(), out);
        return out;
    }

    @Transactional
    public KbArticleDto update(String slug, KbArticleDto req) {
        Long org = TenantContext.requireOrganisationId();
        KbArticle a = repo.findByOrganisationIdAndSlug(org, slug)
            .orElseThrow(() -> new EntityNotFoundException("KB article not found: " + slug));
        // Edit only content fields — status changes go through transition()
        if (req.getTitleEn() != null)     a.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null)     a.setTitleAr(req.getTitleAr());
        if (req.getSummaryEn() != null)   a.setSummaryEn(req.getSummaryEn());
        if (req.getSummaryAr() != null)   a.setSummaryAr(req.getSummaryAr());
        if (req.getBodyEnMd() != null)    a.setBodyEnMd(req.getBodyEnMd());
        if (req.getBodyArMd() != null)    a.setBodyArMd(req.getBodyArMd());
        if (req.getCategoryCode() != null) a.setCategoryCode(req.getCategoryCode());
        if (req.getTags() != null)        a.setTags(req.getTags());
        if (req.getVisibility() != null)  a.setVisibility(req.getVisibility());
        a.setUpdatedAt(LocalDateTime.now());
        KbArticleDto out = KbArticleDto.from(repo.save(a));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(org, "kb_article", a.getExternalId(), out);
        return out;
    }

    @Transactional
    public KbArticleDto transition(String slug, KbArticleDto.TransitionRequest req) {
        Long org = TenantContext.requireOrganisationId();
        KbArticle a = repo.findByOrganisationIdAndSlug(org, slug)
            .orElseThrow(() -> new EntityNotFoundException("KB article not found: " + slug));
        Set<String> allowed = ALLOWED.getOrDefault(a.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(a.getStatus(), req.getStatus(), allowed);
        }
        LocalDateTime now = LocalDateTime.now();
        a.setStatus(req.getStatus());
        if ("PUBLISHED".equals(req.getStatus()) && a.getPublishedAt() == null) {
            a.setPublishedAt(now);
        }
        a.setUpdatedAt(now);
        KbArticleDto out = KbArticleDto.from(repo.save(a));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(org, "kb_article", a.getExternalId(), out);
        return out;
    }

    @Transactional
    public void softDelete(String slug) {
        Long org = TenantContext.requireOrganisationId();
        KbArticle a = repo.findByOrganisationIdAndSlug(org, slug)
            .orElseThrow(() -> new EntityNotFoundException("KB article not found: " + slug));
        a.setIsDeleted((short) 1);
        repo.save(a);
    }

    /** Increment view counter (fire-and-forget; no realtime broadcast). */
    @Transactional
    public void recordView(String slug) {
        Long org = TenantContext.requireOrganisationId();
        repo.findByOrganisationIdAndSlug(org, slug).ifPresent(a -> {
            a.setViewCount(a.getViewCount() == null ? 1 : a.getViewCount() + 1);
            repo.save(a);
        });
    }

    private String generateExternalId() {
        return String.format("KB-%d-%s",
            LocalDateTime.now().getYear(),
            Long.toHexString(System.currentTimeMillis()).toUpperCase().substring(0, 6));
    }
}
