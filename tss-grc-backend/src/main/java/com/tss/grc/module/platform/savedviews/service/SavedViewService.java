package com.tss.grc.module.platform.savedviews.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.savedviews.dto.SavedViewDto;
import com.tss.grc.module.platform.savedviews.entity.SavedView;
import com.tss.grc.module.platform.savedviews.repository.SavedViewRepository;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Persistence service for per-user saved views. Sprint-4 Track-2.5
 * Phase 2. Frontend adapter (lib/savedViews.ts) calls this through
 * the controller and falls back to localStorage when the user isn't
 * signed in (kiosk/demo/offline).
 *
 * <p>Authoring rule: setting a view as default for a (user, page)
 * combination automatically clears the previous default — the unique
 * partial index on (org, user, page) WHERE is_default=1 makes this
 * a correctness requirement, not just UX polish.
 *
 * <p>Audit-logged on create / update / delete. Reads aren't logged
 * because the dropdown re-reads on every page mount (would flood
 * the audit table).
 */
@Service
@RequiredArgsConstructor
public class SavedViewService {

    private final SavedViewRepository repo;
    private final AuditLogService     audit;

    // ── Reads ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SavedViewDto> listForPage(String pageKey) {
        Long orgId  = TenantContext.requireOrganisationId();
        Long userId = currentUserId();
        return repo.findByOrganisationIdAndUserIdAndPageKeyOrderByNameAsc(orgId, userId, pageKey)
            .stream().map(SavedViewDto::from).toList();
    }

    @Transactional(readOnly = true)
    public SavedViewDto get(Long id) {
        Long orgId  = TenantContext.requireOrganisationId();
        Long userId = currentUserId();
        return repo.findByIdAndOrganisationIdAndUserId(id, orgId, userId)
            .map(SavedViewDto::from)
            .orElseThrow(() -> new EntityNotFoundException("SavedView " + id + " not found"));
    }

    // ── Writes ───────────────────────────────────────────────────────

    @Transactional
    public SavedViewDto create(SavedViewDto in) {
        Long orgId  = TenantContext.requireOrganisationId();
        Long userId = currentUserId();

        SavedView v = new SavedView();
        v.setOrganisationId(orgId);
        v.setUserId(userId);
        v.setPageKey(in.pageKey());
        v.setName(in.name());
        v.setIsDefault(Boolean.TRUE.equals(in.isDefault()));
        v.setIsShared(Boolean.TRUE.equals(in.isShared()));
        v.setFilterJson(in.filterJson());
        v.setSortJson(in.sortJson());
        v.setVisibleColumnsJson(in.visibleColumnsJson());
        v.setUrlQuery(in.urlQuery());
        v.setCreatedBy(userId);
        v.setUpdatedBy(userId);

        SavedView saved = repo.save(v);

        // If this row is the new default, clear the previous default.
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            repo.clearDefaultsExcept(orgId, userId, saved.getPageKey(), saved.getId());
        }

        audit.log("CREATE", "SavedView", saved.getId(),
            "Saved view '" + saved.getName() + "' for " + saved.getPageKey());
        return SavedViewDto.from(saved);
    }

    @Transactional
    public SavedViewDto update(Long id, SavedViewDto in) {
        Long orgId  = TenantContext.requireOrganisationId();
        Long userId = currentUserId();

        SavedView v = repo.findByIdAndOrganisationIdAndUserId(id, orgId, userId)
            .orElseThrow(() -> new EntityNotFoundException("SavedView " + id + " not found"));

        v.setName(in.name());
        v.setIsDefault(Boolean.TRUE.equals(in.isDefault()));
        v.setIsShared(Boolean.TRUE.equals(in.isShared()));
        v.setFilterJson(in.filterJson());
        v.setSortJson(in.sortJson());
        v.setVisibleColumnsJson(in.visibleColumnsJson());
        v.setUrlQuery(in.urlQuery());
        v.setUpdatedAt(LocalDateTime.now());
        v.setUpdatedBy(userId);

        SavedView saved = repo.save(v);

        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            repo.clearDefaultsExcept(orgId, userId, saved.getPageKey(), saved.getId());
        }

        audit.log("UPDATE", "SavedView", saved.getId(),
            "Updated saved view '" + saved.getName() + "'");
        return SavedViewDto.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        Long orgId  = TenantContext.requireOrganisationId();
        Long userId = currentUserId();

        SavedView v = repo.findByIdAndOrganisationIdAndUserId(id, orgId, userId)
            .orElseThrow(() -> new EntityNotFoundException("SavedView " + id + " not found"));

        v.setIsDeleted(Boolean.TRUE);
        v.setUpdatedAt(LocalDateTime.now());
        v.setUpdatedBy(userId);
        repo.save(v);

        audit.log("DELETE", "SavedView", v.getId(),
            "Deleted saved view '" + v.getName() + "'");
    }

    // ── Internals ────────────────────────────────────────────────────

    /** Pull the current user id from the security context. Falls back
     *  to {@code 0L} for system-level callers, though the controller
     *  is annotated {@code isAuthenticated()} so that path shouldn't
     *  fire in practice. */
    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) {
            return u.getUserId();
        }
        return 0L;
    }
}
