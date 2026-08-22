package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberSubControl;
import com.tss.grc.module.cyber.repository.CyberControlRepository;
import com.tss.grc.module.cyber.repository.CyberSubControlRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cyber sub-controls register.
 *
 * <p>Extracted from the former {@code CyberService} god-class (QUAL-CP-001).
 * Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class CyberSubControlService {

    private final CyberSubControlRepository subControlRepo;
    /** CY-H — verify the parent {@code control_id} references a real control in this tenant. */
    private final CyberControlRepository controlRepo;

    @Transactional(readOnly = true)
    public Page<CyberSubControl> getSubControls(Long orgId, Pageable p) {
        return subControlRepo.findByOrganisationId(orgId, p);
    }

    @Transactional(readOnly = true)
    public Page<CyberSubControl> getSubControlsByControl(Long orgId, Long controlId, Pageable p) {
        return subControlRepo.findByOrganisationIdAndControlId(orgId, controlId, p);
    }

    @Transactional
    public CyberSubControl saveSubControl(CyberSubControl s, Long orgId) {
        // CY-H — title + parent control are NOT NULL; reject a bad body with 400.
        s.setTitleEn(CyberInputValidation.requireText(s.getTitleEn(), "titleEn"));
        CyberInputValidation.requireId(s.getControlId(), "controlId");
        if (controlRepo.findByIdAndOrganisationId(s.getControlId(), orgId).isEmpty()) {
            throw new IllegalArgumentException(
                    "controlId " + s.getControlId() + " does not reference an existing control");
        }
        // Mass-assignment defense — see CyberFrameworkService.saveFramework().
        s.setId(null);
        s.setOrganisationId(orgId);
        s.setIsDeleted(false);
        return subControlRepo.save(s);
    }

    @Transactional
    public CyberSubControl updateSubControl(Long id, CyberSubControl req) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberSubControl existing = subControlRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberSubControl not found: " + id));
        applyEditable(existing, req);
        return subControlRepo.save(existing);
    }

    /**
     * F2-213 — the fields an operator can actually edit through this endpoint.
     *
     * <p>Persisting the REQUEST OBJECT nulled {@code descriptionAr}, because the form that drives
     * this endpoint does not send it — so editing an NCA ECC sub-control through the English form
     * deleted its Arabic description, in a platform whose primary language is Arabic (house rule 7).
     *
     * <p>⚠⚠ F2-275 — {@link com.tss.grc.common.BilingualWriteSymmetryTest} flagged this on its
     * first run, and the finding is that <b>preserving a field is not the same as protecting it</b>.
     * Omitting {@code descriptionAr} from this whitelist stopped the deletion and left the column
     * <b>permanently uneditable</b> — F2-272's defect on
     * {@code privacy.processing_activities.purpose_ar} exactly, where an edit was accepted with HTTP
     * 200 and silently discarded. 🔑 <b>Frozen and protected are indistinguishable from the
     * caller's side.</b> A NULL GUARD gives both properties at once: an omitted Arabic description
     * leaves the stored one alone, and a supplied one is written.
     *
     * <p>⚠ And a correction to my own correction, which is the more useful half. I first wrote that
     * {@code CyberSubControlRequest} <i>"does not exist — its only occurrence in the whole tree was
     * this sentence"</i>, having grepped the Java tree alone. <b>It exists in TypeScript</b>
     * ({@code tss-grc-ui-v2/src/lib/api.ts}), which is exactly what F2-213 meant and what
     * {@code FullReplaceUpdateContractTest}'s reverse leg then proved by failing the build the
     * moment this write was added: the server may not write a column its only client cannot put on
     * the wire. So the fix is BOTH halves in one commit — the guarded write here and the field on
     * the frontend type. 🔑 <b>"That type does not exist" is a claim about every tree, and this
     * platform has two.</b>
     */
    private void applyEditable(CyberSubControl existing, CyberSubControl req) {
        existing.setControlId(req.getControlId());
        existing.setSubControlNumber(req.getSubControlNumber());
        existing.setTitleEn(req.getTitleEn());
        existing.setTitleAr(req.getTitleAr());
        existing.setDescriptionEn(req.getDescriptionEn());
        if (req.getDescriptionAr() != null) existing.setDescriptionAr(req.getDescriptionAr());
        existing.setControlOwner(req.getControlOwner());
        existing.setImplementationStatus(req.getImplementationStatus());
        existing.setMaturityLevel(req.getMaturityLevel());
        existing.setEvidenceRequired(req.getEvidenceRequired());
    }

    @Transactional
    public void deleteSubControl(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberSubControl s = subControlRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberSubControl not found: " + id));
        s.setIsDeleted(true);
        subControlRepo.save(s);
    }
}
