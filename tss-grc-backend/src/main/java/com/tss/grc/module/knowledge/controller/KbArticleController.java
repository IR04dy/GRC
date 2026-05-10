package com.tss.grc.module.knowledge.controller;

import com.tss.grc.module.knowledge.dto.KbArticleDto;
import com.tss.grc.module.knowledge.service.KbArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Knowledge Base — read + write endpoints (Sprint 4 §2.9 completion).
 *
 * <pre>
 *   GET    /api/kb/articles                — list published (optional ?category=)
 *   GET    /api/kb/articles/{slug}         — read one + record view
 *   POST   /api/kb/articles                — create draft (admin/author only)
 *   PUT    /api/kb/articles/{slug}         — edit content
 *   POST   /api/kb/articles/{slug}/transition — publish / archive / re-draft
 *   DELETE /api/kb/articles/{slug}         — soft-delete (admin only)
 * </pre>
 */
@RestController
@RequestMapping("/api/kb/articles")
@RequiredArgsConstructor
public class KbArticleController {

    private final KbArticleService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<KbArticleDto> listPublished(@RequestParam(required = false) String category) {
        return service.listPublished(category);
    }

    @GetMapping("/{slug}")
    @PreAuthorize("isAuthenticated()")
    public KbArticleDto bySlug(@PathVariable String slug) {
        // Record a view as a side-effect of read.
        service.recordView(slug);
        return service.bySlug(slug);
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('KB_WRITE')")
    public ResponseEntity<KbArticleDto> create(@RequestBody @Valid KbArticleDto req,
                                               Authentication auth) {
        return ResponseEntity.ok(service.create(req, auth.getName()));
    }

    @PutMapping("/{slug}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('KB_WRITE')")
    public KbArticleDto update(@PathVariable String slug, @RequestBody @Valid KbArticleDto req) {
        return service.update(slug, req);
    }

    @PostMapping("/{slug}/transition")
    @PreAuthorize("hasRole('PLATFORM_ADMIN') or hasAuthority('KB_WRITE')")
    public KbArticleDto transition(@PathVariable String slug,
                                   @RequestBody @Valid KbArticleDto.TransitionRequest req) {
        return service.transition(slug, req);
    }

    @DeleteMapping("/{slug}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String slug) {
        service.softDelete(slug);
        return ResponseEntity.noContent().build();
    }
}
