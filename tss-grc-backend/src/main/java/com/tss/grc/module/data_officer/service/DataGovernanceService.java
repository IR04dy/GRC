package com.tss.grc.module.data_officer.service;

import com.tss.grc.module.data_officer.dto.DataOfficerDtos.*;
import com.tss.grc.module.data_officer.entity.Entities.*;
import com.tss.grc.module.data_officer.repository.CatalogEntryRepository;
import com.tss.grc.module.data_officer.repository.CatalogLineageRepository;
import com.tss.grc.module.data_officer.repository.DisposalJobRepository;
import com.tss.grc.module.data_officer.repository.NdrRepository;
import com.tss.grc.module.data_officer.repository.QualityRuleRepository;
import com.tss.grc.module.data_officer.repository.QualityRunRepository;
import com.tss.grc.module.data_officer.repository.RetentionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data-lifecycle / governance slice of the Saudi Data Officer Toolkit —
 * Retention rules + Disposal jobs, National Data Register (NDR) submissions,
 * the data Catalog + Lineage, and data-Quality rules + runs.
 *
 * <p>Extracted from the former {@code DataOfficerService} god-class
 * (QUAL-ESG-001). The SEC-NDI-004 cross-tenant FK guards (every caller-supplied
 * FK id is re-checked against the caller's org) are preserved verbatim.
 *
 * <pre>
 *   Disposal: SCHEDULED → IN_PROGRESS → COMPLETED | FAILED; ON_HOLD ↔ SCHEDULED
 *   NDR:      DRAFT → INTERNAL_REVIEW → READY → SUBMITTED → ACCEPTED | REJECTED | REVISION_REQUESTED
 * </pre>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DataGovernanceService {

    private static final Map<String, Set<String>> DISPOSAL_ALLOWED = Map.of(
        "SCHEDULED",   Set.of("IN_PROGRESS", "ON_HOLD"),
        "IN_PROGRESS", Set.of("COMPLETED", "FAILED"),
        "ON_HOLD",     Set.of("SCHEDULED"),
        "COMPLETED",   Set.of(),
        "FAILED",      Set.of("SCHEDULED")
    );

    private static final Map<String, Set<String>> NDR_ALLOWED = Map.of(
        "DRAFT",                Set.of("INTERNAL_REVIEW"),
        "INTERNAL_REVIEW",      Set.of("READY", "DRAFT"),
        "READY",                Set.of("SUBMITTED"),
        "SUBMITTED",            Set.of("ACCEPTED", "REJECTED", "REVISION_REQUESTED"),
        "REVISION_REQUESTED",   Set.of("DRAFT"),
        "ACCEPTED",             Set.of(),
        "REJECTED",             Set.of("DRAFT")
    );

    private final RetentionRepository retentionRepo;
    private final DisposalJobRepository disposalRepo;
    private final NdrRepository ndrRepo;
    private final CatalogEntryRepository catalogRepo;
    private final CatalogLineageRepository lineageRepo;
    private final QualityRuleRepository qualityRuleRepo;
    private final QualityRunRepository qualityRunRepo;
    private final DataOfficerSupport support;

    // ════════════════════════════════════════════════════ 6. Retention

    public RetentionRuleResponse createRetentionRule(CreateRetentionRuleRequest req) {
        Long o = DataOfficerSupport.org();
        if (retentionRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("Rule " + req.reference() + " already exists");
        RetentionRule r = new RetentionRule();
        r.setOrganisationId(o);
        r.setReference(req.reference());
        r.setDataCategory(req.dataCategory());
        r.setDescriptionEn(req.descriptionEn()); r.setDescriptionAr(req.descriptionAr());
        r.setRetentionMonths(req.retentionMonths());
        r.setLegalBasisEn(req.legalBasisEn());   r.setLegalBasisAr(req.legalBasisAr());
        r.setDisposalMethod(req.disposalMethod());
        r.setReviewCadenceMonths(req.reviewCadenceMonths());
        if (req.reviewCadenceMonths() != null)
            r.setNextReviewDue(LocalDate.now().plusMonths(req.reviewCadenceMonths()));
        r.setOwnerRole(req.ownerRole());
        r.setOwnerUserId(req.ownerUserId());
        r.setLinkedAssetRef(req.linkedAssetRef());
        r.setStatus("ACTIVE");
        retentionRepo.save(r);
        support.writeTimeline("RETENTION", r.getId(), "RULE_REGISTERED", "ACTIVE",
            "Retention rule registered for " + req.dataCategory(),
            "تم تسجيل قاعدة الاحتفاظ للفئة " + req.dataCategory());
        return DataOfficerMappers.toRetention(r);
    }

    public List<RetentionRuleResponse> listRetentionRules() {
        return retentionRepo.findByOrganisationIdOrderByCreatedAtDesc(DataOfficerSupport.org()).stream()
            .map(DataOfficerMappers::toRetention).toList();
    }

    public DisposalJobResponse scheduleDisposal(CreateDisposalJobRequest req) {
        Long o = DataOfficerSupport.org();
        if (disposalRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("Disposal " + req.reference() + " already exists");
        // SEC-NDI-004: the referenced retention rule must belong to the caller's org.
        retentionRepo.findByOrganisationIdAndId(o, req.retentionRuleId())
            .orElseThrow(() -> new EntityNotFoundException("Retention rule " + req.retentionRuleId() + " not found"));
        DisposalJob j = new DisposalJob();
        j.setOrganisationId(o);
        j.setRetentionRuleId(req.retentionRuleId());
        j.setReference(req.reference());
        j.setScheduledFor(req.scheduledFor());
        j.setStatus("SCHEDULED");
        disposalRepo.save(j);
        support.writeTimeline("DISPOSAL", j.getId(), "DISPOSAL_SCHEDULED", "SCHEDULED",
            "Disposal scheduled for " + req.scheduledFor(),
            "تمت جدولة التخلص بتاريخ " + req.scheduledFor());
        return DataOfficerMappers.toDisposal(j);
    }

    public DisposalJobResponse transitionDisposal(Long id, TransitionDisposalRequest req) {
        DisposalJob j = disposalRepo.findByOrganisationIdAndId(DataOfficerSupport.org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Disposal " + id + " not found"));
        DataOfficerSupport.validate(DISPOSAL_ALLOWED, j.getStatus(), req.toStatus(), "Disposal");
        j.setStatus(req.toStatus());
        LocalDateTime now = LocalDateTime.now();
        switch (req.toStatus()) {
            case "IN_PROGRESS" -> j.setStartedAt(now);
            case "COMPLETED" -> {
                j.setCompletedAt(now);
                if (req.recordsDisposed() != null) j.setRecordsDisposed(req.recordsDisposed());
                if (req.evidenceHash() != null) j.setEvidenceHash(req.evidenceHash());
            }
            case "ON_HOLD" -> {
                if (req.legalHoldReason() != null) j.setLegalHoldReason(req.legalHoldReason());
            }
            default -> { /* FAILED / SCHEDULED */ }
        }
        disposalRepo.save(j);
        support.writeTimeline("DISPOSAL", j.getId(), "DISPOSAL_" + req.toStatus(), req.toStatus(),
            req.legalHoldReason(), null);
        return DataOfficerMappers.toDisposal(j);
    }

    public List<DisposalJobResponse> listDisposals() {
        return disposalRepo.findByOrganisationIdOrderByScheduledForDesc(DataOfficerSupport.org()).stream()
            .map(DataOfficerMappers::toDisposal).toList();
    }

    // ════════════════════════════════════════════════════ 7. NDR

    public NdrResponse createNdr(CreateNdrRequest req) {
        Long o = DataOfficerSupport.org();
        if (ndrRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("NDR " + req.reference() + " already exists");
        NdrSubmission n = new NdrSubmission();
        n.setOrganisationId(o);
        n.setReference(req.reference());
        n.setCycleYear(req.cycleYear());
        n.setTitleEn(req.titleEn()); n.setTitleAr(req.titleAr());
        n.setStatus("DRAFT");
        n.setDatasetCount(0);
        ndrRepo.save(n);
        support.writeTimeline("NDR", n.getId(), "NDR_DRAFTED", "DRAFT",
            "NDR submission cycle " + req.cycleYear() + " drafted",
            "إعداد مسوّدة تقديم السجل الوطني للبيانات " + req.cycleYear());
        return DataOfficerMappers.toNdr(n);
    }

    public NdrResponse transitionNdr(Long id, TransitionNdrRequest req) {
        NdrSubmission n = ndrRepo.findByOrganisationIdAndId(DataOfficerSupport.org(), id)
            .orElseThrow(() -> new EntityNotFoundException("NDR " + id + " not found"));
        DataOfficerSupport.validate(NDR_ALLOWED, n.getStatus(), req.toStatus(), "NDR");
        LocalDateTime now = LocalDateTime.now();
        n.setStatus(req.toStatus());
        if (req.datasetCount() != null) n.setDatasetCount(req.datasetCount());
        switch (req.toStatus()) {
            case "SUBMITTED" -> {
                n.setSubmittedAt(now);
                if (req.submissionRef() != null) n.setSubmissionRef(req.submissionRef());
            }
            case "ACCEPTED" -> {
                n.setAcceptedAt(now);
                if (req.responseSummaryEn() != null) n.setResponseSummaryEn(req.responseSummaryEn());
                if (req.responseSummaryAr() != null) n.setResponseSummaryAr(req.responseSummaryAr());
            }
            case "REJECTED", "REVISION_REQUESTED" -> {
                if (req.responseSummaryEn() != null) n.setResponseSummaryEn(req.responseSummaryEn());
                if (req.responseSummaryAr() != null) n.setResponseSummaryAr(req.responseSummaryAr());
            }
            default -> { /* INTERNAL_REVIEW / READY / DRAFT */ }
        }
        ndrRepo.save(n);
        support.writeTimeline("NDR", n.getId(), "NDR_" + req.toStatus(), req.toStatus(),
            req.responseSummaryEn(), req.responseSummaryAr());
        return DataOfficerMappers.toNdr(n);
    }

    public List<NdrResponse> listNdr() {
        return ndrRepo.findByOrganisationIdOrderByCycleYearDesc(DataOfficerSupport.org()).stream()
            .map(DataOfficerMappers::toNdr).toList();
    }

    // ════════════════════════════════════════════════════ 8. Catalog

    public CatalogEntryResponse createCatalogEntry(CreateCatalogEntryRequest req) {
        Long o = DataOfficerSupport.org();
        if (catalogRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("Catalog entry " + req.reference() + " already exists");
        CatalogEntry e = new CatalogEntry();
        e.setOrganisationId(o);
        e.setReference(req.reference());
        e.setDatasetNameEn(req.datasetNameEn()); e.setDatasetNameAr(req.datasetNameAr());
        e.setDescriptionEn(req.descriptionEn());  e.setDescriptionAr(req.descriptionAr());
        e.setDomain(req.domain());
        e.setSourceSystem(req.sourceSystem());
        e.setStorageLocation(req.storageLocation());
        e.setClassification(req.classification());
        e.setContainsPii(Boolean.TRUE.equals(req.containsPii()));
        e.setContainsSpecialCategory(Boolean.TRUE.equals(req.containsSpecialCategory()));
        e.setOwnerRole(req.ownerRole());
        e.setOwnerUserId(req.ownerUserId());
        e.setStewardUserId(req.stewardUserId());
        e.setCustodianTeam(req.custodianTeam());
        e.setRowCountApprox(req.rowCountApprox());
        e.setRefreshFrequency(req.refreshFrequency());
        catalogRepo.save(e);
        support.writeTimeline("CATALOG", e.getId(), "CATALOG_REGISTERED", null,
            "Dataset " + req.datasetNameEn() + " registered",
            "تم تسجيل مجموعة البيانات " + DataOfficerSupport.nullSafe(req.datasetNameAr()));
        return DataOfficerMappers.toCatalogEntry(e);
    }

    public List<CatalogEntryResponse> listCatalog() {
        return catalogRepo.findByOrganisationIdOrderByCreatedAtDesc(DataOfficerSupport.org()).stream()
            .map(DataOfficerMappers::toCatalogEntry).toList();
    }

    public LineageResponse createLineage(CreateLineageRequest req) {
        Long o = DataOfficerSupport.org();
        // SEC-NDI-004: validate both endpoints belong to the caller's org before
        // persisting the edge — never trust caller-supplied FK ids. A 404 on miss
        // prevents planting lineage that points at another tenant's catalog ids.
        catalogRepo.findByOrganisationIdAndId(o, req.sourceEntryId())
            .orElseThrow(() -> new EntityNotFoundException("Source catalog entry " + req.sourceEntryId() + " not found"));
        catalogRepo.findByOrganisationIdAndId(o, req.targetEntryId())
            .orElseThrow(() -> new EntityNotFoundException("Target catalog entry " + req.targetEntryId() + " not found"));
        CatalogLineage l = new CatalogLineage();
        l.setOrganisationId(o);
        l.setSourceEntryId(req.sourceEntryId());
        l.setTargetEntryId(req.targetEntryId());
        l.setTransformLabel(req.transformLabel());
        lineageRepo.save(l);
        return new LineageResponse(l.getId(), l.getSourceEntryId(), l.getTargetEntryId(), l.getTransformLabel());
    }

    public List<LineageResponse> listLineage(Long sourceId) {
        return lineageRepo.findByOrganisationIdAndSourceEntryId(DataOfficerSupport.org(), sourceId).stream()
            .map(l -> new LineageResponse(l.getId(), l.getSourceEntryId(), l.getTargetEntryId(), l.getTransformLabel()))
            .toList();
    }

    // ════════════════════════════════════════════════════ 9. Quality

    public QualityRuleResponse createQualityRule(CreateQualityRuleRequest req) {
        Long o = DataOfficerSupport.org();
        if (qualityRuleRepo.findByOrganisationIdAndReference(o, req.reference()).isPresent())
            throw new IllegalStateException("Quality rule " + req.reference() + " already exists");
        // SEC-NDI-004: the referenced catalog entry must belong to the caller's org.
        catalogRepo.findByOrganisationIdAndId(o, req.catalogEntryId())
            .orElseThrow(() -> new EntityNotFoundException("Catalog entry " + req.catalogEntryId() + " not found"));
        QualityRule r = new QualityRule();
        r.setOrganisationId(o);
        r.setReference(req.reference());
        r.setCatalogEntryId(req.catalogEntryId());
        r.setRuleNameEn(req.ruleNameEn()); r.setRuleNameAr(req.ruleNameAr());
        r.setDimension(req.dimension());
        r.setRuleExpression(req.ruleExpression());
        r.setThresholdPct(req.thresholdPct());
        r.setSeverity(req.severity());
        r.setIsActive(true);
        qualityRuleRepo.save(r);
        support.writeTimeline("QUALITY", r.getId(), "RULE_REGISTERED", null,
            "Quality rule " + req.ruleNameEn() + " (" + req.dimension() + ")",
            "قاعدة جودة بيانات " + DataOfficerSupport.nullSafe(req.ruleNameAr()));
        return DataOfficerMappers.toQualityRule(r);
    }

    public List<QualityRuleResponse> listQualityRules() {
        return qualityRuleRepo.findByOrganisationIdOrderByCreatedAtDesc(DataOfficerSupport.org()).stream()
            .map(DataOfficerMappers::toQualityRule).toList();
    }

    public QualityRunResponse recordQualityRun(RecordQualityRunRequest req) {
        Long o = DataOfficerSupport.org();
        // SEC-NDI-004: the referenced quality rule must belong to the caller's org.
        qualityRuleRepo.findByOrganisationIdAndId(o, req.qualityRuleId())
            .orElseThrow(() -> new EntityNotFoundException("Quality rule " + req.qualityRuleId() + " not found"));
        QualityRun run = new QualityRun();
        run.setOrganisationId(o);
        run.setQualityRuleId(req.qualityRuleId());
        run.setRecordsChecked(req.recordsChecked());
        run.setRecordsFailed(req.recordsFailed());
        run.setPassPct(req.passPct());
        run.setBand(req.band());
        run.setNotesEn(req.notesEn());
        run.setNotesAr(req.notesAr());
        qualityRunRepo.save(run);
        // Emit RED-band signal so the dashboard + automation engine react.
        if ("RED".equals(req.band())) {
            support.emit("QUALITY_RUN_RED", "QUALITY_RULE", req.qualityRuleId(),
                Map.of("passPct", req.passPct() == null ? "0" : req.passPct().toPlainString()));
        }
        return DataOfficerMappers.toQualityRun(run);
    }

    public List<QualityRunResponse> listQualityRuns(Long ruleId) {
        if (ruleId != null) {
            return qualityRunRepo.findByOrganisationIdAndQualityRuleIdOrderByRunAtDesc(DataOfficerSupport.org(), ruleId)
                .stream().map(DataOfficerMappers::toQualityRun).toList();
        }
        return qualityRunRepo.findByOrganisationIdOrderByRunAtDesc(DataOfficerSupport.org()).stream()
            .map(DataOfficerMappers::toQualityRun).toList();
    }
}
