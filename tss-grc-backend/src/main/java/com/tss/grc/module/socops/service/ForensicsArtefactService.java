package com.tss.grc.module.socops.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.ForensicsArtefact;
import com.tss.grc.module.socops.entity.ForensicsCustodyEvent;
import com.tss.grc.module.socops.repository.ForensicsArtefactRepository;
import com.tss.grc.module.socops.repository.ForensicsCustodyEventRepository;
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
    private final ForensicsCustodyEventRepository ledger;   // SOC-D append-only chain of custody
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
            // F2-331 — integrity_status / integrity_verified_at are the verifyIntegrity() attestation,
            // owned by POST /{id}/verify-integrity (which emits FORENSICS_INTEGRITY_LOST on failure and
            // audits the check). A fresh artefact has had its hash RECORDED at capture but not
            // RE-VERIFIED, so it is UNVERIFIED until that route runs. The entity initialiser was
            // "VERIFIED" (F2-238, flattering direction) — every artefact was born asserting a
            // verification that never happened, on evidence this class calls court-bound. Own the
            // correct initial state here; the create body may not assert it.
            in.setIntegrityStatus("UNVERIFIED");
            in.setIntegrityVerifiedAt(null);
            target = in;
        } else {
            ForensicsArtefact existing = repo.findByIdAndOrganisationId(in.getId(), org)
                .orElseThrow(() -> new EntityNotFoundException("ForensicsArtefact " + in.getId()));
            copyMutable(in, existing);
            target = existing;
        }
        ForensicsArtefact saved = repo.save(target);
        // SOC-D: seed the chain-of-custody ledger with the initial CAPTURE row so
        // the chain is complete from the moment of acquisition.
        if (isNew) {
            String firstHolder = saved.getCurrentCustodian() != null && !saved.getCurrentCustodian().isBlank()
                ? saved.getCurrentCustodian() : saved.getCapturedByHandler();
            appendCustodyEvent(saved, "CAPTURE", null, firstHolder, null, saved.getCurrentLocation(),
                saved.getCapturedAt(), "Artefact captured by " + saved.getCapturedByHandler());
        }
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
        // F2-331 — integrityVerifiedAt and integrityStatus are NOT copied from the request: they are
        // verifyIntegrity()'s alone. Copying them let a same-permission caller PUT a CORRUPTED
        // artefact (court evidence flagged unusable, legal + CISO paged via FORENSICS_INTEGRITY_LOST)
        // back to VERIFIED with no event and no integrity audit — a silent chain-of-custody forge.
        // F2-332 — currentCustodian / currentLocation are NOT copied from the request: they are
        // transferCustody()'s alone, which APPENDS an immutable custody-ledger TRANSFER row before
        // moving the pointer (SOC-D chain of custody; current_custodian is a denormalised pointer to
        // the latest ledger row). copyMutable runs only on the update branch, so a PUT that changed
        // the custodian moved court-bound evidence with no ledger row and no FORENSICS_CUSTODY_TRANSFER
        // audit — the F2-331 forge, one field-pair over. Create sets both from the request directly
        // (save's isNew branch) and seeds the CAPTURE ledger row from them, so create is unaffected.
        target.setRetentionUntil(in.getRetentionUntil());
        target.setDisposedAt(in.getDisposedAt());
        target.setDisposalMethod(in.getDisposalMethod());
        target.setLegalHold(in.getLegalHold());
        target.setPrivileged(in.getPrivileged());
        target.setNotes(in.getNotes());
    }

    @Transactional
    public ForensicsArtefact transferCustody(Long id, String newCustodian, String newLocation, String notes) {
        if (newCustodian == null || newCustodian.isBlank())
            throw new IllegalArgumentException("newCustodian is required for a custody transfer");
        ForensicsArtefact a = get(id);
        String prior = a.getCurrentCustodian();
        String priorLoc = a.getCurrentLocation();
        // SOC-D: APPEND an immutable custody-transfer row to the ledger BEFORE moving
        // the artefact's current-holder pointer — the chain is only ever extended,
        // never overwritten. The artefact's current_custodian is a denormalised
        // pointer to the latest ledger row.
        appendCustodyEvent(a, "TRANSFER", prior, newCustodian, priorLoc, newLocation, LocalDateTime.now(), notes);
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

    /** SOC-D — the full ordered chain of custody for an artefact (tenant-scoped). */
    @Transactional(readOnly = true)
    public List<ForensicsCustodyEvent> getCustodyChain(Long id) {
        ForensicsArtefact a = get(id);   // validates existence + tenant scope
        return ledger.findByOrganisationIdAndArtefactIdOrderBySeqNoAsc(a.getOrganisationId(), a.getId());
    }

    /** Append one immutable row to an artefact's custody ledger (next seq = max+1). */
    private ForensicsCustodyEvent appendCustodyEvent(ForensicsArtefact a, String eventType,
            String fromCustodian, String toCustodian, String fromLocation, String toLocation,
            LocalDateTime when, String notes) {
        Long org = a.getOrganisationId();
        ForensicsCustodyEvent e = new ForensicsCustodyEvent();
        e.setOrganisationId(org);
        e.setArtefactId(a.getId());
        e.setSeqNo(ledger.maxSeqNo(org, a.getId()) + 1);
        e.setEventType(eventType);
        e.setFromCustodian(fromCustodian);
        e.setToCustodian(toCustodian);
        e.setFromLocation(fromLocation);
        e.setToLocation(toLocation);
        e.setArtefactHash(a.getSha256Hash());
        e.setTransferredAt(when != null ? when : LocalDateTime.now());
        e.setActor(CurrentActor.username());
        e.setNotes(notes);
        return ledger.save(e);
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
