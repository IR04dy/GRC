package com.tss.grc.module.admin.committee.service;

import com.tss.grc.module.admin.committee.entity.CommitteeFormTemplate;
import com.tss.grc.module.admin.committee.entity.CommitteeStandingItem;
import com.tss.grc.module.admin.committee.entity.CommitteeTemplate;
import com.tss.grc.module.admin.committee.repository.CommitteeFormTemplateRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeStandingItemRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
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
// noRollbackFor RuntimeException is the critical bit — without it, Spring
// marks the transaction rollback-only when the underlying JPA query throws
// (e.g. SQL Server "Invalid object name 'admin.committee_templates'").
// Even though our catch returns an empty list, Spring then throws
// UnexpectedRollbackException at commit time because the tx was marked
// rollback-only, which surfaces as a 500. With noRollbackFor the tx
// stays committable and the catch's empty-list return reaches the client.
@Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
public class CommitteeCatalogService {

    private static final Logger log = LoggerFactory.getLogger(CommitteeCatalogService.class);

    private final CommitteeTemplateRepository      templates;
    private final CommitteeStandingItemRepository  standingItems;
    private final CommitteeFormTemplateRepository  forms;

    /**
     * Returns {@code true} when the underlying SQL error indicates the
     * committees tables don't exist in this database — typical on a
     * fresh dev DB where Flyway is disabled and the {@code
     * setup_committees_db.py} bootstrap hasn't been run. We log once and
     * degrade to empty results so the catalog page renders an empty
     * state instead of a 500 the user can't action.
     */
    private static boolean isMissingObjectError(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String low = msg.toLowerCase();
                // SQL Server: "Invalid object name 'admin.committee_templates'"
                // Postgres/H2 variants kept here for forward-compat
                if (low.contains("invalid object name")
                    || low.contains("does not exist")
                    || low.contains("not found")) return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    // ── Templates ─────────────────────────────────────────────────────

    public List<CommitteeTemplate> listAllTemplates() {
        try {
            return templates.findAllActiveOrdered();
        } catch (RuntimeException e) {
            if (isMissingObjectError(e)) {
                log.warn("[catalog] admin.committee_templates missing — run setup_committees_db.py; returning empty list");
                return Collections.emptyList();
            }
            throw e;
        }
    }

    public List<CommitteeTemplate> listTemplatesByDomain(String domain) {
        try {
            return templates.findActiveByDomain(domain);
        } catch (RuntimeException e) {
            if (isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    public CommitteeTemplate getTemplate(String templateCode) {
        return templates.findByTemplateCode(templateCode)
            .orElseThrow(() -> new EntityNotFoundException("Template " + templateCode));
    }

    public Optional<CommitteeTemplate> findTemplate(String templateCode) {
        try {
            return templates.findByTemplateCode(templateCode);
        } catch (RuntimeException e) {
            if (isMissingObjectError(e)) return Optional.empty();
            throw e;
        }
    }

    // ── Standing items ────────────────────────────────────────────────

    public List<CommitteeStandingItem> getStandingItems(String templateCode) {
        try {
            return standingItems.findByTemplateCodeOrderBySeqAsc(templateCode);
        } catch (RuntimeException e) {
            if (isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    public long countStandingItems(String templateCode) {
        try {
            return standingItems.countByTemplateCode(templateCode);
        } catch (RuntimeException e) {
            if (isMissingObjectError(e)) return 0L;
            throw e;
        }
    }

    // ── Forms library ─────────────────────────────────────────────────

    public List<CommitteeFormTemplate> listAllForms() {
        try {
            return forms.findAllActiveOrdered();
        } catch (RuntimeException e) {
            if (isMissingObjectError(e)) {
                log.warn("[catalog] admin.committee_form_templates missing — run setup_committees_db.py; returning empty list");
                return Collections.emptyList();
            }
            throw e;
        }
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
        try {
            return forms.findActiveApplicableTo(pattern);
        } catch (RuntimeException e) {
            if (isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }
}
