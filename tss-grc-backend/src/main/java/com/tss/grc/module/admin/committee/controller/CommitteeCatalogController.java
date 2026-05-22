package com.tss.grc.module.admin.committee.controller;

import com.tss.grc.module.admin.committee.entity.CommitteeFormTemplate;
import com.tss.grc.module.admin.committee.entity.CommitteeStandingItem;
import com.tss.grc.module.admin.committee.entity.CommitteeTemplate;
import com.tss.grc.module.admin.committee.service.CommitteeCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only REST controller for the committee catalog — 19 templates,
 * ~150 standing agenda items, and the 10-form bilingual library.
 *
 * <p>All endpoints require {@code ADMIN:READ} since the catalog drives
 * organisational decisions (which templates to instantiate, which forms
 * to download). The catalog itself is global — not tenant-scoped — so
 * no organisationId filtering.
 */
@RestController
@RequestMapping("/api/v1/committees/catalog")
@RequiredArgsConstructor
public class CommitteeCatalogController {

    private final CommitteeCatalogService catalog;

    // ── Templates ─────────────────────────────────────────────────────

    @GetMapping("/templates")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeTemplate> listTemplates(
            @RequestParam(required = false) String domain) {
        return domain == null
            ? catalog.listAllTemplates()
            : catalog.listTemplatesByDomain(domain);
    }

    @GetMapping("/templates/{templateCode}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public CommitteeTemplate getTemplate(@PathVariable String templateCode) {
        return catalog.getTemplate(templateCode);
    }

    @GetMapping("/templates/{templateCode}/standing-items")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeStandingItem> getStandingItems(@PathVariable String templateCode) {
        return catalog.getStandingItems(templateCode);
    }

    // ── Forms library ─────────────────────────────────────────────────

    @GetMapping("/forms")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeFormTemplate> listForms(
            @RequestParam(required = false) String committeeType) {
        return committeeType == null
            ? catalog.listAllForms()
            : catalog.getFormsForCommitteeType(committeeType);
    }

    @GetMapping("/forms/{formCode}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public CommitteeFormTemplate getForm(@PathVariable String formCode) {
        return catalog.getForm(formCode);
    }
}
