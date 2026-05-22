package com.tss.grc.module.admin.committee.service;

import com.tss.grc.module.admin.committee.entity.CommitteeFormTemplate;
import com.tss.grc.module.admin.committee.entity.CommitteeStandingItem;
import com.tss.grc.module.admin.committee.entity.CommitteeTemplate;
import com.tss.grc.module.admin.committee.repository.CommitteeFormTemplateRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeStandingItemRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Read-only catalog service for committee templates, their standing
 * agenda items, and the bilingual forms library.
 *
 * <p>The catalog is global — the same 19 templates + 10 forms are visible
 * to every tenant. Instantiation into a live committee (with members,
 * meetings, etc.) is handled separately by {@code CommitteeService}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommitteeCatalogService {

    private final CommitteeTemplateRepository      templates;
    private final CommitteeStandingItemRepository  standingItems;
    private final CommitteeFormTemplateRepository  forms;

    // ── Templates ─────────────────────────────────────────────────────

    public List<CommitteeTemplate> listAllTemplates() {
        return templates.findAllActiveOrdered();
    }

    public List<CommitteeTemplate> listTemplatesByDomain(String domain) {
        return templates.findActiveByDomain(domain);
    }

    public CommitteeTemplate getTemplate(String templateCode) {
        return templates.findByTemplateCode(templateCode)
            .orElseThrow(() -> new EntityNotFoundException("Template " + templateCode));
    }

    public Optional<CommitteeTemplate> findTemplate(String templateCode) {
        return templates.findByTemplateCode(templateCode);
    }

    // ── Standing items ────────────────────────────────────────────────

    public List<CommitteeStandingItem> getStandingItems(String templateCode) {
        return standingItems.findByTemplateCodeOrderBySeqAsc(templateCode);
    }

    public long countStandingItems(String templateCode) {
        return standingItems.countByTemplateCode(templateCode);
    }

    // ── Forms library ─────────────────────────────────────────────────

    public List<CommitteeFormTemplate> listAllForms() {
        return forms.findAllActiveOrdered();
    }

    public CommitteeFormTemplate getForm(String formCode) {
        return forms.findByFormCode(formCode)
            .orElseThrow(() -> new EntityNotFoundException("Form " + formCode));
    }

    /** Forms applicable to a given committee type (e.g. AUDIT_COMMITTEE).
     *  Includes forms marked applicableTo='ALL' + any that explicitly
     *  list this committee type in their comma-separated applicableTo
     *  field. */
    public List<CommitteeFormTemplate> getFormsForCommitteeType(String committeeType) {
        // SQL LIKE pattern: %TYPE% — matches anywhere in the comma list.
        // ALL match is handled in the query.
        String pattern = "%" + committeeType + "%";
        return forms.findActiveApplicableTo(pattern);
    }
}
