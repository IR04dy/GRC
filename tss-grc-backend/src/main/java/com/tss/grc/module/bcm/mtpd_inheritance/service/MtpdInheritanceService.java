package com.tss.grc.module.bcm.mtpd_inheritance.service;

import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.entity.BiaRecord;
import com.tss.grc.module.bcm.entity.CriticalProcess;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.AssessmentDetailResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.AssessmentResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.CapaCreateRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.CapaCreateResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.ComputeRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.DependencyCreateRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.DependencyResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.DependencyUpdateRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.FindingResponse;
import com.tss.grc.module.bcm.mtpd_inheritance.dto.MtpdInheritanceDtos.PublishRequest;
import com.tss.grc.module.bcm.mtpd_inheritance.entity.MtpdInheritanceAssessment;
import com.tss.grc.module.bcm.mtpd_inheritance.entity.MtpdInheritanceFinding;
import com.tss.grc.module.bcm.mtpd_inheritance.entity.ProcessVendorDependency;
import com.tss.grc.module.bcm.mtpd_inheritance.repository.MtpdInheritanceAssessmentRepository;
import com.tss.grc.module.bcm.mtpd_inheritance.repository.MtpdInheritanceFindingRepository;
import com.tss.grc.module.bcm.mtpd_inheritance.repository.ProcessVendorDependencyRepository;
import com.tss.grc.module.bcm.repository.BiaRecordRepository;
import com.tss.grc.module.bcm.service.BiaObjectives;
import com.tss.grc.module.bcm.repository.CriticalProcessRepository;
import com.tss.grc.module.capa.dto.CapaTaskDto;
import com.tss.grc.module.capa.service.CapaTaskService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpAttestation;
import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpCampaign;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpAttestationRepository;
import com.tss.grc.module.tprm.bcp_attestation.repository.VendorBcpCampaignRepository;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Slice 10-E — Vendor MTPD inheritance compute engine.
 *
 * <p>Joins three registers — the business-process register
 * ({@link CriticalProcess} + {@link BiaRecord}), the vendor register
 * ({@link Vendor}), and the Slice 10-D vendor attestation rows
 * ({@link VendorBcpAttestation}) — and, for every REQUIRED or
 * RECOMMENDED edge in {@link ProcessVendorDependency}, classifies the
 * pair as one of:
 * <ul>
 *   <li>{@code BREACH}      — vendor attested MTPD > process MTPD</li>
 *   <li>{@code WARNING}     — vendor MTPD in [80%, 100%) of process MTPD</li>
 *   <li>{@code OK}          — vendor MTPD &lt; 80% of process MTPD</li>
 *   <li>{@code NO_ATTESTATION} — vendor has no ACCEPTED attestation; data gap</li>
 * </ul>
 *
 * <p>Severity (CRITICAL / HIGH / MEDIUM / LOW) is computed from the
 * dependency-strength axis: TIGHT-coupled processes inherit a more
 * severe label for the same MTPD variance than LOOSE-coupled ones.
 *
 * <p>Every mutation writes an F7 audit row and emits an F8 domain
 * event so the lifecycle is traceable for SAMA / NCA review.
 *
 * <h3>Design decisions documented in the slice plan</h3>
 * <ol>
 *   <li>If a REQUIRED vendor has no ACCEPTED attestation we emit a
 *       {@code NO_ATTESTATION} finding rather than silently skipping
 *       it. Auditors need to see the data gap, not have it hidden.</li>
 *   <li>WARNING threshold is 80% of process MTPD — captured in the
 *       {@code WARNING_THRESHOLD} constant so it can be tuned later
 *       without a migration.</li>
 *   <li>Process names and vendor names are denormalised onto the
 *       finding row at compute time. Subsequent renames in either
 *       register leave the historical assessment intact.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class MtpdInheritanceService {

    private static final Logger log = LoggerFactory.getLogger(MtpdInheritanceService.class);

    /** Vendor MTPD ≥ 0.80 × process MTPD (but still ≤ process MTPD) → WARNING. */
    public static final double WARNING_THRESHOLD = 0.80;

    private final ProcessVendorDependencyRepository depRepo;
    private final MtpdInheritanceAssessmentRepository assessRepo;
    private final MtpdInheritanceFindingRepository findingRepo;

    private final CriticalProcessRepository processRepo;
    private final BiaRecordRepository biaRepo;
    private final VendorRepository vendorRepo;
    private final VendorBcpAttestationRepository attestationRepo;
    private final VendorBcpCampaignRepository campaignRepo;
    private final CapaTaskService capaService;

    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Dependency CRUD ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DependencyResponse> listDependencies() {
        return depRepo.findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(org())
            .stream().map(this::toDependencyResponse).toList();
    }

    @Transactional(readOnly = true)
    public DependencyResponse getDependency(Long id) {
        ProcessVendorDependency d = depRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Dependency not found: " + id));
        return toDependencyResponse(d);
    }

    @Transactional
    public DependencyResponse createDependency(DependencyCreateRequest req) {
        Long orgId = org();
        // Validate the linked process and vendor both exist + belong to this tenant.
        CriticalProcess p = processRepo.findById(req.businessProcessId())
            .orElseThrow(() -> new EntityNotFoundException("Business process not found: " + req.businessProcessId()));
        if (!orgId.equals(p.getOrganisationId())) {
            throw new EntityNotFoundException("Business process not found: " + req.businessProcessId());
        }
        Vendor v = vendorRepo.findById(req.vendorId())
            .orElseThrow(() -> new EntityNotFoundException("Vendor not found: " + req.vendorId()));
        if (!orgId.equals(v.getOrganisationId())) {
            throw new EntityNotFoundException("Vendor not found: " + req.vendorId());
        }

        ProcessVendorDependency d = new ProcessVendorDependency();
        d.setOrganisationId(orgId);
        d.setBusinessProcessId(req.businessProcessId());
        d.setVendorId(req.vendorId());
        d.setDependencyType(req.dependencyType());
        d.setDependencyStrength(req.dependencyStrength());
        d.setNotesEn(req.notesEn());
        d.setNotesAr(req.notesAr());
        ProcessVendorDependency saved = depRepo.save(d);

        audit.log("CREATE", "ProcessVendorDependency", saved.getId(),
            "Process-vendor dependency created: process " + req.businessProcessId()
                + " ↔ vendor " + req.vendorId()
                + " (" + req.dependencyType() + "/" + req.dependencyStrength() + ")");
        emitDependencyEvent("MTPD_DEPENDENCY_CREATED", saved);
        return toDependencyResponse(saved);
    }

    @Transactional
    public DependencyResponse updateDependency(Long id, DependencyUpdateRequest req) {
        ProcessVendorDependency d = depRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Dependency not found: " + id));
        if (req.dependencyType() != null)     d.setDependencyType(req.dependencyType());
        if (req.dependencyStrength() != null) d.setDependencyStrength(req.dependencyStrength());
        if (req.notesEn() != null)            d.setNotesEn(req.notesEn());
        if (req.notesAr() != null)            d.setNotesAr(req.notesAr());
        d.setUpdatedAt(LocalDateTime.now());
        ProcessVendorDependency saved = depRepo.save(d);

        audit.log("UPDATE", "ProcessVendorDependency", saved.getId(),
            "Dependency updated: " + saved.getDependencyType() + "/" + saved.getDependencyStrength());
        emitDependencyEvent("MTPD_DEPENDENCY_UPDATED", saved);
        return toDependencyResponse(saved);
    }

    @Transactional
    public void deleteDependency(Long id) {
        ProcessVendorDependency d = depRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Dependency not found: " + id));
        d.setIsDeleted(true);
        d.setDeletedAt(LocalDateTime.now());
        depRepo.save(d);
        audit.log("DELETE", "ProcessVendorDependency", d.getId(),
            "Dependency soft-deleted: process " + d.getBusinessProcessId() + " ↔ vendor " + d.getVendorId());
        emitDependencyEvent("MTPD_DEPENDENCY_DELETED", d);
    }

    // ─── Assessment compute ─────────────────────────────────────────

    /**
     * Walk every REQUIRED + RECOMMENDED edge, join to the latest
     * ACCEPTED vendor attestation, classify, persist, and roll up.
     *
     * <p>Returns the assessment header. Detail (findings) is loaded by
     * {@link #getAssessmentDetail(Long)}.
     */
    @Transactional
    public AssessmentResponse computeAssessment(ComputeRequest req) {
        Long orgId = org();
        LocalDateTime now = LocalDateTime.now();

        // 1. Build header row.
        MtpdInheritanceAssessment header = new MtpdInheritanceAssessment();
        header.setOrganisationId(orgId);
        header.setAssessmentCode(generateAssessmentCode(orgId, req.cycleYear(), req.cycleQuarter()));
        header.setCycleYear(req.cycleYear());
        header.setCycleQuarter(req.cycleQuarter() == null || req.cycleQuarter().isBlank() ? null : req.cycleQuarter());
        header.setComputedAt(now);
        header.setNotesEn(req.notesEn());
        header.setNotesAr(req.notesAr());
        header = assessRepo.save(header);

        // 2. Load all REQUIRED + RECOMMENDED edges. FALLBACK is skipped —
        //    the design decision is that fallback vendors don't count
        //    against the primary process MTPD because they're only
        //    engaged after primary failure.
        List<ProcessVendorDependency> edges = depRepo
            .findByOrganisationIdAndDependencyTypeInAndIsDeletedFalse(orgId, List.of("REQUIRED", "RECOMMENDED"));

        // 3. Preload all referenced processes + their MTPDs (latest BIA per process).
        Map<Long, CriticalProcess> processByid = new HashMap<>();
        Map<Long, Integer> mtpdByProcess = new HashMap<>();
        for (ProcessVendorDependency e : edges) {
            if (!processByid.containsKey(e.getBusinessProcessId())) {
                processRepo.findById(e.getBusinessProcessId())
                    .filter(p -> orgId.equals(p.getOrganisationId()))
                    .ifPresent(p -> processByid.put(p.getId(), p));
            }
        }
        // F2-282: this was the OUTLIER of three hand-rolled copies. It picked the
        // most recent BIA by assessmentDate where the cascade and the board pack both
        // pick the lowest MTPD, and it filtered nulls only AFTER selecting — so when
        // the latest record left MTPD blank this process lost its MTPD entirely even
        // though an older record stated one, and the vendor gap silently became
        // unmeasurable. All three now ask the same predicate. Verified: the two rules
        // coincide on every live row in dev and prod, so this changes no finding
        // today; what it removes is the divergence.
        // (The old comment here said the entity "has no @Where so we filter
        // manually" — F2-279 added @Where(is_deleted = 0), and the manual filter it
        // described never existed.)
        if (!processByid.isEmpty()) {
            BiaObjectives.authoritativeByProcess(
                    biaRepo.findByProcessIdIn(new ArrayList<>(processByid.keySet())))
                .forEach((pid, bia) -> {
                    if (bia.getMtpdHours() != null) mtpdByProcess.put(pid, bia.getMtpdHours());
                });
        }

        // 4. Preload vendors + latest ACCEPTED attestation per vendor.
        Map<Long, Vendor> vendorById = new HashMap<>();
        Map<Long, VendorBcpAttestation> latestAcceptedByVendor = new HashMap<>();
        Map<Long, VendorBcpCampaign> campaignById = new HashMap<>();
        for (ProcessVendorDependency e : edges) {
            if (!vendorById.containsKey(e.getVendorId())) {
                vendorRepo.findById(e.getVendorId())
                    .filter(v -> orgId.equals(v.getOrganisationId()))
                    .ifPresent(v -> vendorById.put(v.getId(), v));
            }
        }
        // For each vendor, walk all attestations and find the most recent
        // ACCEPTED one. (Vendor BCP attestation repo has no direct latest-
        // by-vendor finder; we walk org-scoped attestations once.)
        // We could add an index-backed finder later — for v1 this is fine:
        // attestations are <1k rows per org in practice.
        for (Long vendorId : vendorById.keySet()) {
            // Walk all campaigns + status=ACCEPTED for this vendor by checking
            // each campaign's attestations. We instead use a broader find:
            // load all org-scoped attestations and filter in-memory.
        }
        // Single batched lookup: all attestations for the org, then group.
        // Strategy: walk all known campaigns once (org-scoped) and collect.
        for (Long vendorId : vendorById.keySet()) {
            // Empty placeholder — the next loop does the real work.
        }
        // Walk org-scoped campaigns, then their attestations.
        List<VendorBcpCampaign> allCampaigns = campaignRepo
            .findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(orgId);
        for (VendorBcpCampaign c : allCampaigns) {
            campaignById.put(c.getId(), c);
            List<VendorBcpAttestation> accepted = attestationRepo
                .findByOrganisationIdAndCampaignIdAndStatusAndIsDeletedFalseOrderByCreatedAtAsc(
                    orgId, c.getId(), "ACCEPTED");
            for (VendorBcpAttestation a : accepted) {
                VendorBcpAttestation existing = latestAcceptedByVendor.get(a.getVendorId());
                if (existing == null) {
                    latestAcceptedByVendor.put(a.getVendorId(), a);
                    continue;
                }
                LocalDateTime existingTs = existing.getSubmittedAt() != null
                    ? existing.getSubmittedAt() : existing.getCreatedAt();
                LocalDateTime aTs = a.getSubmittedAt() != null ? a.getSubmittedAt() : a.getCreatedAt();
                if (aTs != null && (existingTs == null || aTs.isAfter(existingTs))) {
                    latestAcceptedByVendor.put(a.getVendorId(), a);
                }
            }
        }

        // 5. Per-edge classification + persistence.
        int totalBreach = 0, totalWarn = 0, totalOk = 0, totalNoAtt = 0;
        for (ProcessVendorDependency e : edges) {
            CriticalProcess proc = processByid.get(e.getBusinessProcessId());
            Vendor vend = vendorById.get(e.getVendorId());
            if (proc == null || vend == null) {
                // Dangling reference — skip but log.
                log.warn("[mtpd-inh] dangling dependency org={} dep={} (proc={}, vendor={})",
                    orgId, e.getId(), e.getBusinessProcessId(), e.getVendorId());
                continue;
            }

            Integer processMtpd = mtpdByProcess.get(proc.getId());      // may be null
            VendorBcpAttestation att = latestAcceptedByVendor.get(vend.getId()); // may be null
            Integer vendorMtpd = att != null ? att.getMtpdHoursAttested() : null;

            String findingType;
            Integer variance = null;
            if (att == null || vendorMtpd == null) {
                // No accepted attestation OR attestation present but MTPD blank.
                findingType = "NO_ATTESTATION";
            } else if (processMtpd == null) {
                // We can't compute inheritance without the process MTPD;
                // treat as NO_ATTESTATION (data gap) on the bank side.
                findingType = "NO_ATTESTATION";
            } else {
                variance = vendorMtpd - processMtpd;
                if (variance > 0) {
                    findingType = "BREACH";
                } else {
                    double ratio = (double) vendorMtpd / (double) processMtpd;
                    if (ratio >= WARNING_THRESHOLD) {
                        findingType = "WARNING";
                    } else {
                        findingType = "OK";
                    }
                }
            }

            String severity = computeSeverity(findingType, e.getDependencyStrength());
            String[] recs = buildRecommendation(findingType, severity, e.getDependencyStrength(),
                proc, vend, processMtpd, vendorMtpd);

            MtpdInheritanceFinding f = new MtpdInheritanceFinding();
            f.setOrganisationId(orgId);
            f.setAssessmentId(header.getId());
            f.setBusinessProcessId(proc.getId());
            f.setBusinessProcessNameEn(proc.getProcessNameEn());
            f.setBusinessProcessNameAr(proc.getProcessNameAr());
            f.setVendorId(vend.getId());
            f.setVendorName(vend.getVendorName());
            f.setProcessMtpdHours(processMtpd);
            f.setVendorAttestedMtpdHours(vendorMtpd);
            f.setVarianceHours(variance);
            f.setFindingType(findingType);
            f.setSeverity(severity);
            f.setDependencyStrength(e.getDependencyStrength());
            f.setDependencyType(e.getDependencyType());
            f.setRecommendationEn(recs[0]);
            f.setRecommendationAr(recs[1]);
            if (att != null) {
                f.setSourceAttestationId(att.getId());
                f.setSourceSubmittedAt(att.getSubmittedAt());
                VendorBcpCampaign srcCampaign = campaignById.get(att.getCampaignId());
                if (srcCampaign != null) f.setSourceCampaignCode(srcCampaign.getCampaignCode());
            }
            findingRepo.save(f);

            switch (findingType) {
                case "BREACH"         -> totalBreach++;
                case "WARNING"        -> totalWarn++;
                case "OK"             -> totalOk++;
                case "NO_ATTESTATION" -> totalNoAtt++;
                default               -> { /* unreachable */ }
            }
        }

        // 6. Header roll-ups.
        header.setTotalProcesses(processByid.size());
        header.setTotalBreaches(totalBreach);
        header.setTotalWarnings(totalWarn);
        header.setTotalOk(totalOk);
        header.setTotalNoAttestation(totalNoAtt);
        header.setUpdatedAt(LocalDateTime.now());
        MtpdInheritanceAssessment saved = assessRepo.save(header);

        audit.log("COMPUTE", "MtpdInheritanceAssessment", saved.getId(),
            "MTPD inheritance assessment " + saved.getAssessmentCode()
                + " computed: " + processByid.size() + " processes scanned, "
                + totalBreach + " breach(es), " + totalWarn + " warning(s), "
                + totalOk + " ok, " + totalNoAtt + " no-attestation");
        emitAssessmentEvent("MTPD_ASSESSMENT_COMPUTED", saved);
        return toAssessmentResponse(saved);
    }

    // ─── Publish + reads ────────────────────────────────────────────

    @Transactional
    public AssessmentResponse publishAssessment(Long assessmentId, PublishRequest req) {
        MtpdInheritanceAssessment a = assessRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), assessmentId)
            .orElseThrow(() -> new EntityNotFoundException("Assessment not found: " + assessmentId));
        if (Boolean.TRUE.equals(a.getIsPublished())) {
            // Idempotent — return existing without re-publish.
            return toAssessmentResponse(a);
        }
        LocalDateTime now = LocalDateTime.now();
        a.setIsPublished(true);
        a.setPublishedAt(now);
        // F2-224 — publishing an MTPD inheritance assessment for audit/board is an act, and the
        // server stamps publishedAt from its own clock one line above. Whoever released these
        // figures to the audit committee is the authenticated principal, not a body field.
        a.setPublishedByUserId(CurrentActor.userId());
        if (req != null) {
            if (req.notesEn() != null) a.setNotesEn(req.notesEn());
            if (req.notesAr() != null) a.setNotesAr(req.notesAr());
        }
        a.setUpdatedAt(now);
        MtpdInheritanceAssessment saved = assessRepo.save(a);

        audit.log("PUBLISH", "MtpdInheritanceAssessment", saved.getId(),
            "MTPD inheritance assessment " + saved.getAssessmentCode() + " published for audit/board.");
        emitAssessmentEvent("MTPD_ASSESSMENT_PUBLISHED", saved);
        return toAssessmentResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> listAssessments() {
        return assessRepo.findByOrganisationIdAndIsDeletedFalseOrderByComputedAtDesc(org())
            .stream().map(this::toAssessmentResponse).toList();
    }

    @Transactional(readOnly = true)
    public AssessmentDetailResponse getAssessmentDetail(Long assessmentId) {
        Long orgId = org();
        MtpdInheritanceAssessment a = assessRepo.findByOrganisationIdAndIdAndIsDeletedFalse(orgId, assessmentId)
            .orElseThrow(() -> new EntityNotFoundException("Assessment not found: " + assessmentId));
        List<FindingResponse> findings = findingRepo
            .findByOrganisationIdAndAssessmentIdAndIsDeletedFalseOrderBySeverityAscBusinessProcessNameEnAsc(orgId, assessmentId)
            .stream()
            // Severity ordering: CRITICAL > HIGH > MEDIUM > LOW.
            .sorted(Comparator.comparingInt((MtpdInheritanceFinding f) -> severityRank(f.getSeverity()))
                .thenComparing(MtpdInheritanceFinding::getBusinessProcessNameEn,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .map(this::toFindingResponse)
            .toList();
        return new AssessmentDetailResponse(toAssessmentResponse(a), findings);
    }

    @Transactional(readOnly = true)
    public List<FindingResponse> getProcessBreachHistory(Long processId) {
        return findingRepo
            .findByOrganisationIdAndBusinessProcessIdAndIsDeletedFalseOrderByCreatedAtDesc(org(), processId)
            .stream().map(this::toFindingResponse).toList();
    }

    // ─── CAPA ──────────────────────────────────────────────────────

    @Transactional
    public CapaCreateResponse createCapaForFinding(Long findingId, CapaCreateRequest req) {
        MtpdInheritanceFinding f = findingRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), findingId)
            .orElseThrow(() -> new EntityNotFoundException("Finding not found: " + findingId));
        if (f.getCapaTaskId() != null) {
            throw new IllegalStateException("CAPA already exists for finding " + findingId
                + " (taskId=" + f.getCapaTaskId() + ")");
        }
        // Decide severity — use override if provided, else map from finding severity.
        String severity = (req != null && req.severityOverride() != null && !req.severityOverride().isBlank())
            ? req.severityOverride()
            : mapSeverityToCapa(f.getSeverity());

        // Build a CAPA task DTO and call the existing service. originType =
        // "bcm.mtpd_finding" so the audit timeline + automation engine can
        // route on it.
        String titleEn = "MTPD inheritance " + f.getFindingType() + " — "
            + f.getBusinessProcessNameEn() + " ↔ " + f.getVendorName();
        String titleAr = "وراثة المهلة القصوى — " + f.getFindingType() + " — "
            + (f.getBusinessProcessNameAr() != null ? f.getBusinessProcessNameAr() : f.getBusinessProcessNameEn())
            + " ↔ " + f.getVendorName();
        String descEn = (f.getRecommendationEn() != null ? f.getRecommendationEn() : "")
            + (req != null && req.notesEn() != null ? "\n\n" + req.notesEn() : "");
        String descAr = (f.getRecommendationAr() != null ? f.getRecommendationAr() : "")
            + (req != null && req.notesAr() != null ? "\n\n" + req.notesAr() : "");

        CapaTaskDto dto = CapaTaskDto.builder()
            .originType("bcm.mtpd_finding")
            .originId(String.valueOf(f.getId()))
            .titleEn(titleEn)
            .titleAr(titleAr)
            .descriptionEn(descEn)
            .descriptionAr(descAr)
            .capaType("CORRECTIVE")
            .severity(severity)
            .owner(req != null && req.owner() != null ? req.owner() : "BCM Manager")
            .targetDate(LocalDate.now().plusDays(defaultTargetDays(severity)))
            .build();
        CapaTaskDto created = capaService.create(dto);

        LocalDateTime now = LocalDateTime.now();
        f.setCapaTaskId(created.getNumericId());
        f.setCapaExternalId(created.getId());
        f.setCapaCreatedAt(now);
        f.setUpdatedAt(now);
        MtpdInheritanceFinding saved = findingRepo.save(f);

        audit.log("CAPA_CREATED", "MtpdInheritanceFinding", saved.getId(),
            "CAPA " + created.getId() + " created for MTPD inheritance "
                + saved.getFindingType() + " finding: " + saved.getBusinessProcessNameEn()
                + " ↔ " + saved.getVendorName() + " (severity=" + severity + ")");
        Map<String, Object> payload = new HashMap<>();
        payload.put("findingId", String.valueOf(saved.getId()));
        payload.put("capaTaskId", String.valueOf(saved.getCapaTaskId()));
        payload.put("capaExternalId", saved.getCapaExternalId());
        payload.put("findingType", saved.getFindingType());
        payload.put("severity", saved.getSeverity());
        publisher.publishEvent(new DomainEvent(
            saved.getOrganisationId(), "MTPD_FINDING_CAPA_CREATED",
            "MtpdInheritanceFinding", saved.getId(),
            "mtpd-finding:" + saved.getId(), payload));
        return new CapaCreateResponse(saved.getId(), saved.getCapaTaskId(),
            saved.getCapaExternalId(), saved.getCapaCreatedAt());
    }

    // ─── Classification helpers (package-visible for tests) ─────────

    static String computeSeverity(String findingType, String strength) {
        return switch (findingType) {
            case "BREACH" -> switch (strength) {
                case "TIGHT"     -> "CRITICAL";
                case "LOOSE"     -> "HIGH";
                case "TRANSIENT" -> "MEDIUM";
                default          -> "MEDIUM";
            };
            case "WARNING" -> switch (strength) {
                case "TIGHT"     -> "HIGH";
                case "LOOSE"     -> "MEDIUM";
                case "TRANSIENT" -> "LOW";
                default          -> "MEDIUM";
            };
            case "NO_ATTESTATION" -> switch (strength) {
                case "TIGHT"     -> "HIGH";
                case "LOOSE"     -> "MEDIUM";
                case "TRANSIENT" -> "LOW";
                default          -> "MEDIUM";
            };
            case "OK" -> "LOW";
            default   -> "LOW";
        };
    }

    /**
     * Build the bilingual recommendation text. Templates reference the
     * relevant SAMA BCF / ISO 22301 clauses so the regulator audit
     * trail is self-contained.
     */
    static String[] buildRecommendation(String findingType, String severity, String strength,
                                        CriticalProcess proc, Vendor vend,
                                        Integer processMtpd, Integer vendorMtpd) {
        String procName = proc != null ? proc.getProcessNameEn() : "(process)";
        String vendName = vend != null ? vend.getVendorName() : "(vendor)";
        String pAr = proc != null && proc.getProcessNameAr() != null ? proc.getProcessNameAr() : procName;
        String vAr = vend != null && vend.getVendorNameAr() != null ? vend.getVendorNameAr() : vendName;

        String en, ar;
        switch (findingType) {
            case "BREACH" -> {
                en = "BREACH (severity " + severity + "): vendor '" + vendName + "' attested MTPD of "
                    + vendorMtpd + "h exceeds the bank's own MTPD of " + processMtpd + "h for process '" + procName + "'. "
                    + "Per SAMA BCF §3.4 and ISO 22301 §8.2, the bank's own MTPD must reflect the longest "
                    + "third-party recovery objective on the critical path. Either (a) renegotiate the vendor SLA "
                    + "to attain MTPD ≤ " + processMtpd + "h, (b) implement a TIGHT/LOOSE alternative vendor, "
                    + "or (c) re-baseline the process MTPD with Risk Committee approval and update the BIA.";
                ar = "خرق (الخطورة " + severity + "): المهلة القصوى للاضطراب المُقَرّ بها من المورّد «" + vAr
                    + "» وهي " + vendorMtpd + " ساعة تتجاوز المهلة القصوى للبنك البالغة " + processMtpd
                    + " ساعة للعملية «" + pAr + "». وفقاً للمادة §3.4 من إطار استمرارية الأعمال (مؤسسة النقد) "
                    + "والبند §8.2 من المواصفة ISO 22301، يجب أن تعكس مهلة البنك أطول مهلة استرداد لطرف خارجي "
                    + "على المسار الحرج. يُوصى بأحد الخيارات: (أ) إعادة التفاوض على اتفاقية مستوى الخدمة لخفض "
                    + "المهلة إلى " + processMtpd + " ساعة أو أقل، (ب) تفعيل مورّد بديل، (ج) إعادة معايرة "
                    + "مهلة العملية بعد اعتماد لجنة المخاطر وتحديث تحليل الأثر.";
            }
            case "WARNING" -> {
                en = "WARNING (severity " + severity + "): vendor '" + vendName + "' attested MTPD of "
                    + vendorMtpd + "h is within 20% of the process MTPD of " + processMtpd + "h for '" + procName + "'. "
                    + "No mathematical breach today, but headroom is thin. Review the dependency at the next BIA cycle "
                    + "(SAMA BCF §3.4); confirm the vendor's recovery test results substantiate the attested value.";
                ar = "تحذير (الخطورة " + severity + "): المهلة المُقَرّة من المورّد «" + vAr + "» وهي "
                    + vendorMtpd + " ساعة تقع ضمن نطاق 20% من مهلة العملية «" + pAr + "» البالغة "
                    + processMtpd + " ساعة. لا يوجد خرق رياضي حالياً، لكن الفجوة محدودة. يُوصى بمراجعة هذا "
                    + "الاعتماد في الدورة القادمة لتحليل أثر الأعمال (المادة §3.4 من إطار مؤسسة النقد)، "
                    + "والتحقق من نتائج اختبارات استرداد المورّد لإثبات القيمة المُقَرّة.";
            }
            case "NO_ATTESTATION" -> {
                en = "NO ATTESTATION (severity " + severity + "): vendor '" + vendName + "' has no ACCEPTED BCP attestation on file "
                    + "for process '" + procName + "'. Per NCA ECC-2 control 4-2-3, third-party BCM posture must be "
                    + "documented for all REQUIRED dependencies. Either onboard the vendor into the next attestation "
                    + "campaign (Slice 10-D), accept the risk in writing with Risk Committee sign-off, or reclassify the "
                    + "dependency to FALLBACK if a primary alternative exists.";
                ar = "غياب الإقرار (الخطورة " + severity + "): لا يوجد إقرار خطة استمرارية أعمال مُعتمَد من المورّد «"
                    + vAr + "» للعملية «" + pAr + "». وفقاً للضابط 4-2-3 من ضوابط الأمن السيبراني الأساسية (الهيئة "
                    + "الوطنية للأمن السيبراني)، يجب توثيق وضعية استمرارية الأعمال لكل طرف خارجي يُصنَّف اعتماداً "
                    + "ضرورياً. يُوصى بإدراج المورّد في حملة الإقرار القادمة، أو قبول الخطر خطياً باعتماد لجنة "
                    + "المخاطر، أو إعادة تصنيف الاعتماد إلى «احتياطي» إذا توفّر بديل أساسي.";
            }
            default -> { // OK
                en = "OK: vendor '" + vendName + "' attested MTPD of " + vendorMtpd
                    + "h provides sufficient headroom against the process MTPD of " + processMtpd + "h.";
                ar = "ضمن النطاق: المهلة المُقَرّة من المورّد «" + vAr + "» (" + vendorMtpd + " ساعة) توفّر هامشاً كافياً "
                    + "مقارنة بمهلة العملية «" + pAr + "» (" + processMtpd + " ساعة).";
            }
        }
        return new String[] { en, ar };
    }

    /** Lower rank = more urgent — used by the detail-page sort. */
    static int severityRank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "CRITICAL" -> 0;
            case "HIGH"     -> 1;
            case "MEDIUM"   -> 2;
            case "LOW"      -> 3;
            default         -> 4;
        };
    }

    /** Map finding severity to CAPA severity vocabulary. */
    static String mapSeverityToCapa(String s) {
        return switch (s == null ? "" : s) {
            case "CRITICAL" -> "HIGH";   // CAPA register tops out at HIGH
            case "HIGH"     -> "HIGH";
            case "MEDIUM"   -> "MEDIUM";
            default          -> "LOW";
        };
    }

    static int defaultTargetDays(String severity) {
        return switch (severity == null ? "" : severity) {
            case "HIGH"   -> 30;
            case "MEDIUM" -> 60;
            default        -> 90;
        };
    }

    // ─── Code generation ────────────────────────────────────────────

    private String generateAssessmentCode(Long orgId, Integer year, String quarter) {
        long ordinal = assessRepo.countByOrganisationIdAndCycleYearAndIsDeletedFalse(orgId, year) + 1L;
        String q = (quarter == null || quarter.isBlank()) ? "ANN" : quarter;
        // MTPD-INH-<year>-<quarter>-<seq>
        String candidate = String.format(Locale.ROOT, "MTPD-INH-%d-%s-%02d", year, q, ordinal);
        // Defensive guard against rare unique-index collisions on rapid double-fire.
        int attempt = 0;
        while (assessRepo.findByOrganisationIdAndAssessmentCodeAndIsDeletedFalse(orgId, candidate).isPresent()
                && attempt < 5) {
            attempt++;
            ordinal++;
            candidate = String.format(Locale.ROOT, "MTPD-INH-%d-%s-%02d", year, q, ordinal);
        }
        return candidate;
    }

    // ─── Mappers ────────────────────────────────────────────────────

    private DependencyResponse toDependencyResponse(ProcessVendorDependency d) {
        // Fetch names for friendly UI render. Misses are tolerated — names
        // may be null if the upstream row was hard-deleted (unsupported but
        // defensively handled).
        Optional<CriticalProcess> p = processRepo.findById(d.getBusinessProcessId())
            .filter(x -> d.getOrganisationId().equals(x.getOrganisationId()));
        Optional<Vendor> v = vendorRepo.findById(d.getVendorId())
            .filter(x -> d.getOrganisationId().equals(x.getOrganisationId()));
        return new DependencyResponse(
            d.getId(), d.getExternalId(), d.getOrganisationId(),
            d.getBusinessProcessId(),
            p.map(CriticalProcess::getProcessNameEn).orElse(null),
            p.map(CriticalProcess::getProcessNameAr).orElse(null),
            d.getVendorId(),
            v.map(Vendor::getVendorName).orElse(null),
            v.map(Vendor::getVendorNameAr).orElse(null),
            d.getDependencyType(), d.getDependencyStrength(),
            d.getNotesEn(), d.getNotesAr(),
            d.getCreatedAt(), d.getUpdatedAt()
        );
    }

    private AssessmentResponse toAssessmentResponse(MtpdInheritanceAssessment a) {
        return new AssessmentResponse(
            a.getId(), a.getExternalId(), a.getOrganisationId(),
            a.getAssessmentCode(), a.getCycleYear(), a.getCycleQuarter(),
            a.getComputedAt(), a.getComputedByUserId(),
            a.getTotalProcesses(), a.getTotalBreaches(),
            a.getTotalWarnings(), a.getTotalOk(), a.getTotalNoAttestation(),
            a.getIsPublished(), a.getPublishedAt(), a.getPublishedByUserId(),
            a.getNotesEn(), a.getNotesAr(),
            a.getCreatedAt(), a.getUpdatedAt()
        );
    }

    private FindingResponse toFindingResponse(MtpdInheritanceFinding f) {
        return new FindingResponse(
            f.getId(), f.getExternalId(), f.getOrganisationId(),
            f.getAssessmentId(),
            f.getBusinessProcessId(), f.getBusinessProcessNameEn(), f.getBusinessProcessNameAr(),
            f.getVendorId(), f.getVendorName(),
            f.getProcessMtpdHours(), f.getVendorAttestedMtpdHours(), f.getVarianceHours(),
            f.getFindingType(), f.getSeverity(),
            f.getDependencyStrength(), f.getDependencyType(),
            f.getRecommendationEn(), f.getRecommendationAr(),
            f.getCapaTaskId(), f.getCapaExternalId(), f.getCapaCreatedAt(),
            f.getSourceAttestationId(), f.getSourceCampaignCode(), f.getSourceSubmittedAt(),
            f.getCreatedAt()
        );
    }

    private void emitDependencyEvent(String trigger, ProcessVendorDependency d) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("dependencyId", String.valueOf(d.getId()));
        payload.put("businessProcessId", String.valueOf(d.getBusinessProcessId()));
        payload.put("vendorId", String.valueOf(d.getVendorId()));
        payload.put("dependencyType", d.getDependencyType());
        payload.put("dependencyStrength", d.getDependencyStrength());
        publisher.publishEvent(new DomainEvent(
            d.getOrganisationId(), trigger, "ProcessVendorDependency", d.getId(),
            "pvdep:" + d.getId(), payload));
    }

    private void emitAssessmentEvent(String trigger, MtpdInheritanceAssessment a) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("assessmentId", String.valueOf(a.getId()));
        payload.put("assessmentCode", a.getAssessmentCode());
        payload.put("cycleYear", String.valueOf(a.getCycleYear()));
        payload.put("cycleQuarter", a.getCycleQuarter() == null ? "" : a.getCycleQuarter());
        payload.put("totalProcesses", String.valueOf(a.getTotalProcesses()));
        payload.put("totalBreaches", String.valueOf(a.getTotalBreaches()));
        payload.put("totalWarnings", String.valueOf(a.getTotalWarnings()));
        payload.put("totalNoAttestation", String.valueOf(a.getTotalNoAttestation()));
        payload.put("isPublished", String.valueOf(Boolean.TRUE.equals(a.getIsPublished())));
        publisher.publishEvent(new DomainEvent(
            a.getOrganisationId(), trigger, "MtpdInheritanceAssessment", a.getId(),
            "mtpd-assess:" + a.getId(), payload));
    }
}
