package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberEvidence;
import com.tss.grc.module.cyber.repository.CyberControlRepository;
import com.tss.grc.module.cyber.repository.CyberEvidenceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Cyber evidence locker.
 *
 * <p>Extracted from the former {@code CyberService} god-class (QUAL-CP-001).
 * Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class CyberEvidenceService {

    private final CyberEvidenceRepository evidenceRepo;
    /** CY-H — used to verify {@code control_id} references a real control (no DB FK exists). */
    private final CyberControlRepository controlRepo;

    /** CY-H — evidence freshness status is a free string in the DB (no CHECK
     *  constraint), so constrain it here. Covers the UI's CURRENT/EXPIRED plus
     *  the STALE state the CY-J expiry sweep introduces. */
    private static final Set<String> EVIDENCE_STATUSES =
            Set.of("CURRENT", "PENDING", "STALE", "EXPIRED", "ARCHIVED", "SUPERSEDED");

    @Transactional(readOnly = true)
    public Page<CyberEvidence> getEvidence(Long orgId, Pageable p) {
        return evidenceRepo.findByOrganisationId(orgId, p);
    }

    @Transactional
    public CyberEvidence saveEvidence(CyberEvidence e, Long orgId) {
        validate(e, orgId);
        e.setOrganisationId(orgId);
        e.setIsDeleted(false);
        return evidenceRepo.save(e);
    }

    @Transactional
    public CyberEvidence updateEvidence(Long id, CyberEvidence req) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        Long orgId = TenantContext.requireOrganisationId();
        CyberEvidence existing = evidenceRepo.findByIdAndOrganisationId(id, orgId)
                .orElseThrow(() -> new EntityNotFoundException("CyberEvidence not found: " + id));
        validate(req, orgId);
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        // CY-J — staleSince / reRequestId are sweep-managed, never client-editable: carry them over
        // so a manual edit (the FE round-trips the whole entity) can't wipe the expiry bookkeeping.
        req.setStaleSince(existing.getStaleSince());
        req.setReRequestId(existing.getReRequestId());
        req.setIsDeleted(false);
        return evidenceRepo.save(req);
    }

    /**
     * CY-H — reject malformed evidence bodies with a 400 instead of letting them
     * hit the DB (opaque 500) or persist a nonsense row. Requires a title,
     * verifies {@code controlId} references a real control in this tenant, and
     * constrains {@code status} to the known freshness lifecycle.
     */
    private void validate(CyberEvidence e, Long orgId) {
        e.setTitleEn(CyberInputValidation.requireText(e.getTitleEn(), "titleEn"));
        if (e.getControlId() != null
                && controlRepo.findByIdAndOrganisationId(e.getControlId(), orgId).isEmpty()) {
            throw new IllegalArgumentException(
                    "controlId " + e.getControlId() + " does not reference an existing control");
        }
        e.setStatus(CyberInputValidation.checkEnum(e.getStatus(), "status", EVIDENCE_STATUSES, false, "CURRENT"));
    }

    @Transactional
    public void deleteEvidence(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberEvidence e = evidenceRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberEvidence not found: " + id));
        e.setIsDeleted(true);
        evidenceRepo.save(e);
    }
}
