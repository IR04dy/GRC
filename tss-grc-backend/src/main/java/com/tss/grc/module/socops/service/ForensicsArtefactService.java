package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.ForensicsArtefact;
import com.tss.grc.module.socops.repository.ForensicsArtefactRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ForensicsArtefactService {

    private final ForensicsArtefactRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    @Transactional(readOnly = true)
    public Page<ForensicsArtefact> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByCapturedAtDesc(
            TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public ForensicsArtefact get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("ForensicsArtefact " + id));
    }

    @Transactional(readOnly = true)
    public List<ForensicsArtefact> byCase(String caseCode) {
        return repo.findByOrganisationIdAndCaseCode(TenantContext.getCurrentTenant(), caseCode);
    }

    @Transactional
    public ForensicsArtefact save(ForensicsArtefact in) {
        // SEC-CYB-002 — on create force id=null so a client-supplied id can't
        // overwrite an existing row via merge; on update load the row scoped
        // to the caller's tenant and copy only mutable fields.
        Long org = TenantContext.getCurrentTenant();
        boolean isNew = in.getId() == null;
        ForensicsArtefact target;
        if (isNew) {
            in.setId(null);
            in.setOrganisationId(org);
            target = in;
        } else {
            ForensicsArtefact existing = repo.findByIdAndOrganisationId(in.getId(), org)
                .orElseThrow(() -> new EntityNotFoundException("ForensicsArtefact " + in.getId()));
            copyMutable(in, existing);
            target = existing;
        }
        ForensicsArtefact saved = repo.save(target);
        // Audit chain-of-custody on every save — this is the table whose
        // history might end up in court.
        audit.log(isNew ? "FORENSICS_CREATE" : "FORENSICS_UPDATE",
            "ForensicsArtefact", saved.getId(),
            saved.getArtefactCode() + " · " + saved.getArtefactType()
                + " · custodian=" + saved.getCurrentCustodian());
        return saved;
    }

    /** Copy client-mutable fields onto the tenant-owned managed entity. id,
     *  organisationId and audit columns are deliberately never copied. */
    private void copyMutable(ForensicsArtefact in, ForensicsArtefact target) {
        target.setArtefactCode(in.getArtefactCode());
        target.setCaseCode(in.getCaseCode());
        target.setRelatedIncidentId(in.getRelatedIncidentId());
        target.setRelatedCrisisEventId(in.getRelatedCrisisEventId());
        target.setArtefactType(in.getArtefactType());
        target.setTitleEn(in.getTitleEn());
        target.setTitleAr(in.getTitleAr());
        target.setSourceHost(in.getSourceHost());
        target.setSourceAssetId(in.getSourceAssetId());
        target.setCapturedAt(in.getCapturedAt());
        target.setCapturedByHandler(in.getCapturedByHandler());
        target.setCaptureTool(in.getCaptureTool());
        target.setCaptureMethod(in.getCaptureMethod());
        target.setSizeBytes(in.getSizeBytes());
        target.setMd5Hash(in.getMd5Hash());
        target.setSha256Hash(in.getSha256Hash());
        target.setSha512Hash(in.getSha512Hash());
        target.setIntegrityVerifiedAt(in.getIntegrityVerifiedAt());
        target.setIntegrityStatus(in.getIntegrityStatus());
        target.setCurrentCustodian(in.getCurrentCustodian());
        target.setCurrentLocation(in.getCurrentLocation());
        target.setRetentionUntil(in.getRetentionUntil());
        target.setDisposedAt(in.getDisposedAt());
        target.setDisposalMethod(in.getDisposalMethod());
        target.setLegalHold(in.getLegalHold());
        target.setPrivileged(in.getPrivileged());
        target.setNotes(in.getNotes());
    }

    @Transactional
    public ForensicsArtefact transferCustody(Long id, String newCustodian, String newLocation, String notes) {
        ForensicsArtefact a = get(id);
        String prior = a.getCurrentCustodian();
        a.setCurrentCustodian(newCustodian);
        a.setCurrentLocation(newLocation);
        ForensicsArtefact saved = repo.save(a);
        // Chain-of-custody transfer is its own audit action so the legal
        // chain can be reconstructed verbatim later.
        audit.log("FORENSICS_CUSTODY_TRANSFER", "ForensicsArtefact", id,
            "From=" + prior + " To=" + newCustodian
                + " Location=" + newLocation
                + " Notes=" + (notes == null ? "—" : notes));
        return saved;
    }

    @Transactional
    public ForensicsArtefact verifyIntegrity(Long id, boolean verified) {
        ForensicsArtefact a = get(id);
        a.setIntegrityStatus(verified ? "VERIFIED" : "CORRUPTED");
        a.setIntegrityVerifiedAt(LocalDateTime.now());
        ForensicsArtefact saved = repo.save(a);
        audit.log("FORENSICS_INTEGRITY_CHECK", "ForensicsArtefact", id,
            verified ? "Verified OK" : "INTEGRITY COMPROMISED");
        // F8 — AR-140. A failed integrity hash means the artefact is
        // unusable in court — page legal counsel + CISO immediately so the
        // chain-of-custody break is documented before any further handling.
        if (!verified) {
            publisher.publishEvent(DomainEvent.of(
                saved.getOrganisationId(), "FORENSICS_INTEGRITY_LOST",
                "ForensicsArtefact", id,
                Map.of("artefactCode",   saved.getArtefactCode()   == null ? "" : saved.getArtefactCode(),
                       "artefactType",   saved.getArtefactType()   == null ? "" : saved.getArtefactType(),
                       "caseCode",       saved.getCaseCode()       == null ? "" : saved.getCaseCode(),
                       "custodian",      saved.getCurrentCustodian() == null ? "" : saved.getCurrentCustodian())));
        }
        return saved;
    }
}
