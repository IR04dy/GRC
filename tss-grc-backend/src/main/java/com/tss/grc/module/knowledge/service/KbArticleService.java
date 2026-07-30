package com.tss.grc.module.knowledge.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.knowledge.dto.KbArticleDto;
import com.tss.grc.module.knowledge.entity.KbArticle;
import com.tss.grc.module.knowledge.repository.KbArticleRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import com.tss.grc.common.util.ExternalIds;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

    /**
     * F2-53 — an unpublished article used to be readable by anyone who knew the slug.
     *
     * <p>{@link #listPublished} has always filtered {@code status='PUBLISHED'}; this lookup resolved
     * by slug alone, so a DRAFT or ARCHIVED article was served to any authenticated caller. The list
     * endpoint is what hid it: a draft never appeared there, so nothing advertised the slug — but
     * slugs in this corpus are guessable by construction ({@code whistleblowing-channel-how-to-report}),
     * and an author's in-progress revision of exactly that procedure was readable before review.
     *
     * <p>Callers holding {@code KB:WRITE} still see every status, because an author has to be able to
     * open their own draft. That check is the granted authority rather than a role name, so it agrees
     * with the controller's {@code hasPermission('KB','WRITE')} guard instead of restating it as a
     * list of identities — the F2-52 lesson applied one layer down.
     */
    public KbArticleDto bySlug(String slug) {
        Long org = TenantContext.requireOrganisationId();
        KbArticle a = repo.findByOrganisationIdAndSlug(org, slug)
            .orElseThrow(() -> new EntityNotFoundException("KB article not found: " + slug));
        if (!"PUBLISHED".equals(a.getStatus()) && !callerMayAuthor()) {
            // 404, not 403: whether an unpublished slug exists is itself information, and the caller
            // is not entitled to distinguish "not published yet" from "no such article".
            throw new EntityNotFoundException("KB article not found: " + slug);
        }
        return KbArticleDto.from(a);
    }

    /**
     * Whether the current principal may author knowledge-base content — the same authority
     * {@code hasPermission('KB','WRITE')} resolves to, plus the PLATFORM_ADMIN bypass that
     * {@code ModulePermissionEvaluator} applies for every other guard.
     */
    private boolean callerMayAuthor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> "KB_WRITE".equals(a) || "ROLE_PLATFORM_ADMIN".equals(a));
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
        // F2-48: substring(0, 6) kept the HIGH-ORDER hex digits, so this id changed once
        // per 2^20 ms (~17.5 min) and every KB raised in one window collided on the unique
        // index. Shared generator now, so the tenth copy cannot reintroduce it.
        return ExternalIds.generate("KB");
    }
}
