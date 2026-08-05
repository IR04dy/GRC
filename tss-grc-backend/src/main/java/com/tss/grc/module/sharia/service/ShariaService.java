package com.tss.grc.module.sharia.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.sharia.dto.ShariaDtos.AuditFindingResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.CreateResolutionRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.CreateScholarRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.DashboardResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.DeclareCoiRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.DetectNcEventRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.EventResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.NcEventResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.ProductResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.PurifyNcEventRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.RaiseAuditFindingRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.ResolutionResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.ScholarResponse;
import com.tss.grc.module.sharia.dto.ShariaDtos.SubmitProductRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.TransitionAuditFindingRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.TransitionNcEventRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.TransitionProductRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.TransitionResolutionRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.UpdateScholarRequest;
import com.tss.grc.module.sharia.entity.ShariaAuditFinding;
import com.tss.grc.module.sharia.entity.ShariaEvent;
import com.tss.grc.module.sharia.entity.ShariaNonComplianceEvent;
import com.tss.grc.module.sharia.entity.ShariaProductApproval;
import com.tss.grc.module.sharia.entity.ShariaResolution;
import com.tss.grc.module.sharia.entity.ShariaScholar;
import com.tss.grc.module.sharia.repository.ShariaAuditFindingRepository;
import com.tss.grc.module.sharia.repository.ShariaEventRepository;
import com.tss.grc.module.sharia.repository.ShariaNonComplianceEventRepository;
import com.tss.grc.module.sharia.repository.ShariaProductApprovalRepository;
import com.tss.grc.module.sharia.repository.ShariaResolutionRepository;
import com.tss.grc.module.sharia.repository.ShariaScholarRepository;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sharia Governance &amp; Audit service — Sprint 20 PR #1.
 *
 * <p>State machines:
 * <pre>
 *   Resolution:  DRAFT → SSB_REVIEW → APPROVED → ACTIVE → SUPERSEDED
 *                                    → REJECTED
 *
 *   AuditFinding: OPEN → REMEDIATION → VERIFIED → CLOSED
 *
 *   NcEvent:      DETECTED → INVESTIGATION → INCOME_PURIFIED → CLOSED
 *
 *   Product:      SUBMITTED → SSB_REVIEW → APPROVED_PROVISIONAL →
 *                 APPROVED_FINAL → LAUNCHED → DECOMMISSIONED
 *                                    → REJECTED
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class ShariaService {

    private static final Map<String, Set<String>> RESOLUTION_ALLOWED = Map.of(
            "DRAFT",       Set.of("SSB_REVIEW", "REJECTED"),
            "SSB_REVIEW",  Set.of("APPROVED", "REJECTED"),
            "APPROVED",    Set.of("ACTIVE", "REJECTED"),
            "ACTIVE",      Set.of("SUPERSEDED"),
            "SUPERSEDED",  Set.of(),
            "REJECTED",    Set.of());

    private static final Map<String, Set<String>> FINDING_ALLOWED = Map.of(
            "OPEN",        Set.of("REMEDIATION", "VERIFIED"),
            "REMEDIATION", Set.of("VERIFIED"),
            "VERIFIED",    Set.of("CLOSED"),
            "CLOSED",      Set.of());

    private static final Map<String, Set<String>> NC_ALLOWED = Map.of(
            "DETECTED",        Set.of("INVESTIGATION"),
            "INVESTIGATION",   Set.of("INCOME_PURIFIED"),
            "INCOME_PURIFIED", Set.of("CLOSED"),
            "CLOSED",          Set.of());

    private static final Map<String, Set<String>> PRODUCT_ALLOWED = Map.of(
            "SUBMITTED",            Set.of("SSB_REVIEW", "REJECTED"),
            "SSB_REVIEW",           Set.of("APPROVED_PROVISIONAL", "APPROVED_FINAL", "REJECTED"),
            "APPROVED_PROVISIONAL", Set.of("APPROVED_FINAL", "REJECTED"),
            "APPROVED_FINAL",       Set.of("LAUNCHED", "REJECTED"),
            "LAUNCHED",             Set.of("DECOMMISSIONED"),
            "DECOMMISSIONED",       Set.of(),
            "REJECTED",             Set.of());

    private final ShariaScholarRepository scholarRepo;
    private final ShariaResolutionRepository resolutionRepo;
    private final ShariaAuditFindingRepository findingRepo;
    private final ShariaNonComplianceEventRepository ncRepo;
    private final ShariaProductApprovalRepository productRepo;
    private final ShariaEventRepository eventRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    // ─── Scholars ──────────────────────────────────────────────────

    @Transactional
    public ScholarResponse createScholar(CreateScholarRequest req) {
        scholarRepo.findByReferenceAndOrganisationId(req.getReference(), org())
                .ifPresent(x -> {
                    throw new IllegalStateException(
                            "Scholar " + req.getReference() + " already exists");
                });
        ShariaScholar s = new ShariaScholar();
        s.setOrganisationId(org());
        s.setReference(req.getReference());
        s.setFullNameEn(req.getFullNameEn());
        s.setFullNameAr(req.getFullNameAr());
        if (req.getSsbRole() != null) s.setSsbRole(req.getSsbRole());
        s.setTermStart(req.getTermStart());
        s.setTermEnd(req.getTermEnd());
        s.setCredentialsEn(req.getCredentialsEn());
        s.setCredentialsAr(req.getCredentialsAr());
        s.setAnnualFeeSar(req.getAnnualFeeSar());
        s.setEmail(req.getEmail());
        s.setPhone(req.getPhone());
        ShariaScholar saved = scholarRepo.save(s);
        audit.log("SHARIA_SCHOLAR_REGISTERED", "ShariaScholar", saved.getId(),
                "Scholar " + saved.getReference() + " (" + saved.getSsbRole() + ") joined the SSB");
        timeline(saved.getId(), null, null, null, null,
                "SCHOLAR_REGISTERED", saved.getStatus(),
                "Scholar " + saved.getReference() + " joined the SSB",
                "انضمام العالم " + saved.getReference() + " إلى الهيئة الشرعية");
        return toScholar(saved);
    }

    @Transactional
    public ScholarResponse updateScholar(Long id, UpdateScholarRequest req) {
        ShariaScholar s = loadScholar(id);
        if (req.getFullNameEn() != null) s.setFullNameEn(req.getFullNameEn());
        if (req.getFullNameAr() != null) s.setFullNameAr(req.getFullNameAr());
        if (req.getSsbRole() != null) s.setSsbRole(req.getSsbRole());
        if (req.getStatus() != null) s.setStatus(req.getStatus());
        if (req.getTermStart() != null) s.setTermStart(req.getTermStart());
        if (req.getTermEnd() != null) s.setTermEnd(req.getTermEnd());
        if (req.getCredentialsEn() != null) s.setCredentialsEn(req.getCredentialsEn());
        if (req.getCredentialsAr() != null) s.setCredentialsAr(req.getCredentialsAr());
        if (req.getAnnualFeeSar() != null) s.setAnnualFeeSar(req.getAnnualFeeSar());
        if (req.getEmail() != null) s.setEmail(req.getEmail());
        if (req.getPhone() != null) s.setPhone(req.getPhone());
        ShariaScholar saved = scholarRepo.save(s);
        audit.log("SHARIA_SCHOLAR_UPDATED", "ShariaScholar", saved.getId(),
                "Scholar " + saved.getReference() + " updated");
        return toScholar(saved);
    }

    @Transactional
    public ScholarResponse declareCoi(Long id, DeclareCoiRequest req) {
        ShariaScholar s = loadScholar(id);
        s.setCoiDeclaredAt(LocalDate.now());
        s.setCoiNotesEn(req.getCoiNotesEn());
        s.setCoiNotesAr(req.getCoiNotesAr());
        ShariaScholar saved = scholarRepo.save(s);
        audit.log("SHARIA_SCHOLAR_COI_DECLARED", "ShariaScholar", saved.getId(),
                "Scholar " + saved.getReference() + " refreshed COI declaration");
        timeline(saved.getId(), null, null, null, null,
                "COI_DECLARED", null,
                "Annual conflict-of-interest declaration refreshed",
                "تحديث إقرار تعارض المصالح السنوي");
        emit("SHARIA_SCHOLAR_COI_DECLARED", "ShariaScholar", saved.getId(),
                saved.getReference(),
                Map.of("scholarRef", saved.getReference(), "role", saved.getSsbRole()));
        return toScholar(saved);
    }

    public Page<ScholarResponse> listScholars(int page, int size) {
        return scholarRepo.findByOrganisationIdOrderByReferenceAsc(
                        org(), PageRequest.of(page, size))
                .map(ShariaService::toScholar);
    }

    public ScholarResponse getScholar(Long id) {
        return toScholar(loadScholar(id));
    }

    // ─── Resolutions ───────────────────────────────────────────────

    @Transactional
    public ResolutionResponse createResolution(CreateResolutionRequest req) {
        resolutionRepo.findByReferenceAndOrganisationId(req.getReference(), org())
                .ifPresent(x -> {
                    throw new IllegalStateException(
                            "Resolution " + req.getReference() + " already exists");
                });
        ShariaResolution r = new ShariaResolution();
        r.setOrganisationId(org());
        r.setReference(req.getReference());
        r.setSubjectArea(req.getSubjectArea());
        r.setTitleEn(req.getTitleEn());
        r.setTitleAr(req.getTitleAr());
        r.setBodyEn(req.getBodyEn());
        r.setBodyAr(req.getBodyAr());
        r.setRationaleEn(req.getRationaleEn());
        r.setRationaleAr(req.getRationaleAr());
        r.setSigningScholars(req.getSigningScholars());
        r.setEffectiveDate(req.getEffectiveDate());
        r.setSupersedesResolutionId(req.getSupersedesResolutionId());
        r.setSubmittedAt(LocalDateTime.now());
        ShariaResolution saved = resolutionRepo.save(r);
        audit.log("SHARIA_RESOLUTION_SUBMITTED", "ShariaResolution", saved.getId(),
                "Resolution " + saved.getReference() + " (" + saved.getSubjectArea() + ") submitted");
        timeline(null, saved.getId(), null, null, null,
                "RESOLUTION_SUBMITTED", saved.getStatus(),
                "Resolution submitted for SSB review",
                "تقديم قرار لمراجعة الهيئة الشرعية");
        emit("SHARIA_RESOLUTION_SUBMITTED", "ShariaResolution", saved.getId(),
                saved.getReference(),
                Map.of("subjectArea", saved.getSubjectArea(),
                        "titleEn", saved.getTitleEn(),
                        "titleAr", saved.getTitleAr()));
        return toResolution(saved);
    }

    @Transactional
    public ResolutionResponse transitionResolution(Long id, TransitionResolutionRequest req) {
        ShariaResolution r = loadResolution(id);
        String from = r.getStatus();
        String to = req.getToStatus();
        Set<String> allowed = RESOLUTION_ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Illegal Resolution transition " + from + " → " + to
                            + " (allowed: " + allowed + ")");
        }
        r.setStatus(to);
        LocalDateTime now = LocalDateTime.now();
        String trigger;
        switch (to) {
            case "SSB_REVIEW" -> {
                r.setSsbReviewStartedAt(now);
                trigger = "SHARIA_RESOLUTION_IN_REVIEW";
            }
            case "APPROVED" -> {
                r.setApprovedAt(now);
                trigger = "SHARIA_RESOLUTION_APPROVED";
            }
            case "ACTIVE" -> { trigger = "SHARIA_RESOLUTION_ACTIVE"; }
            case "SUPERSEDED" -> { trigger = "SHARIA_RESOLUTION_SUPERSEDED"; }
            case "REJECTED" -> {
                r.setRejectedAt(now);
                r.setRejectionReasonEn(req.getReasonEn());
                r.setRejectionReasonAr(req.getReasonAr());
                trigger = "SHARIA_RESOLUTION_REJECTED";
            }
            default -> throw new IllegalStateException("Unhandled status " + to);
        }
        ShariaResolution saved = resolutionRepo.save(r);
        audit.log(trigger, "ShariaResolution", saved.getId(),
                "Resolution " + saved.getReference() + " " + from + " → " + to);
        timeline(null, saved.getId(), null, null, null,
                "RESOLUTION_TRANSITIONED", to,
                req.getReasonEn() != null ? req.getReasonEn()
                        : ("Moved to " + to),
                req.getReasonAr() != null ? req.getReasonAr()
                        : ("الانتقال إلى الحالة " + to));
        emit(trigger, "ShariaResolution", saved.getId(),
                saved.getReference(),
                Map.of("from", from, "to", to,
                        "subjectArea", saved.getSubjectArea()));
        return toResolution(saved);
    }

    public Page<ResolutionResponse> listResolutions(int page, int size) {
        return resolutionRepo.findByOrganisationIdOrderByReferenceDesc(
                        org(), PageRequest.of(page, size))
                .map(ShariaService::toResolution);
    }

    public ResolutionResponse getResolution(Long id) {
        return toResolution(loadResolution(id));
    }

    public List<EventResponse> listResolutionEvents(Long id) {
        return eventRepo.findByResolutionIdAndOrganisationIdOrderByOccurredAtDesc(id, org())
                .stream().map(ShariaService::toEvent).toList();
    }

    // ─── Audit Findings ────────────────────────────────────────────

    @Transactional
    public AuditFindingResponse raiseFinding(RaiseAuditFindingRequest req) {
        ShariaAuditFinding f = new ShariaAuditFinding();
        f.setOrganisationId(org());
        f.setReference(req.getReference());
        f.setAuditCycleLabel(req.getAuditCycleLabel());
        f.setSeverity(req.getSeverity());
        f.setTitleEn(req.getTitleEn());
        f.setTitleAr(req.getTitleAr());
        f.setDescriptionEn(req.getDescriptionEn());
        f.setDescriptionAr(req.getDescriptionAr());
        f.setRootCauseEn(req.getRootCauseEn());
        f.setRootCauseAr(req.getRootCauseAr());
        f.setRemediationPlanEn(req.getRemediationPlanEn());
        f.setRemediationPlanAr(req.getRemediationPlanAr());
        f.setLinkedResolutionId(req.getLinkedResolutionId());
        f.setProductLine(req.getProductLine());
        f.setRemediationDueAt(req.getRemediationDueAt());
        f.setAssignedToUserId(req.getAssignedToUserId());
        f.setRaisedAt(LocalDateTime.now());
        ShariaAuditFinding saved = findingRepo.save(f);
        audit.log("SHARIA_AUDIT_FINDING_RAISED", "ShariaAuditFinding", saved.getId(),
                "Finding " + saved.getReference() + " " + saved.getSeverity() + " raised");
        timeline(null, null, saved.getId(), null, null,
                "FINDING_RAISED", saved.getStatus(),
                "Audit finding raised at " + saved.getSeverity() + " severity",
                "رفع نتيجة المراجعة الشرعية بدرجة خطورة " + saved.getSeverity());
        emit("SHARIA_AUDIT_FINDING_RAISED", "ShariaAuditFinding", saved.getId(),
                saved.getReference(),
                Map.of("severity", saved.getSeverity(),
                        "productLine", saved.getProductLine() == null ? "" : saved.getProductLine(),
                        "titleEn", saved.getTitleEn(),
                        "titleAr", saved.getTitleAr()));
        return toFinding(saved);
    }

    @Transactional
    public AuditFindingResponse transitionFinding(Long id, TransitionAuditFindingRequest req) {
        ShariaAuditFinding f = loadFinding(id);
        String from = f.getStatus();
        String to = req.getToStatus();
        Set<String> allowed = FINDING_ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Illegal Finding transition " + from + " → " + to
                            + " (allowed: " + allowed + ")");
        }
        f.setStatus(to);
        LocalDateTime now = LocalDateTime.now();
        if ("VERIFIED".equals(to)) f.setVerifiedAt(now);
        if ("CLOSED".equals(to)) f.setClosedAt(now);
        ShariaAuditFinding saved = findingRepo.save(f);
        audit.log("SHARIA_AUDIT_FINDING_TRANSITIONED", "ShariaAuditFinding", saved.getId(),
                "Finding " + saved.getReference() + " " + from + " → " + to);
        timeline(null, null, saved.getId(), null, null,
                "FINDING_TRANSITIONED", to,
                req.getNoteEn(), req.getNoteAr());
        emit("SHARIA_AUDIT_FINDING_TRANSITIONED", "ShariaAuditFinding", saved.getId(),
                saved.getReference(),
                Map.of("from", from, "to", to, "severity", saved.getSeverity()));
        return toFinding(saved);
    }

    public Page<AuditFindingResponse> listFindings(int page, int size) {
        return findingRepo.findByOrganisationIdOrderByRaisedAtDesc(
                        org(), PageRequest.of(page, size))
                .map(ShariaService::toFinding);
    }

    // ─── NC Events ─────────────────────────────────────────────────

    @Transactional
    public NcEventResponse detectNcEvent(DetectNcEventRequest req) {
        ShariaNonComplianceEvent n = new ShariaNonComplianceEvent();
        n.setOrganisationId(org());
        n.setReference(req.getReference());
        n.setSeverity(req.getSeverity());
        n.setTitleEn(req.getTitleEn());
        n.setTitleAr(req.getTitleAr());
        n.setDescriptionEn(req.getDescriptionEn());
        n.setDescriptionAr(req.getDescriptionAr());
        n.setProductLine(req.getProductLine());
        n.setLinkedResolutionId(req.getLinkedResolutionId());
        n.setDetectionSource(req.getDetectionSource());
        n.setSnciAmountSar(req.getSnciAmountSar());
        n.setAffectedCustomerCount(req.getAffectedCustomerCount());
        n.setAssignedToUserId(req.getAssignedToUserId());
        n.setDetectedAt(LocalDateTime.now());
        ShariaNonComplianceEvent saved = ncRepo.save(n);
        audit.log("SHARIA_NC_EVENT_DETECTED", "ShariaNonComplianceEvent", saved.getId(),
                "SNCI event " + saved.getReference() + " " + saved.getSeverity()
                        + " SNCI=" + saved.getSnciAmountSar() + " SAR detected");
        timeline(null, null, null, saved.getId(), null,
                "NC_DETECTED", saved.getStatus(),
                "Sharia non-compliance event detected — SNCI " + saved.getSnciAmountSar() + " SAR",
                "رصد مخالفة شرعية — دخل غير شرعي " + saved.getSnciAmountSar() + " ر.س");
        emit("SHARIA_NC_EVENT_DETECTED", "ShariaNonComplianceEvent", saved.getId(),
                saved.getReference(),
                Map.of("severity", saved.getSeverity(),
                        "snciAmountSar", saved.getSnciAmountSar(),
                        "productLine", saved.getProductLine() == null ? "" : saved.getProductLine()));
        return toNcEvent(saved);
    }

    @Transactional
    public NcEventResponse transitionNcEvent(Long id, TransitionNcEventRequest req) {
        ShariaNonComplianceEvent n = loadNcEvent(id);
        String from = n.getStatus();
        String to = req.getToStatus();
        Set<String> allowed = NC_ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Illegal NcEvent transition " + from + " → " + to
                            + " (allowed: " + allowed + ")");
        }
        n.setStatus(to);
        if ("CLOSED".equals(to)) n.setClosedAt(LocalDateTime.now());
        ShariaNonComplianceEvent saved = ncRepo.save(n);
        audit.log("SHARIA_NC_EVENT_TRANSITIONED", "ShariaNonComplianceEvent", saved.getId(),
                "NcEvent " + saved.getReference() + " " + from + " → " + to);
        timeline(null, null, null, saved.getId(), null,
                "NC_TRANSITIONED", to, req.getNoteEn(), req.getNoteAr());
        emit("SHARIA_NC_EVENT_TRANSITIONED", "ShariaNonComplianceEvent", saved.getId(),
                saved.getReference(),
                Map.of("from", from, "to", to, "snciAmountSar", saved.getSnciAmountSar()));
        return toNcEvent(saved);
    }

    @Transactional
    public NcEventResponse purifyNcEvent(Long id, PurifyNcEventRequest req) {
        ShariaNonComplianceEvent n = loadNcEvent(id);
        if (!"INVESTIGATION".equals(n.getStatus()) && !"DETECTED".equals(n.getStatus())) {
            throw new IllegalStateException(
                    "Cannot purify SNCI in status " + n.getStatus());
        }
        // ── F2-121: the purification date is caller-supplied, so it must be bounded ─────────────
        //
        // `purifiedAt` is @NotNull but otherwise unconstrained, and it is the date an AAOIFI FAS 1
        // disclosure and the SAMA §6.4 90-day window are both computed from. Two values are simply
        // incoherent and neither was refused:
        //
        //   * a FUTURE date — the record would assert a donation that has not happened;
        //   * a date BEFORE detected_at — income purified before the breach was discovered.
        //
        // ⚠ Deliberately NOT server-derived. Purification genuinely happens outside the platform and
        // is recorded afterwards from a charity receipt, so a back-dated value is legitimate and
        // stamping LocalDate.now() would overwrite the real date with the data-entry date — the
        // mirror image of the RC-E defect, and it would corrupt the disclosure it feeds. Bounding a
        // caller's value is the correct treatment when the caller is the only source of it.
        LocalDate purifiedOn = req.getPurifiedAt();
        if (purifiedOn.isAfter(LocalDate.now())) {
            throw new IllegalStateException(
                    "Purification date " + purifiedOn + " is in the future — SNCI cannot be "
                    + "recorded as purified before the donation has been made.");
        }
        if (n.getDetectedAt() != null && purifiedOn.isBefore(n.getDetectedAt().toLocalDate())) {
            throw new IllegalStateException(
                    "Purification date " + purifiedOn + " precedes detection on "
                    + n.getDetectedAt().toLocalDate() + " — income cannot be purified before the "
                    + "non-compliance that produced it was detected.");
        }
        n.setStatus("INCOME_PURIFIED");
        n.setPurificationDestination(req.getPurificationDestination());
        n.setCharityReceiptRef(req.getCharityReceiptRef());
        n.setPurifiedAt(req.getPurifiedAt());
        ShariaNonComplianceEvent saved = ncRepo.save(n);
        audit.log("SHARIA_NC_EVENT_PURIFIED", "ShariaNonComplianceEvent", saved.getId(),
                "SNCI " + saved.getSnciAmountSar() + " SAR purified to "
                        + saved.getPurificationDestination()
                        + " (receipt " + saved.getCharityReceiptRef() + ")");
        timeline(null, null, null, saved.getId(), null,
                "NC_PURIFIED", saved.getStatus(),
                "SNCI purified — " + saved.getSnciAmountSar() + " SAR donated to "
                        + saved.getPurificationDestination(),
                "تطهير الدخل غير الشرعي — " + saved.getSnciAmountSar() + " ر.س إلى "
                        + saved.getPurificationDestination());
        emit("SHARIA_NC_EVENT_PURIFIED", "ShariaNonComplianceEvent", saved.getId(),
                saved.getReference(),
                Map.of("snciAmountSar", saved.getSnciAmountSar(),
                        "destination", saved.getPurificationDestination(),
                        "purifiedAt", saved.getPurifiedAt().toString()));
        return toNcEvent(saved);
    }

    public Page<NcEventResponse> listNcEvents(int page, int size) {
        return ncRepo.findByOrganisationIdOrderByDetectedAtDesc(
                        org(), PageRequest.of(page, size))
                .map(ShariaService::toNcEvent);
    }

    // ─── Product Approvals ─────────────────────────────────────────

    @Transactional
    public ProductResponse submitProduct(SubmitProductRequest req) {
        ShariaProductApproval p = new ShariaProductApproval();
        p.setOrganisationId(org());
        p.setReference(req.getReference());
        p.setProductNameEn(req.getProductNameEn());
        p.setProductNameAr(req.getProductNameAr());
        p.setStructure(req.getStructure());
        p.setDescriptionEn(req.getDescriptionEn());
        p.setDescriptionAr(req.getDescriptionAr());
        p.setBusinessOwnerUserId(req.getBusinessOwnerUserId());
        p.setShariaOfficerUserId(req.getShariaOfficerUserId());
        p.setSubmittedAt(LocalDateTime.now());
        ShariaProductApproval saved = productRepo.save(p);
        audit.log("SHARIA_PRODUCT_SUBMITTED", "ShariaProductApproval", saved.getId(),
                "Product " + saved.getReference() + " (" + saved.getStructure()
                        + ") submitted for SSB approval");
        timeline(null, null, null, null, saved.getId(),
                "PRODUCT_SUBMITTED", saved.getStatus(),
                "Product " + saved.getProductNameEn() + " submitted to SSB",
                "تقديم منتج " + saved.getProductNameAr() + " للهيئة الشرعية");
        emit("SHARIA_PRODUCT_SUBMITTED", "ShariaProductApproval", saved.getId(),
                saved.getReference(),
                Map.of("structure", saved.getStructure(),
                        "productNameEn", saved.getProductNameEn(),
                        "productNameAr", saved.getProductNameAr()));
        return toProduct(saved);
    }

    @Transactional
    public ProductResponse transitionProduct(Long id, TransitionProductRequest req) {
        ShariaProductApproval p = loadProduct(id);
        String from = p.getStatus();
        String to = req.getToStatus();
        Set<String> allowed = PRODUCT_ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Illegal Product transition " + from + " → " + to
                            + " (allowed: " + allowed + ")");
        }
        p.setStatus(to);
        LocalDateTime now = LocalDateTime.now();
        String trigger;
        switch (to) {
            case "SSB_REVIEW" -> {
                p.setSsbReviewStartedAt(now);
                trigger = "SHARIA_PRODUCT_IN_REVIEW";
            }
            case "APPROVED_PROVISIONAL" -> {
                p.setApprovedProvisionalAt(now);
                trigger = "SHARIA_PRODUCT_APPROVED_PROVISIONAL";
            }
            case "APPROVED_FINAL" -> {
                p.setApprovedFinalAt(now);
                if (req.getLinkedResolutionId() != null) {
                    p.setLinkedResolutionId(req.getLinkedResolutionId());
                }
                trigger = "SHARIA_PRODUCT_APPROVED";
            }
            case "LAUNCHED" -> {
                p.setLaunchedAt(LocalDate.now());
                trigger = "SHARIA_PRODUCT_LAUNCHED";
            }
            case "DECOMMISSIONED" -> {
                p.setDecommissionedAt(LocalDate.now());
                trigger = "SHARIA_PRODUCT_DECOMMISSIONED";
            }
            case "REJECTED" -> {
                p.setRejectedAt(now);
                p.setRejectionReasonEn(req.getReasonEn());
                p.setRejectionReasonAr(req.getReasonAr());
                trigger = "SHARIA_PRODUCT_REJECTED";
            }
            default -> throw new IllegalStateException("Unhandled status " + to);
        }
        ShariaProductApproval saved = productRepo.save(p);
        audit.log(trigger, "ShariaProductApproval", saved.getId(),
                "Product " + saved.getReference() + " " + from + " → " + to);
        timeline(null, null, null, null, saved.getId(),
                "PRODUCT_TRANSITIONED", to, req.getReasonEn(), req.getReasonAr());
        emit(trigger, "ShariaProductApproval", saved.getId(),
                saved.getReference(),
                Map.of("from", from, "to", to, "structure", saved.getStructure()));
        return toProduct(saved);
    }

    public Page<ProductResponse> listProducts(int page, int size) {
        return productRepo.findByOrganisationIdOrderBySubmittedAtDesc(
                        org(), PageRequest.of(page, size))
                .map(ShariaService::toProduct);
    }

    // ─── Dashboard ─────────────────────────────────────────────────

    public DashboardResponse dashboard() {
        Long o = org();
        long activeScholars = scholarRepo.countByOrganisationIdAndStatus(o, "ACTIVE");
        LocalDate cutoff = LocalDate.now().plusDays(90);
        long termsExpiring90d = scholarRepo
                .findByOrganisationIdAndTermEndBefore(o, cutoff)
                .stream().filter(s -> "ACTIVE".equals(s.getStatus())).count();
        long activeResolutions = resolutionRepo.countByOrganisationIdAndStatus(o, "ACTIVE");
        long resolutionsInReview = resolutionRepo.countByOrganisationIdAndStatusIn(
                o, List.of("DRAFT", "SSB_REVIEW"));
        long openFindingsHigh = findingRepo.countByOrganisationIdAndStatusInAndSeverity(
                o, List.of("OPEN", "REMEDIATION"), "HIGH");
        long openFindingsTotal = findingRepo.countByOrganisationIdAndStatus(o, "OPEN")
                + findingRepo.countByOrganisationIdAndStatus(o, "REMEDIATION");
        long ncPending = ncRepo.countByOrganisationIdAndStatus(o, "DETECTED")
                + ncRepo.countByOrganisationIdAndStatus(o, "INVESTIGATION");
        BigDecimal snciOutstanding = ncRepo.sumSnciByStatus(
                o, List.of("DETECTED", "INVESTIGATION"));
        long productsInReview = productRepo.countByOrganisationIdAndStatusIn(
                o, List.of("SUBMITTED", "SSB_REVIEW", "APPROVED_PROVISIONAL"));
        long productsLaunched = productRepo.countByOrganisationIdAndStatus(o, "LAUNCHED");

        Map<String, Long> resolutionsByStatus = new LinkedHashMap<>();
        for (String s : List.of("DRAFT", "SSB_REVIEW", "APPROVED", "ACTIVE",
                "SUPERSEDED", "REJECTED")) {
            long c = resolutionRepo.countByOrganisationIdAndStatus(o, s);
            if (c > 0) resolutionsByStatus.put(s, c);
        }
        Map<String, Long> findingsBySeverity = new LinkedHashMap<>();
        for (String sev : List.of("HIGH", "MEDIUM", "LOW")) {
            long c = findingRepo.countByOrganisationIdAndStatusInAndSeverity(
                    o, List.of("OPEN", "REMEDIATION"), sev);
            if (c > 0) findingsBySeverity.put(sev, c);
        }
        Map<String, Long> productsByStatus = new LinkedHashMap<>();
        for (String s : List.of("SUBMITTED", "SSB_REVIEW", "APPROVED_PROVISIONAL",
                "APPROVED_FINAL", "LAUNCHED", "DECOMMISSIONED", "REJECTED")) {
            long c = productRepo.countByOrganisationIdAndStatus(o, s);
            if (c > 0) productsByStatus.put(s, c);
        }

        List<ResolutionResponse> recentResolutions = resolutionRepo
                .findByOrganisationIdOrderByReferenceDesc(o, PageRequest.of(0, 8))
                .map(ShariaService::toResolution).getContent();
        List<AuditFindingResponse> openHighFindings = findingRepo
                .findByOrganisationIdAndStatus(o, "OPEN").stream()
                .filter(f -> "HIGH".equals(f.getSeverity()))
                .limit(10)
                .map(ShariaService::toFinding).toList();
        List<NcEventResponse> openNcEvents = ncRepo
                .findByOrganisationIdAndStatus(o, "DETECTED").stream()
                .limit(10)
                .map(ShariaService::toNcEvent).toList();

        return DashboardResponse.builder()
                .activeScholars(activeScholars)
                .termsExpiring90d(termsExpiring90d)
                .activeResolutions(activeResolutions)
                .resolutionsInReview(resolutionsInReview)
                .openFindingsHigh(openFindingsHigh)
                .openFindingsTotal(openFindingsTotal)
                .ncEventsPendingPurification(ncPending)
                .snciOutstandingSar(snciOutstanding == null ? BigDecimal.ZERO : snciOutstanding)
                .productsInReview(productsInReview)
                .productsLaunched(productsLaunched)
                .resolutionsByStatus(resolutionsByStatus)
                .findingsBySeverity(findingsBySeverity)
                .productsByStatus(productsByStatus)
                .recentResolutions(recentResolutions)
                .openHighFindings(openHighFindings)
                .openNcEvents(openNcEvents)
                .build();
    }

    // ─── Loaders ───────────────────────────────────────────────────

    private ShariaScholar loadScholar(Long id) {
        return scholarRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException("Scholar " + id + " not found"));
    }

    private ShariaResolution loadResolution(Long id) {
        return resolutionRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException("Resolution " + id + " not found"));
    }

    private ShariaAuditFinding loadFinding(Long id) {
        return findingRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException("Finding " + id + " not found"));
    }

    private ShariaNonComplianceEvent loadNcEvent(Long id) {
        return ncRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException("NcEvent " + id + " not found"));
    }

    private ShariaProductApproval loadProduct(Long id) {
        return productRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException("Product " + id + " not found"));
    }

    private void timeline(Long scholarId, Long resolutionId, Long findingId,
                          Long ncId, Long productId,
                          String eventType, String statusAfter,
                          String noteEn, String noteAr) {
        ShariaEvent e = new ShariaEvent();
        e.setOrganisationId(org());
        e.setScholarId(scholarId);
        e.setResolutionId(resolutionId);
        e.setAuditFindingId(findingId);
        e.setNcEventId(ncId);
        e.setProductApprovalId(productId);
        e.setOccurredAt(LocalDateTime.now());
        e.setEventType(eventType);
        e.setActorUserId(currentUserId());
        e.setStatusAfter(statusAfter);
        e.setNoteEn(noteEn);
        e.setNoteAr(noteAr);
        eventRepo.save(e);
    }

    private static Long org() { return TenantContext.requireOrganisationId(); }

    private static Long currentUserId() {
        return com.tss.grc.common.security.CurrentActor.userId();
    }

    private void emit(String trigger, String entityType, Long entityId,
                       String correlationId, Map<String, Object> payload) {
        publisher.publishEvent(new DomainEvent(
                TenantContext.getCurrentTenant(),
                trigger, entityType, entityId, correlationId,
                payload == null ? Map.of() : new HashMap<>(payload)));
    }

    // ─── Mappers ───────────────────────────────────────────────────

    private static ScholarResponse toScholar(ShariaScholar s) {
        return ScholarResponse.builder()
                .id(s.getId()).reference(s.getReference())
                .fullNameEn(s.getFullNameEn()).fullNameAr(s.getFullNameAr())
                .ssbRole(s.getSsbRole()).status(s.getStatus())
                .termStart(s.getTermStart()).termEnd(s.getTermEnd())
                .credentialsEn(s.getCredentialsEn()).credentialsAr(s.getCredentialsAr())
                .coiDeclaredAt(s.getCoiDeclaredAt())
                .coiNotesEn(s.getCoiNotesEn()).coiNotesAr(s.getCoiNotesAr())
                .annualFeeSar(s.getAnnualFeeSar())
                .meetingsAttendedYtd(s.getMeetingsAttendedYtd())
                .email(s.getEmail()).phone(s.getPhone())
                .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt())
                .build();
    }

    private static ResolutionResponse toResolution(ShariaResolution r) {
        return ResolutionResponse.builder()
                .id(r.getId()).reference(r.getReference())
                .subjectArea(r.getSubjectArea())
                .titleEn(r.getTitleEn()).titleAr(r.getTitleAr())
                .bodyEn(r.getBodyEn()).bodyAr(r.getBodyAr())
                .rationaleEn(r.getRationaleEn()).rationaleAr(r.getRationaleAr())
                .status(r.getStatus())
                .submittedAt(r.getSubmittedAt())
                .ssbReviewStartedAt(r.getSsbReviewStartedAt())
                .approvedAt(r.getApprovedAt()).rejectedAt(r.getRejectedAt())
                .effectiveDate(r.getEffectiveDate())
                .supersedesResolutionId(r.getSupersedesResolutionId())
                .signingScholars(r.getSigningScholars())
                .meetingId(r.getMeetingId())
                .rejectionReasonEn(r.getRejectionReasonEn())
                .rejectionReasonAr(r.getRejectionReasonAr())
                .build();
    }

    private static AuditFindingResponse toFinding(ShariaAuditFinding f) {
        return AuditFindingResponse.builder()
                .id(f.getId()).reference(f.getReference())
                .auditCycleLabel(f.getAuditCycleLabel())
                .severity(f.getSeverity()).status(f.getStatus())
                .titleEn(f.getTitleEn()).titleAr(f.getTitleAr())
                .descriptionEn(f.getDescriptionEn()).descriptionAr(f.getDescriptionAr())
                .rootCauseEn(f.getRootCauseEn()).rootCauseAr(f.getRootCauseAr())
                .remediationPlanEn(f.getRemediationPlanEn())
                .remediationPlanAr(f.getRemediationPlanAr())
                .linkedResolutionId(f.getLinkedResolutionId())
                .productLine(f.getProductLine())
                .raisedAt(f.getRaisedAt())
                .remediationDueAt(f.getRemediationDueAt())
                .verifiedAt(f.getVerifiedAt())
                .closedAt(f.getClosedAt())
                .assignedToUserId(f.getAssignedToUserId())
                .build();
    }

    private static NcEventResponse toNcEvent(ShariaNonComplianceEvent n) {
        return NcEventResponse.builder()
                .id(n.getId()).reference(n.getReference())
                .status(n.getStatus()).severity(n.getSeverity())
                .titleEn(n.getTitleEn()).titleAr(n.getTitleAr())
                .descriptionEn(n.getDescriptionEn()).descriptionAr(n.getDescriptionAr())
                .productLine(n.getProductLine())
                .linkedResolutionId(n.getLinkedResolutionId())
                .detectedAt(n.getDetectedAt())
                .detectionSource(n.getDetectionSource())
                .snciAmountSar(n.getSnciAmountSar())
                .purificationDestination(n.getPurificationDestination())
                .purifiedAt(n.getPurifiedAt())
                .charityReceiptRef(n.getCharityReceiptRef())
                .closedAt(n.getClosedAt())
                .affectedCustomerCount(n.getAffectedCustomerCount())
                .assignedToUserId(n.getAssignedToUserId())
                .build();
    }

    private static ProductResponse toProduct(ShariaProductApproval p) {
        return ProductResponse.builder()
                .id(p.getId()).reference(p.getReference())
                .productNameEn(p.getProductNameEn())
                .productNameAr(p.getProductNameAr())
                .structure(p.getStructure())
                .status(p.getStatus())
                .descriptionEn(p.getDescriptionEn())
                .descriptionAr(p.getDescriptionAr())
                .linkedResolutionId(p.getLinkedResolutionId())
                .submittedAt(p.getSubmittedAt())
                .ssbReviewStartedAt(p.getSsbReviewStartedAt())
                .approvedProvisionalAt(p.getApprovedProvisionalAt())
                .approvedFinalAt(p.getApprovedFinalAt())
                .rejectedAt(p.getRejectedAt())
                .launchedAt(p.getLaunchedAt())
                .decommissionedAt(p.getDecommissionedAt())
                .rejectionReasonEn(p.getRejectionReasonEn())
                .rejectionReasonAr(p.getRejectionReasonAr())
                .businessOwnerUserId(p.getBusinessOwnerUserId())
                .shariaOfficerUserId(p.getShariaOfficerUserId())
                .build();
    }

    private static EventResponse toEvent(ShariaEvent e) {
        return EventResponse.builder()
                .id(e.getId())
                .scholarId(e.getScholarId())
                .resolutionId(e.getResolutionId())
                .auditFindingId(e.getAuditFindingId())
                .ncEventId(e.getNcEventId())
                .productApprovalId(e.getProductApprovalId())
                .occurredAt(e.getOccurredAt())
                .eventType(e.getEventType())
                .actorUserId(e.getActorUserId())
                .actorName(e.getActorName())
                .statusAfter(e.getStatusAfter())
                .noteEn(e.getNoteEn())
                .noteAr(e.getNoteAr())
                .build();
    }
}
