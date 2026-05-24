package com.tss.grc.module.aml.sanctions.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.EntryBulkImportRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.EntryBulkImportResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.EntryCreateRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.EntryResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ListCreateRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ListResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ListUpdateRequest;
import com.tss.grc.module.aml.sanctions.entity.SanctionsList;
import com.tss.grc.module.aml.sanctions.entity.SanctionsListEntry;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListEntryRepository;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 13 — Sanctions list catalog service.
 *
 * <p>Owns the platform-global + tenant-scoped sanctions lists and their
 * entries. Reads use the {@code findVisibleTo} variants so every tenant
 * sees both global (OFAC/UN/EU/SAMA) and its own internal lists. Writes
 * are gated by the controller's {@code @PreAuthorize}.
 *
 * <p>The {@code refresh} method is a hook — in v1 it only stamps
 * {@code lastRefreshedAt}. A follow-up slice will plug in real fetchers
 * (OFAC JSON, UN XML, EU XML) behind a {@code SanctionsSourceAdapter}
 * interface — modelled after the {@code integrations/*} adapter pattern.
 *
 * <p>F8 events emitted: {@code SANCTIONS_LIST_REFRESHED}.
 */
@Service
@RequiredArgsConstructor
public class SanctionsListService {

    private static final Logger log = LoggerFactory.getLogger(SanctionsListService.class);

    private final SanctionsListRepository listRepo;
    private final SanctionsListEntryRepository entryRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() { return TenantContext.requireOrganisationId(); }

    // ─── Reads ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ListResponse> listVisible() {
        Long orgId = org();
        return listRepo.findVisibleTo(orgId).stream().map(SanctionsListService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ListResponse get(Long id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<EntryResponse> listEntries(Long listId) {
        // Authorisation via visible-to (so an org can't read another org's
        // private list's entries).
        loadOrThrow(listId);
        return entryRepo.findByListIdAndIsDeletedFalseOrderByPrimaryNameEnAsc(listId)
            .stream().map(SanctionsListService::entryToResponse).toList();
    }

    // ─── Writes (org-scoped list creation) ─────────────────────────────

    @Transactional
    public ListResponse create(ListCreateRequest req) {
        Long orgId = org();
        if (listRepo.findByListCodeVisibleTo(req.listCode(), orgId).isPresent()) {
            throw new IllegalArgumentException("List code already exists: " + req.listCode());
        }
        SanctionsList l = new SanctionsList();
        l.setOrganisationId(orgId);
        l.setListCode(req.listCode());
        l.setTitleEn(req.titleEn());
        l.setTitleAr(req.titleAr());
        l.setSourceUrl(req.sourceUrl());
        l.setSourceType(req.sourceType());
        if (req.refreshFrequency() != null) l.setRefreshFrequency(req.refreshFrequency());
        if (req.isActive() != null) l.setIsActive(req.isActive());
        SanctionsList saved = listRepo.save(l);
        audit.log("CREATE", "SanctionsList", saved.getId(),
            "Sanctions list created: " + saved.getListCode());
        return toResponse(saved);
    }

    @Transactional
    public ListResponse update(Long id, ListUpdateRequest req) {
        SanctionsList l = loadOrThrow(id);
        if (l.getOrganisationId() == null) {
            // Cannot edit platform-global lists from a tenant context.
            throw new IllegalArgumentException(
                "Cannot edit platform-global list " + l.getListCode() + " from tenant context");
        }
        if (req.titleEn() != null) l.setTitleEn(req.titleEn());
        if (req.titleAr() != null) l.setTitleAr(req.titleAr());
        if (req.sourceUrl() != null) l.setSourceUrl(req.sourceUrl());
        if (req.refreshFrequency() != null) l.setRefreshFrequency(req.refreshFrequency());
        if (req.isActive() != null) l.setIsActive(req.isActive());
        l.setUpdatedAt(LocalDateTime.now());
        SanctionsList saved = listRepo.save(l);
        audit.log("UPDATE", "SanctionsList", saved.getId(),
            "Sanctions list updated: " + saved.getListCode());
        return toResponse(saved);
    }

    /**
     * Refresh hook — stamps {@code lastRefreshedAt}, recomputes
     * {@code entryCount} from the row table, and emits
     * {@code SANCTIONS_LIST_REFRESHED}. Real fetchers (OFAC/UN/EU
     * downloaders) land in a follow-up slice.
     */
    @Transactional
    public ListResponse refresh(Long id) {
        SanctionsList l = loadOrThrow(id);
        long entries = entryRepo.countByListIdAndIsDeletedFalse(l.getId());
        // Preserve a realistic platform-global count where available — if
        // the seeded entry_count is much larger than the JPA-visible rows
        // (e.g. OFAC seeded 16k vs. 4 demo rows) we keep the seeded number.
        int newCount = Math.max(l.getEntryCount(), (int) entries);
        l.setEntryCount(newCount);
        l.setLastRefreshedAt(LocalDateTime.now());
        l.setUpdatedAt(LocalDateTime.now());
        SanctionsList saved = listRepo.save(l);

        audit.log("REFRESH", "SanctionsList", saved.getId(),
            "Sanctions list refreshed: " + saved.getListCode() + " (entries=" + newCount + ")");

        Map<String, Object> payload = new HashMap<>();
        payload.put("listId", String.valueOf(saved.getId()));
        payload.put("listCode", saved.getListCode());
        payload.put("entryCount", newCount);
        publisher.publishEvent(new DomainEvent(
            saved.getOrganisationId(), "SANCTIONS_LIST_REFRESHED",
            "SanctionsList", saved.getId(),
            "sanctions-list:" + saved.getId(), payload));
        return toResponse(saved);
    }

    @Transactional
    public EntryBulkImportResponse bulkImportEntries(EntryBulkImportRequest req) {
        SanctionsList l = loadOrThrow(req.listId());
        int created = 0;
        int skipped = 0;
        for (EntryCreateRequest e : req.entries()) {
            if (entryRepo.existsByListIdAndEntryExternalIdAndIsDeletedFalse(l.getId(), e.entryExternalId())) {
                skipped++;
                continue;
            }
            SanctionsListEntry row = new SanctionsListEntry();
            row.setOrganisationId(l.getOrganisationId());
            row.setListId(l.getId());
            row.setEntryExternalId(e.entryExternalId());
            row.setEntityType(e.entityType());
            row.setPrimaryNameEn(e.primaryNameEn());
            row.setPrimaryNameAr(e.primaryNameAr());
            row.setAliasesJson(e.aliasesJson());
            row.setDateOfBirth(e.dateOfBirth());
            row.setCountry(e.country());
            row.setIdDocumentsJson(e.idDocumentsJson());
            row.setListingDate(e.listingDate());
            row.setListedBy(e.listedBy());
            row.setListingReasonEn(e.listingReasonEn());
            row.setListingReasonAr(e.listingReasonAr());
            if (e.severity() != null) row.setSeverity(e.severity());
            entryRepo.save(row);
            created++;
        }
        // Recompute entry_count after the import
        long entries = entryRepo.countByListIdAndIsDeletedFalse(l.getId());
        l.setEntryCount((int) entries);
        l.setUpdatedAt(LocalDateTime.now());
        listRepo.save(l);

        audit.log("IMPORT", "SanctionsList", l.getId(),
            "Bulk imported " + created + " entries (" + skipped + " skipped) into " + l.getListCode());
        return new EntryBulkImportResponse(req.entries().size(), created, skipped);
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    SanctionsList loadOrThrow(Long id) {
        return listRepo.findByIdVisibleTo(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Sanctions list not found: " + id));
    }

    static ListResponse toResponse(SanctionsList l) {
        return new ListResponse(
            l.getId(), l.getExternalId(), l.getOrganisationId(),
            l.getListCode(), l.getTitleEn(), l.getTitleAr(),
            l.getSourceUrl(), l.getSourceType(),
            l.getLastRefreshedAt(), l.getRefreshFrequency(),
            l.getEntryCount(), l.getIsActive(),
            l.getCreatedAt(), l.getUpdatedAt()
        );
    }

    static EntryResponse entryToResponse(SanctionsListEntry e) {
        return new EntryResponse(
            e.getId(), e.getExternalId(), e.getListId(),
            e.getEntryExternalId(), e.getEntityType(),
            e.getPrimaryNameEn(), e.getPrimaryNameAr(),
            e.getAliasesJson(), e.getDateOfBirth(), e.getCountry(),
            e.getIdDocumentsJson(),
            e.getListingDate(), e.getListedBy(),
            e.getListingReasonEn(), e.getListingReasonAr(),
            e.getSeverity(), e.getCreatedAt()
        );
    }
}
