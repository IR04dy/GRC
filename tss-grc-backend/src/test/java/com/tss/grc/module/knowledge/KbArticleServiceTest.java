package com.tss.grc.module.knowledge;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.knowledge.dto.KbArticleDto;
import com.tss.grc.module.knowledge.entity.KbArticle;
import com.tss.grc.module.knowledge.repository.KbArticleRepository;
import com.tss.grc.module.knowledge.service.KbArticleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-374 — a KB create whose slug was freed only by a soft delete must not reach an opaque
 * unique-index 500.
 *
 * <p>{@code kb.articles} carries {@code @Where(is_deleted = 0)} on the entity, so the derived
 * {@code findByOrganisationIdAndSlug} guard in {@link KbArticleService#create} cannot see a
 * soft-deleted row — but {@code uk_kb_slug} (V21) is a PLAIN unique constraint that counts every
 * physical row. So an admin delete of a slug followed by a create of the same slug passes the
 * finder guard (the row is invisible to the app) and then violates the constraint at INSERT: an
 * opaque 500 where the guard promised a clean 400. The fix adds a native count that sees what the
 * constraint sees ({@code countBySlugIncludingDeleted}) and returns the clean 400 instead.
 *
 * <p>No unit test can reproduce the actual 500 — a mocked repository has no unique index — so this
 * asserts the SOURCING: that the create consults the deleted-inclusive count and refuses on it.
 * {@link KbArticleGuardTest} covers the endpoint guards; this covers the create's uniqueness logic.
 */
@ExtendWith(MockitoExtension.class)
class KbArticleServiceTest {

    @Mock KbArticleRepository repo;
    @InjectMocks KbArticleService service;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private KbArticleDto req(String slug) {
        return KbArticleDto.builder()
            .slug(slug).titleEn("Title").titleAr("عنوان").build();
    }

    /**
     * The discriminating leg. Pre-fix the create checks only the {@code @Where}-filtered finder
     * (empty for a soft-deleted slug), so it proceeds to {@code repo.save} — which in production
     * hits the unique index (500) and in this mock silently succeeds. Post-fix the deleted-inclusive
     * count refuses it with a clean {@code IllegalArgumentException} before any save.
     */
    @Test
    void aSlugReservedByASoftDeletedArticleIsRefusedCleanlyNotWithACrash() {
        TenantContext.setCurrentTenant(1L);
        when(repo.findByOrganisationIdAndSlug(1L, "whistleblowing-how-to")).thenReturn(Optional.empty());
        when(repo.countBySlugIncludingDeleted(1L, "whistleblowing-how-to")).thenReturn(1L);

        assertThatThrownBy(() -> service.create(req("whistleblowing-how-to"), "author"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reserved");
        verify(repo, never()).save(any());
    }

    /** A LIVE duplicate keeps the pre-existing "already exists" message (regression guard). */
    @Test
    void aLiveDuplicateSlugKeepsTheAlreadyExistsMessage() {
        TenantContext.setCurrentTenant(1L);
        when(repo.findByOrganisationIdAndSlug(1L, "already-there"))
            .thenReturn(Optional.of(new KbArticle()));

        assertThatThrownBy(() -> service.create(req("already-there"), "author"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(repo, never()).save(any());
    }

    /** A genuinely fresh slug creates, and the deleted-inclusive count is consulted on the way. */
    @Test
    void aFreshSlugCreatesAndConsultsTheDeletedInclusiveCount() {
        TenantContext.setCurrentTenant(1L);
        when(repo.findByOrganisationIdAndSlug(1L, "brand-new")).thenReturn(Optional.empty());
        when(repo.countBySlugIncludingDeleted(1L, "brand-new")).thenReturn(0L);
        when(repo.save(any(KbArticle.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> service.create(req("brand-new"), "author")).doesNotThrowAnyException();
        verify(repo).countBySlugIncludingDeleted(1L, "brand-new");
        verify(repo).save(any(KbArticle.class));
    }
}
